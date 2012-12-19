/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.kernel.differencing

import reflect.BeanProperty
import scala.collection.JavaConversions._
import org.joda.time.{DateTime, Interval}
import net.lshift.diffa.kernel.config.{JooqConfigStoreCompanion, PairRef}
import net.lshift.diffa.kernel.util.cache.{KeyPredicate, CachedMap, CacheProvider}
import net.lshift.diffa.kernel.util.sequence.SequenceProvider
import net.lshift.diffa.kernel.util.AlertCodes._
import net.lshift.diffa.schema.jooq.DatabaseFacade
import net.lshift.diffa.schema.jooq.DatabaseFacade.{timestampToDateTime, dateTimeToTimestamp}
import net.lshift.diffa.schema.Tables._
import net.lshift.diffa.schema.tables.records.PendingDiffsRecord
import org.jooq.impl.Factory._
import net.lshift.diffa.kernel.util.MissingObjectException
import org.jooq.impl.Factory
import org.slf4j.LoggerFactory
import org.jooq._
import java.lang.{Long => LONG}
import java.sql.Timestamp
import net.lshift.diffa.kernel.naming.{CacheName, SequenceName}
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.lifecycle.PairLifecycleAware
import net.lshift.diffa.snowflake.IdProvider

/**
 * Hibernate backed Domain Cache provider.
 */
class JooqDomainDifferenceStore(db: DatabaseFacade,
                                cacheProvider:CacheProvider,
                                sequenceProvider:SequenceProvider,
                                idProvider: IdProvider)
    extends DomainDifferenceStore with PairLifecycleAware {

  val logger = LoggerFactory.getLogger(getClass)

  initializeExistingSequences()

  val aggregationCache = new DifferenceAggregationCache(this, cacheProvider)

  val pendingEvents = cacheProvider.getCachedMap[VersionID, PendingDifferenceEvent]("pending.difference.events")
  val reportedEvents = cacheProvider.getCachedMap[VersionID, InternalReportedDifferenceEvent](CacheName.DIFFS)
  val extentsByPair = cacheProvider.getCachedMap[PairRef, java.lang.Long](CacheName.EXTENT_PAIRS)

  /**
   * This is a marker to indicate the absence of an event in a map rather than using null
   * (using an Option is not an option in this case).
   */
  val NON_EXISTENT_SEQUENCE_ID = -1
  val nonExistentReportedEvent = InternalReportedDifferenceEvent(seqId = NON_EXISTENT_SEQUENCE_ID)

  /**
   * This is a heuristic that allows the cache to get prefilled if the agent is booted and
   * there were persistent pending diffs. The motivation is to reduce cache misses in subsequent calls.
   */
  val prefetchLimit = 1000 // TODO This should be a tuning parameter
  prefetchPendingEvents(prefetchLimit)

  val PAIR_NAME_ALIAS = "pair_name"
  val ESCALATION_NAME_ALIAS = "escalation_name"


  def reset {
    pendingEvents.evictAll()
    reportedEvents.evictAll()
    extentsByPair.evictAll()
    aggregationCache.clear()
  }

  def onPairUpdated(pair:PairRef) = extentsByPair.evict(pair)
  def onPairDeleted(pair:PairRef) = extentsByPair.evict(pair)

  def removeDomain(space:Long) = {

    db.execute(t => {
      JooqConfigStoreCompanion.listPairsInCurrentTx(t, space).foreach(p => {
        removePendingDifferences(t, space)
        removeLatestRecordedVersion(t, p.asRef)
        orphanExtentForPair(t, p.asRef)
      })
    })

    preenExtentsCache(space)
    preenPendingEventsCache("objId.pair.space", space.toString)
  }

  def removePair(pair: PairRef) = {

    db.execute { t =>
      removePendingDifferences(t, pair)
      removeLatestRecordedVersion(t, pair)
      // TODO investigate potential deadlock - first noticed 2012-12-19
      orphanExtentForPair(t, pair)
    }

    extentsByPair.evict(pair)
    preenPendingEventsCache("objId.pair.name", pair.name)
  }
  
  def currentSequenceId(space:Long) = sequenceProvider.currentSequenceValue(SequenceName.SPACES).toString

  def maxSequenceId(pair: PairRef, start:DateTime, end:DateTime) = {

    db.execute { t =>
      var query = t.select(max(DIFFS.SEQ_ID)).
                    from(DIFFS).
                    join(PAIRS).
                      on(PAIRS.EXTENT.equal(DIFFS.EXTENT)).
                    where(PAIRS.SPACE.equal(pair.space)).
                      and(PAIRS.NAME.equal(pair.name))

      if (start != null)
        query = query.and(DIFFS.DETECTED_AT.greaterOrEqual(dateTimeToTimestamp(start)))
      if (end != null)
        query = query.and(DIFFS.DETECTED_AT.lessThan(dateTimeToTimestamp(end)))

      Option(query.fetchOne().getValue(0).asInstanceOf[java.lang.Long])
        .getOrElse(java.lang.Long.valueOf(0)).longValue()
    }
  }

  def addPendingUnmatchedEvent(id: VersionID, lastUpdate: DateTime, upstreamVsn: String, downstreamVsn: String, seen: DateTime) = {
    db.execute(t => {
      val pending = getPendingEvent(t, id)

      if (pending.exists()) {
        updatePendingEvent(t, pending, upstreamVsn, downstreamVsn, seen)
      }
      else {

        val reported = getEventById(t, id)

        if (reportedEventExists(reported)) {

          val updated = reported.copy(
            isMatch = false,
            upstreamVsn = upstreamVsn,
            downstreamVsn = downstreamVsn,
            lastSeen = seen
          )

          addReportableMismatch(t, updated)
        }
        else {
          val pendingUnmatched = PendingDifferenceEvent(null, id, lastUpdate, upstreamVsn, downstreamVsn, seen)
          createPendingEvent(pendingUnmatched)
        }

      }
    })
  }

  def addReportableUnmatchedEvent(id: VersionID, lastUpdate: DateTime, upstreamVsn: String, downstreamVsn: String, seen: DateTime) =
    db.execute(t => {
      addReportableMismatch(t, InternalReportedDifferenceEvent(
        objId = id,
        detectedAt = lastUpdate,
        isMatch = false,
        upstreamVsn = upstreamVsn,
        downstreamVsn = downstreamVsn,
        lastSeen = seen
      ))
    })


  def upgradePendingUnmatchedEvent(id: VersionID) = {

    db.execute(t => {
      val pending = getPendingEvent(t, id)

      if (pending.exists()) {

        // Remove the pending and report a mismatch
        try {

            removePendingEvent(t, pending)
            createReportedEvent(t, pending.convertToUnmatched, nextEventSequenceValue)

        } catch {
          case e: Exception =>
            reportedEvents.evict(pending.objId)
            throw e
        }
      }
      else {
        // No pending difference, nothing to do
        null
      }

    })

  }

  def cancelPendingUnmatchedEvent(id: VersionID, vsn: String) = {
    db.execute(t => {
      val pending = getPendingEvent(t, id)

      if (pending.exists()) {
        if (pending.upstreamVsn == vsn || pending.downstreamVsn == vsn) {
          removePendingEvent(t, pending)
          true
        } else {
          false
        }
      }
      else {
        false
      }
    })
  }

  def addMatchedEvent(id: VersionID, vsn: String) = {

    db.execute(t => {
      // Remove any pending events with the given id
      val pending = getPendingEvent(t, id)

      if (pending.exists()) {
        removePendingEvent(t, pending)
      }

      // Find any existing events we've got for this ID
      val event = getEventById(t, id)

      if (reportedEventExists(event)) {
        event.state match {
          case MatchState.MATCHED => // Ignore. We've already got an event for what we want.
            event.asDifferenceEvent
          case MatchState.UNMATCHED | MatchState.IGNORED =>
            // A difference has gone away. Remove the difference, and add in a match
            val previousDetectionTime = event.detectedAt

            val newEvent = event.copy(
              detectedAt = new DateTime,
              isMatch = true,
              upstreamVsn = vsn,
              downstreamVsn = vsn
            )

            updateAndConvertEvent(t, newEvent, previousDetectionTime)
        }
      }
      else {
        // No unmatched event. Nothing to do.
        null
      }
    })
  }

  def ignoreEvent(space:Long, seqId:String) = {

    db.execute {t =>

      val evt = getEventBySequenceId(t, seqId.toLong).getOrElse {
        throw new MissingObjectException("No diff found with seqId: " + seqId)
      }
      if (evt.objId.pair.space != space) {
        throw new IllegalArgumentException("Invalid domain %s for sequence id %s (expected %s)".format(space, seqId, evt.objId.pair.space))
      }

      if (evt.isMatch) {
        throw new IllegalArgumentException("Cannot ignore a match for %s (in domain %s)".format(seqId, space))
      }
      if (!evt.ignored) {
        // Remove this event, and replace it with a new event. We do this to ensure that consumers watching the updates
        // (or even just monitoring sequence ids) see a noticeable change.

        val newEvent = evt.copy(ignored = true)

        updateAndConvertEvent(t, newEvent)

      } else {
        evt.asDifferenceEvent
      }

    }
  }

  def unignoreEvent(space:Long, seqId:String) = {

    db.execute { t =>
      val evt = getEventBySequenceId(t, seqId.toLong).getOrElse {
        throw new MissingObjectException("No diff found with seqId: " + seqId)
      }
      if (evt.objId.pair.space != space) {
        throw new IllegalArgumentException("Invalid domain %s for sequence id %s (expected %s)".format(space, seqId, evt.objId.pair.space))
      }
      if (evt.isMatch) {
        throw new IllegalArgumentException("Cannot unignore a match for %s (in domain %s)".format(seqId, space))
      }
      if (!evt.ignored) {
        throw new IllegalArgumentException("Cannot unignore an event that isn't ignored - %s (in domain %s)".format(seqId, space))
      }

      // Generate a new event with the same details but the ignored flag cleared. This will ensure consumers
      // that are monitoring for changes will see one.

      val newEvent = evt.copy(ignored = false)

      updateAndConvertEvent(t, newEvent)
    }
  }

  def lastRecordedVersion(pair:PairRef) = {

    db.execute(t => {
      val record =  t.select(STORE_CHECKPOINTS.LATEST_VERSION).
                      from(STORE_CHECKPOINTS).
                      where(STORE_CHECKPOINTS.SPACE.equal(pair.space)).
                        and(STORE_CHECKPOINTS.PAIR.equal(pair.name)).
                      fetchOne()

      if (record == null) {
        None
      }
      else {
        Some(record.getValue(STORE_CHECKPOINTS.LATEST_VERSION))
      }
    })
  }

  def recordLatestVersion(pairRef:PairRef, version:Long) = {

    db.execute { t =>
      t.insertInto(STORE_CHECKPOINTS).
          set(STORE_CHECKPOINTS.SPACE, pairRef.space:LONG).
          set(STORE_CHECKPOINTS.PAIR, pairRef.name).
          set(STORE_CHECKPOINTS.LATEST_VERSION, java.lang.Long.valueOf(version)).
        onDuplicateKeyUpdate().
          set(STORE_CHECKPOINTS.LATEST_VERSION, java.lang.Long.valueOf(version)).
        execute()
    }
  }

  def retrieveUnmatchedEvents(space:Long, interval: Interval) = {

    db.execute { t =>
      t.select(DIFFS.getFields).
        select(PAIRS.SPACE, PAIRS.NAME.as(PAIR_NAME_ALIAS)).
        select(ESCALATIONS.NAME.as(ESCALATION_NAME_ALIAS)).
        from(DIFFS).
        join(PAIRS).
          on(PAIRS.EXTENT.equal(DIFFS.EXTENT)).
        leftOuterJoin(ESCALATION_RULES).
          on(ESCALATION_RULES.ID.eq(DIFFS.NEXT_ESCALATION)).
        leftOuterJoin(ESCALATIONS).
          on(ESCALATIONS.EXTENT.eq(ESCALATION_RULES.EXTENT)).
            and(ESCALATIONS.NAME.eq(ESCALATION_RULES.ESCALATION)).
        where(PAIRS.SPACE.equal(space)).
          and(DIFFS.DETECTED_AT.greaterOrEqual(dateTimeToTimestamp(interval.getStart))).
          and(DIFFS.DETECTED_AT.lessThan(dateTimeToTimestamp(interval.getEnd))).
          and(DIFFS.IS_MATCH.equal(false)).
          and(DIFFS.IGNORED.equal(false)).
        fetch().
        map(new RecordMapper[Record, DifferenceEvent] {
          def map(r:Record) : DifferenceEvent = {
            recordToReportedDifferenceEventAsDifferenceEvent(r)
          }
        })
    }
  }

  def streamUnmatchedEvents(pairRef:PairRef, handler:(ReportedDifferenceEvent) => Unit) = {

    db.execute { t =>
      val cursor =  t.select(DIFFS.getFields).
                      select(PAIRS.SPACE, PAIRS.NAME.as(PAIR_NAME_ALIAS)).
                      select(ESCALATIONS.NAME.as(ESCALATION_NAME_ALIAS)).
                      from(DIFFS).
                      join(PAIRS).
                        on(PAIRS.EXTENT.equal(DIFFS.EXTENT)).
                      leftOuterJoin(ESCALATION_RULES).
                        on(ESCALATION_RULES.ID.eq(DIFFS.NEXT_ESCALATION)).
                      leftOuterJoin(ESCALATIONS).
                        on(ESCALATIONS.EXTENT.eq(ESCALATION_RULES.EXTENT)).
                          and(ESCALATIONS.NAME.eq(ESCALATION_RULES.ESCALATION)).
                      where(PAIRS.SPACE.equal(pairRef.space)).
                        and(PAIRS.NAME.equal(pairRef.name)).
                        and(DIFFS.IS_MATCH.equal(false)).
                        and(DIFFS.IGNORED.equal(false)).
                      fetchLazy()

      db.processAsStream(cursor, (r:Record) => handler(recordToReportedDifferenceEvent(r).asExternalReportedDifferenceEvent ))
    }
  }

  def retrievePagedEvents(pair: PairRef, interval: Interval, offset: Int, length: Int, options:EventOptions = EventOptions()) = {

    db.execute { t =>
      val query = t.select(DIFFS.getFields).
                    select(PAIRS.NAME.as(PAIR_NAME_ALIAS), PAIRS.SPACE).
                    select(ESCALATIONS.NAME.as(ESCALATION_NAME_ALIAS)).
                    from(DIFFS).
                    join(PAIRS).
                      on(PAIRS.EXTENT.equal(DIFFS.EXTENT)).
                    leftOuterJoin(ESCALATION_RULES).
                      on(ESCALATION_RULES.ID.eq(DIFFS.NEXT_ESCALATION)).
                    leftOuterJoin(ESCALATIONS).
                      on(ESCALATIONS.EXTENT.eq(ESCALATION_RULES.EXTENT)).
                        and(ESCALATIONS.NAME.eq(ESCALATION_RULES.ESCALATION)).
                    where(PAIRS.SPACE.equal(pair.space)).
                      and(PAIRS.NAME.equal(pair.name)).
                      and(DIFFS.DETECTED_AT.greaterOrEqual(dateTimeToTimestamp(interval.getStart))).
                      and(DIFFS.DETECTED_AT.lessThan(dateTimeToTimestamp(interval.getEnd))).
                      and(DIFFS.IS_MATCH.equal(false))

      val results =
        if (! options.includeIgnored)
          query.and(DIFFS.IGNORED.equal(false)).limit(length).offset(offset).fetch()
        else
        // TODO why shouldn't the query be ordered this way when ignored events are excluded?
          query.orderBy(DIFFS.SEQ_ID.asc()).limit(length).offset(offset).fetch()

      results.
        map(new RecordMapper[Record, DifferenceEvent] {
          def map(r:Record) : DifferenceEvent = {
            recordToReportedDifferenceEventAsDifferenceEvent(r)
          }
        })
    }
  }

  def countUnmatchedEvents(pair: PairRef, start:DateTime, end:DateTime):Int = {

    db.execute { t =>
      var query = t.select(count(DIFFS.SEQ_ID)).
                    from(DIFFS).
                    join(PAIRS).
                      on(PAIRS.EXTENT.equal(DIFFS.EXTENT)).
                    where(PAIRS.SPACE.equal(pair.space)).
                      and(PAIRS.NAME.equal(pair.name)).
                      and(DIFFS.IS_MATCH.equal(false)).
                      and(DIFFS.IGNORED.equal(false))

      if (start != null)
        query = query.and(DIFFS.DETECTED_AT.greaterOrEqual(dateTimeToTimestamp(start)))
      if (end != null)
        query = query.and(DIFFS.DETECTED_AT.lessThan(dateTimeToTimestamp(end)))

      Option(query.fetchOne().getValue(0).asInstanceOf[java.lang.Number])
        .getOrElse(java.lang.Integer.valueOf(0)).intValue()
    }
  }

  def retrieveAggregates(pair:PairRef, start:DateTime, end:DateTime, aggregateMinutes:Option[Int]):Seq[AggregateTile] =
    aggregationCache.retrieveAggregates(pair, start, end, aggregateMinutes)

  def getEvent(space:Long, evtSeqId: String) = db.execute { t =>
    getEventBySequenceId(t, evtSeqId.toLong).map(_.asDifferenceEvent).getOrElse {
      throw new InvalidSequenceNumberException(evtSeqId)
    }
  }

  def expireMatches(cutoff:DateTime) = db.execute { t =>
    val deleted =
      t.delete(DIFFS).
      where(DIFFS.LAST_SEEN.lessThan(dateTimeToTimestamp(cutoff))).
      and(DIFFS.IS_MATCH.equal(true)).
      execute()

    if (deleted > 0) {

      logger.info("Expired %s events".format(deleted))
      reportedEvents.evictAll()

      /*
      val cachedEvents = reportedEvents.valueSubset("isMatch")
      // TODO Index the cache and add a date predicate rather than doing this manually
      cachedEvents.foreach(e => {
        if (e.lastSeen.isBefore(cutoff)){
          reportedEvents.evict(e.objId)
        }
      })
      */
    }
  }

  def pendingEscalatees(cutoff:DateTime, callback:(DifferenceEvent) => Unit) = db.execute { t =>
    val escalatees =
      t.select(DIFFS.getFields).
        select(PAIRS.NAME.as(PAIR_NAME_ALIAS), PAIRS.SPACE).
        select(ESCALATIONS.NAME.as(ESCALATION_NAME_ALIAS)).
        from(DIFFS).
        join(PAIRS).
          on(PAIRS.EXTENT.eq(DIFFS.EXTENT)).
        leftOuterJoin(ESCALATION_RULES).
          on(ESCALATION_RULES.ID.eq(DIFFS.NEXT_ESCALATION)).
        join(ESCALATIONS).
          on(ESCALATIONS.EXTENT.eq(ESCALATION_RULES.EXTENT)).
            and(ESCALATIONS.NAME.eq(ESCALATION_RULES.ESCALATION)).
        where(DIFFS.NEXT_ESCALATION_TIME.lessOrEqual(dateTimeToTimestamp(cutoff))).
        fetchLazy()

    db.processAsStream(escalatees, (r:Record) => callback(recordToReportedDifferenceEventAsDifferenceEvent(r)))
  }


  def scheduleEscalation(diff: DifferenceEvent, escalationName: String, escalationTime: DateTime) = {

    db.execute { t =>
      t.update(DIFFS).
          set(DIFFS.NEXT_ESCALATION,
            t.select(ESCALATION_RULES.ID).
              from(ESCALATION_RULES).
              join(PAIRS).
                on(ESCALATION_RULES.EXTENT.eq(PAIRS.EXTENT)).
              where(ESCALATION_RULES.ESCALATION.eq(escalationName)).
                and(PAIRS.SPACE.eq(diff.objId.pair.space)).
                and(PAIRS.NAME.eq(diff.objId.pair.name)).
              asField().
              asInstanceOf[Field[LONG]]).
          set(DIFFS.NEXT_ESCALATION_TIME, dateTimeToTimestamp(escalationTime)).
        where(DIFFS.SEQ_ID.eq(diff.sequenceId)).
        execute()
    }

    reportedEvents.evict(diff.objId)
  }

  def unscheduleEscalations(pair:PairRef) = {

    db.execute { t =>
      t.update(DIFFS).
          set(DIFFS.NEXT_ESCALATION, null:LONG).
          set(DIFFS.NEXT_ESCALATION_TIME, null:Timestamp).
        where(DIFFS.EXTENT.eq(
          t.select(PAIRS.EXTENT).
            from(PAIRS).
            where(PAIRS.SPACE.eq(pair.space).
              and(PAIRS.NAME.eq(pair.name))
      ))).execute()
    }

    preenReportedEventsCache(pair)
  }

  def purgeOrphanedEvents = {

    val (diffs, escalations, extents) = db.execute(t => {

      val diffs = t.delete(DIFFS).
                    whereNotExists(
                      t.select(field("1")).
                        from(PAIRS).
                        where(PAIRS.EXTENT.eq(DIFFS.EXTENT))
                    ).execute()

      val escalations = t.delete(ESCALATION_RULES).
                          whereNotExists(
                          t.select(field("1")).
                            from(PAIRS).
                            where(ESCALATION_RULES.EXTENT.eq(PAIRS.EXTENT))
                        ).execute()

      val extents = t.delete(EXTENTS).
                      whereNotExists(
                        t.select(field("1")).
                          from(PAIRS).
                          where(EXTENTS.ID.eq(PAIRS.EXTENT))
                      ).execute()

      (diffs,escalations,extents)
    })

    logger.debug("Vacuumed %s diff(s), %s pending escalation(s) and %s extent(s)".format(diffs, escalations, extents))

    diffs
  }

  def clearAllDifferences = db.execute { t =>
    reset
    t.truncate(DIFFS).execute()
    t.truncate(PENDING_DIFFS).execute()
  }

  private def orphanExtentForPair(t:Factory, pair:PairRef) = {
    val nextExtent = sequenceProvider.nextSequenceValue(SequenceName.EXTENTS)

    t.insertInto(EXTENTS).
        set(EXTENTS.ID, nextExtent:LONG).
      execute()

    t.update(ESCALATION_RULES).
        set(ESCALATION_RULES.EXTENT, null:LONG).
        set(ESCALATION_RULES.ESCALATION, null:String).
      where(ESCALATION_RULES.EXTENT.eq(
        t.select(PAIRS.EXTENT).
          from(PAIRS).
          where(PAIRS.SPACE.eq(pair.space).
            and(PAIRS.NAME.eq(pair.name)))
      )).
      execute()

      t.delete(ESCALATIONS).
        where(ESCALATIONS.EXTENT.eq(
        t.select(PAIRS.EXTENT).
          from(PAIRS).
          where(PAIRS.SPACE.eq(pair.space).
            and(PAIRS.NAME.eq(pair.name)))
      )).
      execute()



    val rows = t.update(PAIRS).
        set(PAIRS.EXTENT, nextExtent:LONG).
      where(PAIRS.SPACE.eq(pair.space)).
        and(PAIRS.NAME.eq(pair.name)).
      execute()

    if (rows == 0) {
      val msg = " %s No pair to orphan".format(formatAlertCode(pair, INCONSISTENT_DIFF_STORE))
      logger.warn(msg)
    }
  }

  private def initializeExistingSequences() = db.execute { t =>

    val maxSeqId = t.select(nvl(max(DIFFS.SEQ_ID).asInstanceOf[Field[Any]], 0)).
                     from(DIFFS).
                     fetchOne().
                     getValueAsBigInteger(0).
                     longValue()

    synchronizeSequence(SequenceName.SPACES, maxSeqId)

    val maxExtentId = t.select(nvl(max(EXTENTS.ID).asInstanceOf[Field[Any]], 0)).
                        from(EXTENTS).
                        fetchOne().
                        getValueAsBigInteger(0).
                        longValue()

    synchronizeSequence(SequenceName.EXTENTS, maxExtentId)

    t.select(PENDING_DIFFS.SPACE, max(PENDING_DIFFS.SEQ_ID).as("max_seq_id")).
      from(PENDING_DIFFS).
      groupBy(PENDING_DIFFS.SPACE).
      fetch().
      foreach( (record:Record) => {
        val space = record.getValue(PENDING_DIFFS.SPACE)
        val key = pendingEventSequenceKey(space)
        val persistentValue = record.getValueAsBigInteger("max_seq_id").longValue()
        val currentValue = sequenceProvider.currentSequenceValue(key)
        if (persistentValue > currentValue) {
          sequenceProvider.upgradeSequenceValue(key, currentValue, persistentValue)
        }
    })
  }

  private def synchronizeSequence(sequence:SequenceName, persistentValue:Long) = {

    val currentValue = sequenceProvider.currentSequenceValue(sequence)

    if (persistentValue > currentValue) {
      sequenceProvider.upgradeSequenceValue(sequence, currentValue, persistentValue)
    }
  }

  private def pendingEventSequenceKey(space: Long) = "%s.pending.events".format(space)

  private def getPendingEvent(t:Factory, id: VersionID) = {

    val query = (f: Factory) =>
      f.selectFrom(PENDING_DIFFS).
        where(PENDING_DIFFS.SPACE.equal(id.pair.space)).
          and(PENDING_DIFFS.PAIR.equal(id.pair.name)).
          and(PENDING_DIFFS.ENTITY_ID.equal(id.id))

    getEventInternal(t, id, pendingEvents, query, recordToPendingDifferenceEvent, PendingDifferenceEvent.nonExistent)
  }

  private def createPendingEvent(pending:PendingDifferenceEvent) = db.execute { t =>

    val space = pending.objId.pair.space
    val pair = pending.objId.pair.name
    val nextSeqId: java.lang.Long = nextPendingEventSequenceValue(space)

    t.insertInto(PENDING_DIFFS).
        set(PENDING_DIFFS.SEQ_ID, nextSeqId).
        set(PENDING_DIFFS.SPACE, space:LONG).
        set(PENDING_DIFFS.PAIR, pair).
        set(PENDING_DIFFS.ENTITY_ID, pending.objId.id).
        set(PENDING_DIFFS.DETECTED_AT, dateTimeToTimestamp(pending.detectedAt)).
        set(PENDING_DIFFS.LAST_SEEN, dateTimeToTimestamp(pending.lastSeen)).
        set(PENDING_DIFFS.UPSTREAM_VSN, pending.upstreamVsn).
        set(PENDING_DIFFS.DOWNSTREAM_VSN, pending.downstreamVsn).
      execute()
    
    pending.oid = nextSeqId

    pendingEvents.put(pending.objId,pending)
  }

  private def removePendingEvent(f: Factory, pending:PendingDifferenceEvent) = {
    f.delete(PENDING_DIFFS).where(PENDING_DIFFS.SEQ_ID.equal(pending.oid)).execute()
    pendingEvents.evict(pending.objId)
  }

  private def updatePendingEvent(t: Factory, pending:PendingDifferenceEvent, upstreamVsn:String, downstreamVsn:String, seenAt:DateTime) = {
    pending.upstreamVsn = upstreamVsn
    pending.downstreamVsn = downstreamVsn
    pending.lastSeen = seenAt

    t.update(PENDING_DIFFS).
        set(PENDING_DIFFS.UPSTREAM_VSN, upstreamVsn).
        set(PENDING_DIFFS.DOWNSTREAM_VSN, downstreamVsn).
        set(PENDING_DIFFS.LAST_SEEN, dateTimeToTimestamp(seenAt)).
      where(PENDING_DIFFS.SEQ_ID.equal(pending.oid)).
      execute()

    val cachedEvents = pendingEvents.valueSubset("oid", pending.oid.toString)
    cachedEvents.foreach(e => pendingEvents.put(e.objId, pending))

  }

  private def preenPendingEventsCache(attribute:String, value:String) = {
    val cachedEvents = pendingEvents.valueSubset(attribute, value)
    cachedEvents.foreach(e => pendingEvents.evict(e.objId))
  }

  private def preenExtentsCache(space:Long) = extentsByPair.keySubset(ExtentBySpacePredicate(space)).evictAll
  private def preenReportedEventsCache(pair:PairRef) = reportedEvents.keySubset(EventByPairPredicate(pair)).evictAll

  private def prefetchPendingEvents(prefetchLimit: Int) = db.execute { t =>
    def prefillCache(r: PendingDiffsRecord) {
      val e = recordToPendingDifferenceEvent(r)
      pendingEvents.put(e.objId, e)
    }

    db.processAsStream(t.selectFrom(PENDING_DIFFS).limit(prefetchLimit).fetchLazy(), prefillCache)
  }


  private def getEventBySequenceId(t:Factory, id: Long) : Option[InternalReportedDifferenceEvent] = {
    Option(
      t.select(DIFFS.getFields).
        select(PAIRS.SPACE, PAIRS.NAME.as(PAIR_NAME_ALIAS)).
        select(ESCALATIONS.NAME.as(ESCALATION_NAME_ALIAS)).
        from(DIFFS).
        join(PAIRS).
          on(PAIRS.EXTENT.eq(DIFFS.EXTENT)).
        leftOuterJoin(ESCALATION_RULES).
          on(ESCALATION_RULES.ID.eq(DIFFS.NEXT_ESCALATION)).
        leftOuterJoin(ESCALATIONS).
          on(ESCALATIONS.EXTENT.eq(ESCALATION_RULES.EXTENT)).
            and(ESCALATIONS.NAME.eq(ESCALATION_RULES.ESCALATION)).
        where(DIFFS.SEQ_ID.eq(id)).
        fetchOne()).map(recordToReportedDifferenceEvent)
  }

  private def getEventById(t:Factory, id: VersionID) : InternalReportedDifferenceEvent = {

    val query = (f: Factory) =>
      f.select(DIFFS.getFields).
        select(PAIRS.SPACE, PAIRS.NAME.as(PAIR_NAME_ALIAS)).
        select(ESCALATIONS.NAME.as(ESCALATION_NAME_ALIAS)).
        from(DIFFS).
        join(PAIRS).
          on(PAIRS.EXTENT.equal(DIFFS.EXTENT)).
        leftOuterJoin(ESCALATION_RULES).
          on(ESCALATION_RULES.ID.eq(DIFFS.NEXT_ESCALATION)).
        leftOuterJoin(ESCALATIONS).
          on(ESCALATIONS.EXTENT.eq(ESCALATION_RULES.EXTENT)).
            and(ESCALATIONS.NAME.eq(ESCALATION_RULES.ESCALATION)).
        where(PAIRS.SPACE.equal(id.pair.space).
          and(PAIRS.NAME.equal(id.pair.name)).
          and(DIFFS.ENTITY_ID.equal(id.id)))

    getEventInternal(t, id, reportedEvents, query, recordToReportedDifferenceEvent, nonExistentReportedEvent)
  }

  private def getEventInternal[R <: Record, O](t:Factory,
                                               id: VersionID,
                                               cache:CachedMap[VersionID, O],
                                               query: Factory => ResultQuery[R],
                                               converter: R => O,
                                               nonExistentMarker: O) = {

    def eventOrNonExistentMarker() = Option(query(t).fetchOne()).map(converter).getOrElse(nonExistentMarker)


    cache.readThrough(id, eventOrNonExistentMarker)

  }

  private def reportedEventExists(event:InternalReportedDifferenceEvent) = event.seqId != NON_EXISTENT_SEQUENCE_ID

  private def addReportableMismatch(t:Factory, reportableUnmatched:InternalReportedDifferenceEvent) : (DifferenceEventStatus, DifferenceEvent) = {
    val event = getEventById(t, reportableUnmatched.objId)

    if (reportedEventExists(event)) {
      event.state match {
        case MatchState.IGNORED =>
          if (identicalEventVersions(event, reportableUnmatched)) {
            // Update the last time it was seen
            val updatedEvent = updateTimestampForPreviouslyReportedEvent(event, reportableUnmatched.lastSeen)
            (UnchangedIgnoredEvent, updatedEvent.asDifferenceEvent)
          } else {
            (UpdatedIgnoredEvent, ignorePreviouslyReportedEvent(event))
          }
        case MatchState.UNMATCHED =>
          // We've already got an unmatched event. See if it matches all the criteria.
          if (identicalEventVersions(event, reportableUnmatched)) {
            // Update the last time it was seen
            val updatedEvent = updateTimestampForPreviouslyReportedEvent(event, reportableUnmatched.lastSeen)
            // No need to update the aggregate cache, since it won't affect the aggregate counts
            (UnchangedUnmatchedEvent, updatedEvent.asDifferenceEvent)
          } else {
            reportableUnmatched.seqId = event.seqId
            reportableUnmatched.extent = event.extent
            (UpdatedUnmatchedEvent, upgradePreviouslyReportedEvent(t, reportableUnmatched))
          }

        case MatchState.MATCHED =>
          // The difference has re-occurred. Remove the match, and add a difference.
          reportableUnmatched.seqId = event.seqId
          (ReturnedUnmatchedEvent, upgradePreviouslyReportedEvent(t, reportableUnmatched))
      }
    }
    else {

      val nextSeqId = nextEventSequenceValue

      try {
        db.execute(t => (NewUnmatchedEvent, createReportedEvent(t, reportableUnmatched, nextSeqId)))
      } catch {
        case x: Exception =>
          val pair = reportableUnmatched.objId.pair.name
          val alert = formatAlertCode(reportableUnmatched.objId.pair.space, pair, INCONSISTENT_DIFF_STORE)
          val msg = " %s Could not insert event %s, next sequence id was %s".format(alert, reportableUnmatched, nextSeqId)
          logger.error(msg)

          throw x
      }
    }

  }

  private def identicalEventVersions(first:InternalReportedDifferenceEvent, second:InternalReportedDifferenceEvent) =
    first.upstreamVsn == second.upstreamVsn && first.downstreamVsn == second.downstreamVsn

  private def updateAndConvertEvent(t:Factory, evt:InternalReportedDifferenceEvent, previousDetectionTime:DateTime) = {
    val res = upgradePreviouslyReportedEvent(t, evt)
    updateAggregateCache(evt.objId.pair, previousDetectionTime)
    res
  }

  private def updateAndConvertEvent(t:Factory, evt:InternalReportedDifferenceEvent) = {
    var res = upgradePreviouslyReportedEvent(t, evt)
    updateAggregateCache(evt.objId.pair, res.detectedAt)
    res
  }


  /**
   * Does not uprev the sequence id for this event
   */
  private def updateTimestampForPreviouslyReportedEvent(event:InternalReportedDifferenceEvent, lastSeen:DateTime) = {

    db.execute { t =>
      t.update(DIFFS).
        set(DIFFS.LAST_SEEN,dateTimeToTimestamp(lastSeen)).
        where(DIFFS.SEQ_ID.eq(event.seqId)).
          and(DIFFS.EXTENT.eq(event.extent)).
        execute()
    }

    event.lastSeen = lastSeen

    reportedEvents.put(event.objId, event)

    event
  }

  /**
   * Uprevs the sequence id for this event
   */
  private def upgradePreviouslyReportedEvent(t:Factory, reportableUnmatched:InternalReportedDifferenceEvent) = {

    val nextSeqId: java.lang.Long = nextEventSequenceValue

    val escalationChanges:Map[Field[_], _] = if (reportableUnmatched.isMatch)
      Map(DIFFS.NEXT_ESCALATION -> null, DIFFS.NEXT_ESCALATION_TIME -> null)
    else
      Map()

    val rows =
      t.update(DIFFS).
          set(DIFFS.SEQ_ID, nextSeqId).
          set(DIFFS.ENTITY_ID, reportableUnmatched.objId.id).
          set(DIFFS.IS_MATCH, java.lang.Boolean.valueOf(reportableUnmatched.isMatch)).
          set(DIFFS.DETECTED_AT, dateTimeToTimestamp(reportableUnmatched.detectedAt)).
          set(DIFFS.LAST_SEEN, dateTimeToTimestamp(reportableUnmatched.lastSeen)).
          set(DIFFS.UPSTREAM_VSN, reportableUnmatched.upstreamVsn).
          set(DIFFS.DOWNSTREAM_VSN, reportableUnmatched.downstreamVsn).
          set(DIFFS.IGNORED, java.lang.Boolean.valueOf(reportableUnmatched.ignored)).
          set(escalationChanges).
        where(DIFFS.SEQ_ID.eq(reportableUnmatched.seqId)).
          and(DIFFS.EXTENT.eq(reportableUnmatched.extent)).
        execute()

    if (rows == 0) {
      val pair = reportableUnmatched.objId.pair.name
      val space = reportableUnmatched.objId.pair.space
      val alert = formatAlertCode(space, pair, INCONSISTENT_DIFF_STORE)
      val msg = " %s No rows updated for previously reported diff %s, next sequence id was %s".format(alert, reportableUnmatched, nextSeqId)
      logger.error(msg, new Exception().fillInStackTrace())
      throw new IllegalStateException(msg)
    }

    updateSequenceValueAndCache(reportableUnmatched, nextSeqId)
  }

  private def updateSequenceValueAndCache(event:InternalReportedDifferenceEvent, seqId:Long) : DifferenceEvent = {
    event.seqId = seqId
    reportedEvents.put(event.objId, event)
    event.asDifferenceEvent
  }

  /**
   * Uprevs the sequence id for this event
   */
  private def ignorePreviouslyReportedEvent(event:InternalReportedDifferenceEvent) = {

    val nextSeqId: java.lang.Long = nextEventSequenceValue

    db.execute { t =>
      t.update(DIFFS).
          set(DIFFS.LAST_SEEN, dateTimeToTimestamp(event.lastSeen)).
          set(DIFFS.IGNORED, java.lang.Boolean.TRUE).
          set(DIFFS.SEQ_ID, nextSeqId).
        where(DIFFS.SEQ_ID.equal(event.seqId)).
          and(DIFFS.EXTENT.equal(event.extent)).
        execute()
    }

    updateSequenceValueAndCache(event, nextSeqId)
  }

  private def nextEventSequenceValue = sequenceProvider.nextSequenceValue(SequenceName.SPACES)
  private def nextPendingEventSequenceValue(space:Long) = sequenceProvider.nextSequenceValue(pendingEventSequenceKey(space))

  private def createReportedEvent(t: Factory, evt:InternalReportedDifferenceEvent, nextSeqId: Long) = {

    // I would have like to have done this extent lookup as a subselect in the insert statement
    // but we need the value of the extent to put back into the cache.
    // We could consider just invalidating the cache now and reading the extent through, but
    // this would cause a lot of DB traffic that we can avoid.

    val extent = getExtent(t, evt.objId.pair)

    t.insertInto(DIFFS).
        set(DIFFS.SEQ_ID, nextSeqId:LONG).
        set(DIFFS.EXTENT, extent:LONG).
        set(DIFFS.ENTITY_ID, evt.objId.id).
        set(DIFFS.IS_MATCH, java.lang.Boolean.valueOf(evt.isMatch)).
        set(DIFFS.DETECTED_AT, dateTimeToTimestamp(evt.detectedAt)).
        set(DIFFS.LAST_SEEN, dateTimeToTimestamp(evt.lastSeen)).
        set(DIFFS.UPSTREAM_VSN, evt.upstreamVsn).
        set(DIFFS.DOWNSTREAM_VSN, evt.downstreamVsn).
        set(DIFFS.IGNORED, java.lang.Boolean.valueOf(evt.ignored)).
      execute()


    evt.extent = extent

    updateAggregateCache(evt.objId.pair, evt.detectedAt)
    updateSequenceValueAndCache(evt, nextSeqId)
  }

  private def getExtent(t:Factory, ref:PairRef) = {
    extentsByPair.readThrough(ref, () => {
      val extent =  t.select(nvl(max(PAIRS.EXTENT).asInstanceOf[Field[Any]],-1)).
                      from(PAIRS).
                      where(PAIRS.SPACE.eq(ref.space)).
                        and(PAIRS.NAME.eq(ref.name)).
                      fetchOne().getValueAsLong(0)

      if (extent < 0) {
        // This should never happen for an exisitng pair
        val msg = " %s No extent for pair - check whether the pair exists".format(formatAlertCode(ref, INCONSISTENT_DIFF_STORE))
        throw new IllegalStateException(msg)
      }

      extent
    })
  }

  private def updateAggregateCache(pair:PairRef, detectedAt:DateTime) =
    aggregationCache.onStoreUpdate(pair, detectedAt)

  private def removeLatestRecordedVersion(t:Factory, pair: PairRef) = {
    t.delete(STORE_CHECKPOINTS).
      where(STORE_CHECKPOINTS.SPACE.equal(pair.space)).
        and(STORE_CHECKPOINTS.PAIR.equal(pair.name)).
      execute()
  }

  private def removePendingDifferences(t:Factory, space:Long) = {
    t.delete(PENDING_DIFFS).
      where(PENDING_DIFFS.SPACE.equal(space)).
      execute()
  }

  private def removePendingDifferences(t:Factory, pair:PairRef) = {
    t.delete(PENDING_DIFFS).
      where(PENDING_DIFFS.PAIR.equal(pair.name)).
        and(PENDING_DIFFS.SPACE.equal(pair.space)).
      execute()
  }


  private def recordToReportedDifferenceEvent(r: Record) = {

    val event = new InternalReportedDifferenceEvent(
      seqId = r.getValue(DIFFS.SEQ_ID),
      extent = r.getValue(DIFFS.EXTENT),
      objId = VersionID(pair = PairRef(
        space = r.getValue(PAIRS.SPACE),
        name = r.getValue(PAIRS.NAME.as(PAIR_NAME_ALIAS))),
        id = r.getValue(DIFFS.ENTITY_ID)),
      isMatch = r.getValue(DIFFS.IS_MATCH),
      detectedAt = timestampToDateTime(r.getValue(DIFFS.DETECTED_AT)),
      lastSeen = timestampToDateTime(r.getValue(DIFFS.LAST_SEEN)),
      upstreamVsn = r.getValue(DIFFS.UPSTREAM_VSN),
      downstreamVsn = r.getValue(DIFFS.DOWNSTREAM_VSN),
      ignored = r.getValue(DIFFS.IGNORED),
      nextEscalationId = r.getValue(DIFFS.NEXT_ESCALATION))

    if (event.nextEscalationId != null) {
      event.nextEscalationName = r.getValue(ESCALATIONS.NAME.as(ESCALATION_NAME_ALIAS))
      event.nextEscalationTime = timestampToDateTime(r.getValue(DIFFS.NEXT_ESCALATION_TIME))
    }

    event
  }

  private def recordToReportedDifferenceEventAsDifferenceEvent(r: Record) =
    recordToReportedDifferenceEvent(r).asDifferenceEvent

  private def recordToPendingDifferenceEvent(r: Record) = {
    PendingDifferenceEvent(oid = r.getValue(PENDING_DIFFS.SEQ_ID),
      objId = VersionID(pair = PairRef(
        space = r.getValue(PENDING_DIFFS.SPACE),
        name = r.getValue(PENDING_DIFFS.PAIR)),
        id = r.getValue(PENDING_DIFFS.ENTITY_ID)),
      detectedAt = timestampToDateTime(r.getValue(PENDING_DIFFS.DETECTED_AT)),
      lastSeen = timestampToDateTime(r.getValue(PENDING_DIFFS.LAST_SEEN)),
      upstreamVsn = r.getValue(PENDING_DIFFS.UPSTREAM_VSN),
      downstreamVsn = r.getValue(PENDING_DIFFS.DOWNSTREAM_VSN))
  }
}

case class InternalReportedDifferenceEvent(
   @BeanProperty var seqId:java.lang.Long = null,
   @BeanProperty var extent:java.lang.Long = null,
   @BeanProperty var objId:VersionID = null,
   @BeanProperty var detectedAt:DateTime = null,
   @BeanProperty var isMatch:Boolean = false,
   @BeanProperty var upstreamVsn:String = null,
   @BeanProperty var downstreamVsn:String = null,
   @BeanProperty var lastSeen:DateTime = null,
   @BeanProperty var ignored:Boolean = false,
   @BeanProperty var nextEscalationId:java.lang.Long = null,
   @BeanProperty var nextEscalationName:String = null,
   @BeanProperty var nextEscalationTime:DateTime = null
) {

  def this() = this(seqId = -1)

  def state = if (isMatch) {
    MatchState.MATCHED
  } else {
    if (ignored) {
      MatchState.IGNORED
    } else {
      MatchState.UNMATCHED
    }

  }

  def asExternalReportedDifferenceEvent = {
    val escalationId = nextEscalationId match {
      case null   => -1L
      case x:LONG => x.toLong
    }
    ReportedDifferenceEvent(seqId,extent,objId,detectedAt,isMatch,upstreamVsn,downstreamVsn,lastSeen,ignored, escalationId, nextEscalationTime)
  }

  def asDifferenceEvent =
    DifferenceEvent(seqId.toString, objId, detectedAt, state, upstreamVsn, downstreamVsn, lastSeen,
      nextEscalationName, nextEscalationTime)
}

case class PendingDifferenceEvent(
  @BeanProperty var oid:java.lang.Long = null,
  @BeanProperty var objId:VersionID = null,
  @BeanProperty var detectedAt:DateTime = null,
  @BeanProperty var upstreamVsn:String = null,
  @BeanProperty var downstreamVsn:String = null,
  @BeanProperty var lastSeen:DateTime = null
) extends java.io.Serializable {

  def this() = this(oid = null)



  def convertToUnmatched = InternalReportedDifferenceEvent(
    objId = objId,
    detectedAt = detectedAt,
    isMatch = false,
    upstreamVsn = upstreamVsn,
    downstreamVsn = downstreamVsn,
    lastSeen = lastSeen)

  /**
   * Indicates whether a cache entry is a real pending event or just a marker to mean something other than null
   */
  def exists() = oid > -1

}

object PendingDifferenceEvent {

  /**
   * Since we cannot use scala Options in the map, we need to denote a non-existent event
   */
  val nonExistent = PendingDifferenceEvent(oid = -1)
}

case class StoreCheckpoint(
  @BeanProperty var pair:PairRef,
  @BeanProperty var latestVersion:java.lang.Long = null
) {
  def this() = this(pair = null)
}

/**
 * Convenience wrapper for a compound primary key
 */
case class DomainNameScopedKey(@BeanProperty var pair:String = null,
                               @BeanProperty var space:Long = -1L) extends java.io.Serializable
{
  def this() = this(pair = null)
}

case class ExtentBySpacePredicate(@BeanProperty var space: java.lang.Long) extends KeyPredicate[PairRef] {
  def this() = this(space = -1L)
  def constrain(key: PairRef) = key.space == space
}

case class EventByPairPredicate(@BeanProperty var pair: PairRef) extends KeyPredicate[VersionID] {
  def this() = this(pair = null)
  def constrain(key: VersionID) = key.pair == pair
}
