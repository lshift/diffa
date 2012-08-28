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

import net.lshift.diffa.kernel.events.VersionID
import reflect.BeanProperty
import scala.collection.JavaConversions._
import org.joda.time.{DateTime, Interval}
import net.lshift.diffa.kernel.hooks.HookManager
import net.lshift.diffa.kernel.config.{JooqConfigStoreCompanion, PairRef}
import net.lshift.diffa.kernel.util.cache.{CachedMap, CacheProvider}
import net.lshift.diffa.kernel.util.sequence.SequenceProvider
import net.lshift.diffa.kernel.util.AlertCodes._
import net.lshift.diffa.schema.jooq.DatabaseFacade
import net.lshift.diffa.schema.jooq.DatabaseFacade.{timestampToDateTime, dateTimeToTimestamp}
import net.lshift.diffa.schema.Tables._
import net.lshift.diffa.schema.tables.records.{PendingDiffsRecord, DiffsRecord}
import org.jooq.impl.Factory._
import net.lshift.diffa.kernel.util.MissingObjectException
import org.jooq.impl.Factory
import org.slf4j.LoggerFactory
import org.jooq.{Field, ResultQuery, Record}
import java.lang.{Long => LONG}
import java.sql.Timestamp

/**
 * Hibernate backed Domain Cache provider.
 */
class JooqDomainDifferenceStore(db: DatabaseFacade,
                                cacheProvider:CacheProvider,
                                sequenceProvider:SequenceProvider,
                                val hookManager:HookManager)
    extends DomainDifferenceStore {

  val logger = LoggerFactory.getLogger(getClass)

  intializeExistingSequences()

  val aggregationCache = new DifferenceAggregationCache(this, cacheProvider)
  val hook = hookManager.createDifferencePartitioningHook(db)

  val pendingEvents = cacheProvider.getCachedMap[VersionID, PendingDifferenceEvent]("pending.difference.events")
  val reportedEvents = cacheProvider.getCachedMap[VersionID, ReportedDifferenceEvent]("reported.difference.events")

  /**
   * This is a marker to indicate the absence of an event in a map rather than using null
   * (using an Option is not an option in this case).
   */
  val NON_EXISTENT_SEQUENCE_ID = -1
  val nonExistentReportedEvent = ReportedDifferenceEvent(seqId = NON_EXISTENT_SEQUENCE_ID)

  /**
   * This is a heuristic that allows the cache to get prefilled if the agent is booted and
   * there were persistent pending diffs. The motivation is to reduce cache misses in subsequent calls.
   */
  val prefetchLimit = 1000 // TODO This should be a tuning parameter
  prefetchPendingEvents(prefetchLimit)


  def reset {
    pendingEvents.evictAll()
    reportedEvents.evictAll()
    aggregationCache.clear()
  }

  def removeDomain(space:Long) = {

    // If difference partitioning is enabled, ask the hook to clean up each pair. Note that we'll end up running a
    // delete over all pair differences later anyway, so we won't record the result of the removal operation.
    if (hook.isDifferencePartitioningEnabled) {

      JooqConfigStoreCompanion.listPairs(db, space).foreach(p => {
        hook.removeAllPairDifferences(space, p.key)
        removeLatestRecordedVersion(p.asRef)
      })

    }

    removeDomainDifferences(space)
    preenPendingEventsCache("objId.pair.space", space.toString)
  }

  def removePair(pair: PairRef) = {

    val hookHelped = hook.removeAllPairDifferences(pair.space, pair.name)

    db.execute { t =>
      if (!hookHelped) {
        t.delete(DIFFS).
          where(DIFFS.PAIR.equal(pair.name)).
            and(DIFFS.SPACE.equal(pair.space)).execute()
      }
      t.delete(PENDING_DIFFS).
        where(PENDING_DIFFS.PAIR.equal(pair.name)).
          and(PENDING_DIFFS.SPACE.equal(pair.space)).
        execute()
      removeLatestRecordedVersion(pair)
    }

    preenPendingEventsCache("objId.pair.name", pair.name)
  }
  
  def currentSequenceId(space:Long) = sequenceProvider.currentSequenceValue(eventSequenceKey(space)).toString

  def maxSequenceId(pair: PairRef, start:DateTime, end:DateTime) = {

    db.execute { t =>
      var query = t.select(max(DIFFS.SEQ_ID)).
        from(DIFFS).
        where(DIFFS.SPACE.equal(pair.space)).
          and(DIFFS.PAIR.equal(pair.name))

      if (start != null)
        query = query.and(DIFFS.DETECTED_AT.greaterOrEqual(dateTimeToTimestamp(start)))
      if (end != null)
        query = query.and(DIFFS.DETECTED_AT.lessThan(dateTimeToTimestamp(end)))

      Option(query.fetchOne().getValue(0).asInstanceOf[java.lang.Long])
        .getOrElse(java.lang.Long.valueOf(0)).longValue()
    }
  }

  def addPendingUnmatchedEvent(id: VersionID, lastUpdate: DateTime, upstreamVsn: String, downstreamVsn: String, seen: DateTime) = {

    val pending = getPendingEvent(id)

    if (pending.exists()) {
      updatePendingEvent(pending, upstreamVsn, downstreamVsn, seen)
    }
    else {

      val reported = getEventById(id)

      if (reportedEventExists(reported)) {
        val reportable = new ReportedDifferenceEvent(null, id, reported.detectedAt, false, upstreamVsn, downstreamVsn, seen)
        addReportableMismatch(reportable)
      }
      else {
        val pendingUnmatched = PendingDifferenceEvent(null, id, lastUpdate, upstreamVsn, downstreamVsn, seen)
        createPendingEvent(pendingUnmatched)
      }

    }
  }

  def addReportableUnmatchedEvent(id: VersionID, lastUpdate: DateTime, upstreamVsn: String, downstreamVsn: String, seen: DateTime) =
    addReportableMismatch(ReportedDifferenceEvent(null, id, lastUpdate, false, upstreamVsn, downstreamVsn, seen))


  def upgradePendingUnmatchedEvent(id: VersionID) = {
    
    val pending = getPendingEvent(id)

    if (pending.exists()) {

      // Remove the pending and report a mismatch
      try {
        db.execute { t =>
          removePendingEvent(t, pending)
          createReportedEvent(t, pending.convertToUnmatched, nextEventSequenceValue(id.pair.space))
        }
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

  }

  def cancelPendingUnmatchedEvent(id: VersionID, vsn: String) = {
    val pending = getPendingEvent(id)

    if (pending.exists()) {
      if (pending.upstreamVsn == vsn || pending.downstreamVsn == vsn) {
        db.execute(t => removePendingEvent(t, pending))
        true
      } else {
        false
      }
    }
    else {
      false
    }

  }

  def addMatchedEvent(id: VersionID, vsn: String) = {

    // Remove any pending events with the given id
    val pending = getPendingEvent(id)

    if (pending.exists()) {
      db.execute(t => removePendingEvent(t, pending))
    }

    // Find any existing events we've got for this ID
    val event = getEventById(id)

    if (reportedEventExists(event)) {
      event.state match {
        case MatchState.MATCHED => // Ignore. We've already got an event for what we want.
          event.asDifferenceEvent
        case MatchState.UNMATCHED | MatchState.IGNORED =>
          // A difference has gone away. Remove the difference, and add in a match
          val previousDetectionTime = event.detectedAt
          val newEvent = ReportedDifferenceEvent(event.seqId, id, new DateTime, true, vsn, vsn, event.lastSeen)
          updateAndConvertEvent(newEvent, previousDetectionTime)
      }
    }
    else {
      // No unmatched event. Nothing to do.
      null
    }

  }

  def ignoreEvent(space:Long, seqId:String) = {

    db.execute { t=>
      val evt = db.getById(t, DIFFS, DIFFS.SEQ_ID, new java.lang.Long(seqId), recordToReportedDifferenceEvent).getOrElse {
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

        val newEvent = ReportedDifferenceEvent(evt.seqId, evt.objId, evt.detectedAt, false,
          evt.upstreamVsn, evt.downstreamVsn, evt.lastSeen, ignored = true)
        updateAndConvertEvent(newEvent)

      } else {
        evt.asDifferenceEvent
      }

    }
  }

  def unignoreEvent(space:Long, seqId:String) = {

    db.execute { t =>
      val evt = db.getById(t, DIFFS, DIFFS.SEQ_ID, new java.lang.Long(seqId), recordToReportedDifferenceEvent).getOrElse {
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

      val newEvent = ReportedDifferenceEvent(evt.seqId, evt.objId, evt.detectedAt,
        false, evt.upstreamVsn, evt.downstreamVsn, new DateTime)
      updateAndConvertEvent(newEvent)
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
      t.selectFrom(DIFFS).
        where(DIFFS.SPACE.equal(space)).
          and(DIFFS.DETECTED_AT.greaterOrEqual(dateTimeToTimestamp(interval.getStart))).
          and(DIFFS.DETECTED_AT.lessThan(dateTimeToTimestamp(interval.getEnd))).
          and(DIFFS.IS_MATCH.equal(false)).
          and(DIFFS.IGNORED.equal(false)).
        fetch().
        toSeq.
        map(recordToReportedDifferenceEventAsDifferenceEvent)
    }
  }

  def streamUnmatchedEvents(pairRef:PairRef, handler:(ReportedDifferenceEvent) => Unit) = {

    db.execute { t =>
      val cursor = t.selectFrom(DIFFS).
        where(DIFFS.SPACE.equal(pairRef.space)).
          and(DIFFS.PAIR.equal(pairRef.name)).
          and(DIFFS.IS_MATCH.equal(false)).
          and(DIFFS.IGNORED.equal(false)).
        fetchLazy()

      db.processAsStream(cursor, recordToReportedDifferenceEvent.andThen(handler))
    }
  }

  def retrievePagedEvents(pair: PairRef, interval: Interval, offset: Int, length: Int, options:EventOptions = EventOptions()) = {

    db.execute { t =>
      val query = t.selectFrom(DIFFS).
        where(DIFFS.SPACE.equal(pair.space)).
          and(DIFFS.PAIR.equal(pair.name)).
          and(DIFFS.DETECTED_AT.greaterOrEqual(dateTimeToTimestamp(interval.getStart))).
          and(DIFFS.DETECTED_AT.lessThan(dateTimeToTimestamp(interval.getEnd))).
          and(DIFFS.IS_MATCH.equal(false))

      val results =
        if (! options.includeIgnored)
          query.and(DIFFS.IGNORED.equal(false)).limit(length).offset(offset).fetch()
        else
        // TODO why shouldn't the query be ordered this way when ignored events are excluded?
          query.orderBy(DIFFS.SEQ_ID.asc()).limit(length).offset(offset).fetch()

      results.map(recordToReportedDifferenceEventAsDifferenceEvent)
    }
  }

  def countUnmatchedEvents(pair: PairRef, start:DateTime, end:DateTime):Int = {

    db.execute { t =>
      var query = t.select(count(DIFFS.SEQ_ID)).from(DIFFS).
        where(DIFFS.SPACE.equal(pair.space)).
          and(DIFFS.PAIR.equal(pair.name)).
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

    Option( t.selectFrom(DIFFS).
              where(DIFFS.SPACE.equal(space)).
                and(DIFFS.SEQ_ID.equal(java.lang.Long.parseLong(evtSeqId))).
      fetchOne()).
    map(recordToReportedDifferenceEventAsDifferenceEvent).getOrElse {
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
      t.selectFrom(DIFFS).
        where(DIFFS.NEXT_ESCALATION_TIME.lessOrEqual(dateTimeToTimestamp(cutoff))).
        fetchLazy()

    db.processAsStream(escalatees, recordToReportedDifferenceEventAsDifferenceEvent.andThen(callback))
  }


  def scheduleEscalation(diff: DifferenceEvent, escalationName: String, escalationTime: DateTime) = {

    db.execute { t =>
      t.update(DIFFS).
          set(DIFFS.NEXT_ESCALATION, escalationName).
          set(DIFFS.NEXT_ESCALATION_TIME, dateTimeToTimestamp(escalationTime)).
        where(DIFFS.SPACE.equal(diff.objId.pair.space).
          and(DIFFS.PAIR.equal(diff.objId.pair.name)).
          and(DIFFS.ENTITY_ID.equal(diff.objId.id))).
        execute()
    }
  }

  def unscheduleEscalations(pair:PairRef) = {

    db.execute { t =>
      t.update(DIFFS).
          set(DIFFS.NEXT_ESCALATION, null:String).
          set(DIFFS.NEXT_ESCALATION_TIME, null:Timestamp).
        where(DIFFS.SPACE.equal(pair.space).
          and(DIFFS.PAIR.equal(pair.name))).
        execute()
    }
  }

  def clearAllDifferences = db.execute { t =>
    reset
    t.truncate(DIFFS).execute()
    t.truncate(PENDING_DIFFS).execute()
  }

  private def intializeExistingSequences() = db.execute { t =>

    def initializer(row: Record, generateKeyName: Long => String) = {
      val space = row.getValue(DIFFS.SPACE)
      val persistentValue  = row.getValueAsLong("max_seq_id")

      if (space != null && persistentValue != null) {

        val key = generateKeyName(space)
        val currentValue = sequenceProvider.currentSequenceValue(key)
        if (persistentValue > currentValue) {
          sequenceProvider.upgradeSequenceValue(key, currentValue, persistentValue)
        }
      }

    }

    t.select(DIFFS.SPACE, max(DIFFS.SEQ_ID).as("max_seq_id")).
      from(DIFFS).
      groupBy(DIFFS.SPACE).
      fetch().
      foreach(row => initializer(row, eventSequenceKey))

    t.select(PENDING_DIFFS.SPACE, max(PENDING_DIFFS.SEQ_ID).as("max_seq_id")).
      from(PENDING_DIFFS).
      groupBy(PENDING_DIFFS.SPACE).
      fetch().
      foreach(row => initializer(row, pendingEventSequenceKey))
  }

  private def eventSequenceKey(space: Long) = "%s.events".format(space)
  private def pendingEventSequenceKey(space: Long) = "%s.pending.events".format(space)

  private def getPendingEvent(id: VersionID) = {

    val query = (f: Factory) =>
      f.selectFrom(PENDING_DIFFS).
        where(PENDING_DIFFS.SPACE.equal(id.pair.space)).
          and(PENDING_DIFFS.PAIR.equal(id.pair.name)).
          and(PENDING_DIFFS.ENTITY_ID.equal(id.id))

    getEventInternal(id, pendingEvents, query, recordToPendingDifferenceEvent, PendingDifferenceEvent.nonExistent)
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

  private def updatePendingEvent(pending:PendingDifferenceEvent, upstreamVsn:String, downstreamVsn:String, seenAt:DateTime) = {
    pending.upstreamVsn = upstreamVsn
    pending.downstreamVsn = downstreamVsn
    pending.lastSeen = seenAt

    db.execute { t =>
      t.update(PENDING_DIFFS).
        set(PENDING_DIFFS.UPSTREAM_VSN, upstreamVsn).
        set(PENDING_DIFFS.DOWNSTREAM_VSN, downstreamVsn).
        set(PENDING_DIFFS.LAST_SEEN, dateTimeToTimestamp(seenAt)).
        where(PENDING_DIFFS.SEQ_ID.equal(pending.oid)).
        execute()
    }

    val cachedEvents = pendingEvents.valueSubset("oid", pending.oid.toString)
    cachedEvents.foreach(e => pendingEvents.put(e.objId, pending))

  }

  private def preenPendingEventsCache(attribute:String, value:String) = {
    val cachedEvents = pendingEvents.valueSubset(attribute, value)
    cachedEvents.foreach(e => pendingEvents.evict(e.objId))
  }

  private def prefetchPendingEvents(prefetchLimit: Int) = db.execute { t =>
    def prefillCache(r: PendingDiffsRecord) {
      val e = recordToPendingDifferenceEvent(r)
      pendingEvents.put(e.objId, e)
    }

    db.processAsStream(t.selectFrom(PENDING_DIFFS).limit(prefetchLimit).fetchLazy(), prefillCache)
  }

  private def getEventById(id: VersionID) = {

    val query = (f: Factory) =>
      f.selectFrom(DIFFS).
        where(DIFFS.SPACE.equal(id.pair.space)).
        and(DIFFS.PAIR.equal(id.pair.name)).
        and(DIFFS.ENTITY_ID.equal(id.id))

    getEventInternal(id, reportedEvents, query, recordToReportedDifferenceEvent, nonExistentReportedEvent)
  }

  private def getEventInternal[R <: Record, O](id: VersionID,
                                               cache:CachedMap[VersionID, O],
                                               query: Factory => ResultQuery[R],
                                               converter: R => O,
                                               nonExistentMarker: O) = {

    def eventOrNonExistentMarker() = db.execute { t =>
      Option(query(t).fetchOne()).map(converter).getOrElse(nonExistentMarker)
    }

    cache.readThrough(id, eventOrNonExistentMarker)

  }

  private def reportedEventExists(event:ReportedDifferenceEvent) = event.seqId != NON_EXISTENT_SEQUENCE_ID

  private def addReportableMismatch(reportableUnmatched:ReportedDifferenceEvent) : (DifferenceEventStatus, DifferenceEvent) = {
    val event = getEventById(reportableUnmatched.objId)

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
            (UpdatedUnmatchedEvent, upgradePreviouslyReportedEvent(reportableUnmatched))
          }

        case MatchState.MATCHED =>
          // The difference has re-occurred. Remove the match, and add a difference.
          reportableUnmatched.seqId = event.seqId
          (ReturnedUnmatchedEvent, upgradePreviouslyReportedEvent(reportableUnmatched))
      }
    }
    else {

      val nextSeqId = nextEventSequenceValue(reportableUnmatched.objId.pair.space)

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

  private def identicalEventVersions(first:ReportedDifferenceEvent, second:ReportedDifferenceEvent) =
    first.upstreamVsn == second.upstreamVsn && first.downstreamVsn == second.downstreamVsn

  private def updateAndConvertEvent(evt:ReportedDifferenceEvent, previousDetectionTime:DateTime) = {
    val res = upgradePreviouslyReportedEvent(evt)
    updateAggregateCache(evt.objId.pair, previousDetectionTime)
    res
  }

  private def updateAndConvertEvent(evt:ReportedDifferenceEvent) = {
    var res = upgradePreviouslyReportedEvent(evt)
    updateAggregateCache(evt.objId.pair, res.detectedAt)
    res
  }


  /**
   * Does not uprev the sequence id for this event
   */
  private def updateTimestampForPreviouslyReportedEvent(event:ReportedDifferenceEvent, lastSeen:DateTime) = {

    db.execute { t =>
      t.update(DIFFS).
        set(DIFFS.LAST_SEEN,dateTimeToTimestamp(lastSeen)).
        where(DIFFS.SEQ_ID.equal(event.seqId)).
          and(DIFFS.SPACE.equal(event.objId.pair.space)).
          and(DIFFS.PAIR.equal(event.objId.pair.name)).
        execute()
    }

    event.lastSeen = lastSeen

    reportedEvents.put(event.objId, event)

    event
  }

  /**
   * Uprevs the sequence id for this event
   */
  private def upgradePreviouslyReportedEvent(reportableUnmatched:ReportedDifferenceEvent) = {

    val pair = reportableUnmatched.objId.pair.name
    val space = reportableUnmatched.objId.pair.space
    val nextSeqId: java.lang.Long = nextEventSequenceValue(space)

    val rows = db.execute { t =>
      val escalationChanges:Map[Field[_], _] = if (reportableUnmatched.isMatch)
          Map(DIFFS.NEXT_ESCALATION -> null, DIFFS.NEXT_ESCALATION_TIME -> null)
        else
          Map()

      t.update(DIFFS).
          set(DIFFS.SEQ_ID, nextSeqId).
          set(DIFFS.SPACE, space:LONG).
          set(DIFFS.PAIR, pair).
          set(DIFFS.ENTITY_ID, reportableUnmatched.objId.id).
          set(DIFFS.IS_MATCH, java.lang.Boolean.valueOf(reportableUnmatched.isMatch)).
          set(DIFFS.DETECTED_AT, dateTimeToTimestamp(reportableUnmatched.detectedAt)).
          set(DIFFS.LAST_SEEN, dateTimeToTimestamp(reportableUnmatched.lastSeen)).
          set(DIFFS.UPSTREAM_VSN, reportableUnmatched.upstreamVsn).
          set(DIFFS.DOWNSTREAM_VSN, reportableUnmatched.downstreamVsn).
          set(DIFFS.IGNORED, java.lang.Boolean.valueOf(reportableUnmatched.ignored)).
          set(escalationChanges).
        where(DIFFS.SEQ_ID.equal(reportableUnmatched.seqId)).
          and(DIFFS.SPACE.equal(space)).
          and(DIFFS.PAIR.equal(pair)).
        execute()
    }

    // TODO Theoretically this should never happen ....
    if (rows == 0) {
      val alert = formatAlertCode(space, pair, INCONSISTENT_DIFF_STORE)
      val msg = " %s No rows updated for previously reported diff %s, next sequence id was %s".format(alert, reportableUnmatched, nextSeqId)
      logger.error(msg, new Exception().fillInStackTrace())
    }

    updateSequenceValueAndCache(reportableUnmatched, nextSeqId)
  }

  private def updateSequenceValueAndCache(event:ReportedDifferenceEvent, seqId:Long) : DifferenceEvent = {
    event.seqId = seqId
    reportedEvents.put(event.objId, event)
    event.asDifferenceEvent
  }

  /**
   * Uprevs the sequence id for this event
   */
  private def ignorePreviouslyReportedEvent(event:ReportedDifferenceEvent) = {

    val space = event.objId.pair.space    
    val pair = event.objId.pair.name    
    val nextSeqId: java.lang.Long = nextEventSequenceValue(space)

    db.execute { t =>
      t.update(DIFFS).
          set(DIFFS.LAST_SEEN, dateTimeToTimestamp(event.lastSeen)).
          set(DIFFS.IGNORED, java.lang.Boolean.TRUE).
          set(DIFFS.SEQ_ID, nextSeqId).
        where(DIFFS.SEQ_ID.equal(event.seqId)).
          and(DIFFS.SPACE.equal(space)).
          and(DIFFS.PAIR.equal(pair)).
        execute()
    }

    updateSequenceValueAndCache(event, nextSeqId)
  }

  private def nextEventSequenceValue(space:Long) = sequenceProvider.nextSequenceValue(eventSequenceKey(space))
  private def nextPendingEventSequenceValue(space:Long) = sequenceProvider.nextSequenceValue(pendingEventSequenceKey(space))

  private def createReportedEvent(f: Factory, evt:ReportedDifferenceEvent, nextSeqId: Long) = {

    val space = evt.objId.pair.space
    val pair = evt.objId.pair.name

    f.insertInto(DIFFS).
        set(DIFFS.SEQ_ID, java.lang.Long.valueOf(nextSeqId)).
        set(DIFFS.SPACE, space:LONG).
        set(DIFFS.PAIR, pair).
        set(DIFFS.ENTITY_ID, evt.objId.id).
        set(DIFFS.IS_MATCH, java.lang.Boolean.valueOf(evt.isMatch)).
        set(DIFFS.DETECTED_AT, dateTimeToTimestamp(evt.detectedAt)).
        set(DIFFS.LAST_SEEN, dateTimeToTimestamp(evt.lastSeen)).
        set(DIFFS.UPSTREAM_VSN, evt.upstreamVsn).
        set(DIFFS.DOWNSTREAM_VSN, evt.downstreamVsn).
        set(DIFFS.IGNORED, java.lang.Boolean.valueOf(evt.ignored)).
      execute()

    updateAggregateCache(evt.objId.pair, evt.detectedAt)
    updateSequenceValueAndCache(evt, nextSeqId)
  }

  private def updateAggregateCache(pair:PairRef, detectedAt:DateTime) =
    aggregationCache.onStoreUpdate(pair, detectedAt)

  private def removeLatestRecordedVersion(pair: PairRef) = {

    db.execute { t =>
      t.delete(STORE_CHECKPOINTS).
        where(STORE_CHECKPOINTS.SPACE.equal(pair.space)).
          and(STORE_CHECKPOINTS.PAIR.equal(pair.name)).
        execute()
    }
  }

  private def removeDomainDifferences(space:Long) = db.execute(t => {

    t.delete(STORE_CHECKPOINTS).
      where(STORE_CHECKPOINTS.SPACE.equal(space)).
      execute()

    t.delete(DIFFS).
      where(DIFFS.SPACE.equal(space)).
      execute()

    t.delete(PENDING_DIFFS).
      where(PENDING_DIFFS.SPACE.equal(space)).
      execute()
  })

  private val recordToReportedDifferenceEvent = (r: DiffsRecord) => {

    ReportedDifferenceEvent(seqId = r.getValue(DIFFS.SEQ_ID),
      objId = VersionID(pair = PairRef(
        space = r.getValue(DIFFS.SPACE),
        name = r.getValue(DIFFS.PAIR)),
        id = r.getValue(DIFFS.ENTITY_ID)),
      isMatch = r.getValue(DIFFS.IS_MATCH),
      detectedAt = timestampToDateTime(r.getValue(DIFFS.DETECTED_AT)),
      lastSeen = timestampToDateTime(r.getValue(DIFFS.LAST_SEEN)),
      upstreamVsn = r.getValue(DIFFS.UPSTREAM_VSN),
      downstreamVsn = r.getValue(DIFFS.DOWNSTREAM_VSN),
      ignored = r.getValue(DIFFS.IGNORED),
      nextEscalation = r.getValue(DIFFS.NEXT_ESCALATION),
      nextEscalationTime = timestampToDateTime(r.getValue(DIFFS.NEXT_ESCALATION_TIME)))
  }

  private val recordToReportedDifferenceEventAsDifferenceEvent =
    recordToReportedDifferenceEvent.andThen(_.asDifferenceEvent)

  private val recordToPendingDifferenceEvent = (r: PendingDiffsRecord) => {
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

case class PendingDifferenceEvent(
  @BeanProperty var oid:java.lang.Long = null,
  @BeanProperty var objId:VersionID = null,
  @BeanProperty var detectedAt:DateTime = null,
  @BeanProperty var upstreamVsn:String = null,
  @BeanProperty var downstreamVsn:String = null,
  @BeanProperty var lastSeen:DateTime = null
) extends java.io.Serializable {

  def this() = this(oid = null)



  def convertToUnmatched = ReportedDifferenceEvent(null, objId, detectedAt, false, upstreamVsn, downstreamVsn, lastSeen)

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
