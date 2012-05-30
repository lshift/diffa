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
import org.hibernate.SessionFactory
import net.lshift.diffa.kernel.util.db.SessionHelper._
import org.hibernate.Session
import net.sf.ehcache.CacheManager
import scala.collection.JavaConversions._
import org.joda.time.{DateTimeZone, DateTime, Interval}
import org.jadira.usertype.dateandtime.joda.columnmapper.TimestampColumnDateTimeMapper
import net.lshift.diffa.kernel.config.DomainScopedKey._
import net.lshift.diffa.kernel.config.Domain._
import net.lshift.diffa.kernel.hooks.HookManager
import net.lshift.diffa.kernel.config.{DomainScopedKey, Domain, DiffaPairRef, DiffaPair}
import org.hibernate.dialect.{Oracle10gDialect, Dialect}
import org.hibernate.criterion.{Projections, Restrictions}
import java.io.Serializable
import net.lshift.diffa.kernel.util.cache.{CachedMap, CacheProvider}
import net.lshift.diffa.kernel.util.db._
import net.lshift.diffa.kernel.util.sequence.SequenceProvider
import java.sql.ResultSet
import net.lshift.diffa.kernel.util.AlertCodes._

/**
 * Hibernate backed Domain Cache provider.
 */
class HibernateDomainDifferenceStore(val sessionFactory:SessionFactory,
                                     db:DatabaseFacade,
                                     cacheProvider:CacheProvider,
                                     sequenceProvider:SequenceProvider,
                                     val cacheManager:CacheManager,
                                     val dialect:Dialect,
                                     val hookManager:HookManager)
    extends DomainDifferenceStore
    with HibernateQueryUtils {

  intializeExistingSequences()

  val aggregationCache = new DifferenceAggregationCache(this, cacheManager)
  val hook = hookManager.createDifferencePartitioningHook(sessionFactory)

  val columnMapper = new TimestampColumnDateTimeMapper()

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

  def removeDomain(domain:String) = {
    // If difference partitioning is enabled, ask the hook to clean up each pair. Note that we'll end up running a
    // delete over all pair differences later anyway, so we won't record the result of the removal operation.
    if (hook.isDifferencePartitioningEnabled) {
      listPairsInDomain(domain).foreach(p => {
        hook.removeAllPairDifferences(domain, p.key)
        removeLatestRecordedVersion(p.asRef)
      })
    }

    removeDomainDifferences(domain)
    preenPendingEventsCache("objId.pair.domain", domain)
  }

  def removePair(pair: DiffaPairRef) = {
    val hookHelped = hook.removeAllPairDifferences(pair.domain, pair.key)

    sessionFactory.withSession { s =>
      if (!hookHelped) {
        executeUpdate(s, "removeDiffsByPairAndDomain", Map("pairKey" -> pair.key, "domain" -> pair.domain))
      }
      executeUpdate(s, "removePendingDiffsByPairAndDomain", Map("pairKey" -> pair.key, "domain" -> pair.domain))
      removeLatestRecordedVersion(pair)
    }

    preenPendingEventsCache("objId.pair.key", pair.key)
  }
  
  def currentSequenceId(domain:String) = sequenceProvider.currentSequenceValue(eventSequenceKey(domain)).toString

  def maxSequenceId(pair: DiffaPairRef, start:DateTime, end:DateTime) = sessionFactory.withSession(s => {
    val c = s.createCriteria(classOf[ReportedDifferenceEvent])
    c.add(Restrictions.eq("objId.pair.domain", pair.domain))
    c.add(Restrictions.eq("objId.pair.key", pair.key))
    if (start != null) c.add(Restrictions.ge("detectedAt", start))
    if (end != null) c.add(Restrictions.lt("detectedAt", end))
    c.setProjection(Projections.max("seqId"))

    val count:Option[java.lang.Long] = Option(c.uniqueResult().asInstanceOf[java.lang.Long])
    count.getOrElse(new java.lang.Long(0)).longValue()
  })

  def addPendingUnmatchedEvent(id: VersionID, lastUpdate: DateTime, upstreamVsn: String, downstreamVsn: String, seen: DateTime) = {

    val pending = getPendingEvent(id)

    if (pending.exists()) {
      updatePendingEvent(pending, upstreamVsn, downstreamVsn, seen)
    }
    else {

      val reported = getEventById(id)

      if (reportedEventExists(reported)) {
        val reportable = new ReportedDifferenceEvent(null, id, reported.detectedAt, false, upstreamVsn, downstreamVsn, seen)
        addReportableMismatch(None, reportable)
      }
      else {
        val pendingUnmatched = PendingDifferenceEvent(null, id, lastUpdate, upstreamVsn, downstreamVsn, seen)
        createPendingEvent(pendingUnmatched)
      }

    }
  }

  def addReportableUnmatchedEvent(id: VersionID, lastUpdate: DateTime, upstreamVsn: String, downstreamVsn: String, seen: DateTime) =
    addReportableMismatch(None, ReportedDifferenceEvent(null, id, lastUpdate, false, upstreamVsn, downstreamVsn, seen))


  def upgradePendingUnmatchedEvent(id: VersionID) = {

    val pending = getPendingEvent(id)

    if (pending.exists()) {

      // Remove the pending and report a mismatch
      inTransaction(tx => {
        removePendingEvent(Some(tx), pending)
        createReportedEvent(Some(tx), pending.convertToUnmatched)
      })

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
        removePendingEvent(None, pending)
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
      removePendingEvent(None, pending)
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

  def ignoreEvent(domain:String, seqId:String) = {

    val evt = db.getOrFail[ReportedDifferenceEvent](classOf[ReportedDifferenceEvent], new java.lang.Long(seqId), "ReportedDifferenceEvent")
    if (evt.objId.pair.domain != domain) {
      throw new IllegalArgumentException("Invalid domain %s for sequence id %s (expected %s)".format(domain, seqId, evt.objId.pair.domain))
    }

    if (evt.isMatch) {
      throw new IllegalArgumentException("Cannot ignore a match for %s (in domain %s)".format(seqId, domain))
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

  def unignoreEvent(domain:String, seqId:String) = {

    val evt = db.getOrFail[ReportedDifferenceEvent](classOf[ReportedDifferenceEvent], new java.lang.Long(seqId), "ReportedDifferenceEvent")
    if (evt.objId.pair.domain != domain) {
      throw new IllegalArgumentException("Invalid domain %s for sequence id %s (expected %s)".format(domain, seqId, evt.objId.pair.domain))
    }
    if (evt.isMatch) {
      throw new IllegalArgumentException("Cannot unignore a match for %s (in domain %s)".format(seqId, domain))
    }
    if (!evt.ignored) {
      throw new IllegalArgumentException("Cannot unignore an event that isn't ignored - %s (in domain %s)".format(seqId, domain))
    }

    // Generate a new event with the same details but the ignored flag cleared. This will ensure consumers
    // that are monitoring for changes will see one.

    val newEvent = ReportedDifferenceEvent(evt.seqId, evt.objId, evt.detectedAt,
      false, evt.upstreamVsn, evt.downstreamVsn, new DateTime)
    updateAndConvertEvent(newEvent)
  }

  def lastRecordedVersion(pair:DiffaPairRef) = getStoreCheckpoint(pair) match {
    case None             => None
    case Some(checkpoint) => Some(checkpoint.latestVersion)
  }

  def recordLatestVersion(pairRef:DiffaPairRef, version:Long) = sessionFactory.withSession(s => {
    val pair = getPair(s, pairRef.domain, pairRef.key)
    s.saveOrUpdate(new StoreCheckpoint(pair, version))
  })

  def retrieveUnmatchedEvents(domain:String, interval: Interval) = sessionFactory.withSession(s => {
    db.listQuery[ReportedDifferenceEvent]("unmatchedEventsInIntervalByDomain",
      Map("domain" -> domain, "start" -> interval.getStart, "end" -> interval.getEnd)).map(_.asDifferenceEvent)
  })

  def streamUnmatchedEvents(pairRef:DiffaPairRef, handler:(ReportedDifferenceEvent) => Unit) =
    processAsStream[ReportedDifferenceEvent]("unmatchedEventsByDomainAndPair",
      Map("domain" -> pairRef.domain, "pair" -> pairRef.key), (s, diff) => handler(diff))

  def retrievePagedEvents(pair: DiffaPairRef, interval: Interval, offset: Int, length: Int, options:EventOptions = EventOptions()) = sessionFactory.withSession(s => {
    val queryName = if (options.includeIgnored) {
      "unmatchedEventsInIntervalByDomainAndPairWithIgnored"
    } else {
      "unmatchedEventsInIntervalByDomainAndPair"
    }

    db.listQuery[ReportedDifferenceEvent](queryName,
        Map("domain" -> pair.domain, "pair" -> pair.key, "start" -> interval.getStart, "end" -> interval.getEnd),
        Some(offset), Some(length)).
      map(_.asDifferenceEvent)
  })

  def countUnmatchedEvents(pair: DiffaPairRef, start:DateTime, end:DateTime):Int =  sessionFactory.withSession(s => {
    val c = s.createCriteria(classOf[ReportedDifferenceEvent])
    c.add(Restrictions.eq("objId.pair.domain", pair.domain))
    c.add(Restrictions.eq("objId.pair.key", pair.key))
    if (start != null) c.add(Restrictions.ge("detectedAt", start))
    if (end != null) c.add(Restrictions.lt("detectedAt", end))
    c.add(Restrictions.eq("isMatch", false))
    c.add(Restrictions.eq("ignored", false))
    c.setProjection(Projections.count("seqId"))

    val count:Option[java.lang.Long] = Option(c.uniqueResult().asInstanceOf[java.lang.Long])
    count.getOrElse(new java.lang.Long(0L)).intValue
  })

  def retrieveAggregates(pair:DiffaPairRef, start:DateTime, end:DateTime, aggregateMinutes:Option[Int]):Seq[AggregateTile] =
    aggregationCache.retrieveAggregates(pair, start, end, aggregateMinutes)

  def getEvent(domain:String, evtSeqId: String) = {
    db.singleQueryMaybe[ReportedDifferenceEvent]("eventByDomainAndSeqId",
        Map("domain" -> domain, "seqId" -> java.lang.Long.parseLong(evtSeqId))) match {
      case None       =>
        throw new InvalidSequenceNumberException(evtSeqId)
      case Some(evt)  =>
        evt.asDifferenceEvent
    }
  }

  def expireMatches(cutoff:DateTime) {
    val rows = db.execute("expireMatches", Map("cutoff" -> cutoff))

    if (rows > 0) {
      val cachedEvents = reportedEvents.valueSubset("isMatch")
      // TODO Index the cache and add a date predicate rather than doing this manually
      cachedEvents.foreach(e => {
        if (e.lastSeen.isBefore(cutoff)){
          reportedEvents.evict(e.objId)
        }
      })
    }

  }

  def clearAllDifferences = sessionFactory.withSession(s => {
    reset
    s.createQuery("delete from ReportedDifferenceEvent").executeUpdate()
    s.createQuery("delete from PendingDifferenceEvent").executeUpdate()
  })

  private def intializeExistingSequences() = {

    def initializer(generateKeyName:String => String) = new ResultSetCallback {
      def onRow(rs: ResultSet) {
        val domain  = rs.getString("domain")
        val persistentValue  = rs.getLong("max_seq_id")
        val key = generateKeyName(domain)
        val currentValue = sequenceProvider.currentSequenceValue(key)
        if (persistentValue > currentValue) {
          sequenceProvider.upgradeSequenceValue(key, currentValue, persistentValue)
        }
      }
    }

    // TODO This hand cranked SQL is very naughty
    db.forEachRow("select domain, max(seq_id) as max_seq_id from diffs group by domain", initializer(eventSequenceKey))
    db.forEachRow("select domain, max(oid) as max_seq_id from pending_diffs group by domain", initializer(pendingEventSequenceKey))

  }

  private def eventSequenceKey(domain:String) = "%s.events".format(domain)
  private def pendingEventSequenceKey(domain:String) = "%s.pending.events".format(domain)

  private def getPendingEvent(id: VersionID) = {
    getEventInternal[PendingDifferenceEvent](id, pendingEvents, "pendingByDomainIdAndVersionID", PendingDifferenceEvent.nonExistent)
  }

  private def createPendingEvent(pending:PendingDifferenceEvent) {

    val domain = pending.objId.pair.domain
    val pair = pending.objId.pair.key
    val nextSeqId = nextPendingEventSequenceValue(domain)


    val query = "createNewPendingDiff"
    val params = Map(
      "oid" -> nextSeqId,
      "domain" -> domain,
      "pair" -> pair,
      "entity_id" -> pending.objId.id,
      "detected_at" -> columnMapper.toNonNullValue(pending.detectedAt),
      "last_seen" -> columnMapper.toNonNullValue(pending.lastSeen),
      "upstream_vsn" -> pending.upstreamVsn,
      "downstream_vsn"-> pending.downstreamVsn
    )

    val rows = db.execute(query,params)

    pending.oid = nextSeqId

    pendingEvents.put(pending.objId,pending)
  }

  private def removePendingEvent(tx:Option[Transaction], pending:PendingDifferenceEvent) = {

    val query = "deletePendingDiffByOid"
    val params = Map("oid" -> pending.oid)

    val rows = tx match {
      case None    => db.execute(query,params)
      case Some(x) => x.execute(DatabaseCommand(query, params))
    }

    pendingEvents.evict(pending.objId)
  }

  private def updatePendingEvent(pending:PendingDifferenceEvent, upstreamVsn:String, downstreamVsn:String, seenAt:DateTime) = {
    pending.upstreamVsn = upstreamVsn
    pending.downstreamVsn = downstreamVsn
    pending.lastSeen = seenAt

    db.execute("updatePendingDiffs",
      Map(
        "upstream_vsn"   -> upstreamVsn,
        "downstream_vsn" -> downstreamVsn,
        "last_seen"      -> columnMapper.toNonNullValue(seenAt),
        "oid"            -> pending.oid
    ))

    val cachedEvents = pendingEvents.valueSubset("oid", pending.oid.toString)
    cachedEvents.foreach(e => pendingEvents.put(e.objId, pending))

  }

  private def preenPendingEventsCache(attribute:String, value:String) = {
    val cachedEvents = pendingEvents.valueSubset(attribute, value)
    cachedEvents.foreach(e => pendingEvents.evict(e.objId))
  }

  private def prefetchPendingEvents(prefetchLimit: Int) = {
    def prefillCache(s:Session, e:PendingDifferenceEvent) {
      pendingEvents.put(e.objId, e)
    }
    processAsStream[PendingDifferenceEvent]("prefetchPendingDiffs", Map(), prefillCache, Some(prefetchLimit))
  }

  private def getEventById(id: VersionID) = {
    getEventInternal[ReportedDifferenceEvent](id, reportedEvents, "eventByDomainAndVersionID", nonExistentReportedEvent)
  }

  private def getEventInternal[T](id: VersionID,
                                  cache:CachedMap[VersionID,T],
                                  query:String,
                                  nonExistentMarker:T) = {

    def eventOrNonExistentMarker() = {
      db.singleQueryMaybe[T](query,
        Map(
          "domain" -> id.pair.domain,
          "pair"   -> id.pair.key,
          "objId"  -> id.id
        )
      ) match {
        case None    => nonExistentMarker
        case Some(e) => e
      }
    }

    cache.readThrough(id, eventOrNonExistentMarker)

  }

  private def reportedEventExists(event:ReportedDifferenceEvent) = event.seqId != NON_EXISTENT_SEQUENCE_ID

  private def addReportableMismatch(existing:Option[Transaction], reportableUnmatched:ReportedDifferenceEvent) : DifferenceEvent = {
    val event = getEventById(reportableUnmatched.objId)

    if (reportedEventExists(event)) {
      event.state match {
        case MatchState.IGNORED =>
          if (identicalEventVersions(event, reportableUnmatched)) {
            // Update the last time it was seen
            val updatedEvent = updateTimestampForPreviouslyReportedEvent(event, reportableUnmatched.lastSeen)
            updatedEvent.asDifferenceEvent
          } else {
            ignorePreviouslyReportedEvent(event)
          }
        case MatchState.UNMATCHED =>
          // We've already got an unmatched event. See if it matches all the criteria.
          if (identicalEventVersions(event, reportableUnmatched)) {
            // Update the last time it was seen
            val updatedEvent = updateTimestampForPreviouslyReportedEvent(event, reportableUnmatched.lastSeen)
            // No need to update the aggregate cache, since it won't affect the aggregate counts
            updatedEvent.asDifferenceEvent
          } else {
            reportableUnmatched.seqId = event.seqId
            upgradePreviouslyReportedEvent(reportableUnmatched)
          }

        case MatchState.MATCHED =>
          // The difference has re-occurred. Remove the match, and add a difference.
          upgradePreviouslyReportedEvent(reportableUnmatched)
      }
    }
    else {
      createReportedEvent(None, reportableUnmatched)
    }

  }

  private def identicalEventVersions(first:ReportedDifferenceEvent, second:ReportedDifferenceEvent) =
    first.upstreamVsn == second.upstreamVsn && first.downstreamVsn == second.downstreamVsn

  private def updateAndConvertEvent(evt:ReportedDifferenceEvent, previousDetectionTime:DateTime) = {
    var res = upgradePreviouslyReportedEvent(evt)
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
    db.execute("updateLastSeenForPreviouslyReportedDiff", Map(
      "seq_id"     -> event.seqId,
      "pair"       -> event.objId.pair.key,
      "domain"     -> event.objId.pair.domain,
      "last_seen"  -> columnMapper.toNonNullValue(lastSeen)
    ))

    event.lastSeen = lastSeen

    reportedEvents.put(event.objId, event)

    event
  }

  /**
   * Uprevs the sequence id for this event
   */
  private def upgradePreviouslyReportedEvent(reportableUnmatched:ReportedDifferenceEvent) = {

    val domain = reportableUnmatched.objId.pair.domain
    val pair = reportableUnmatched.objId.pair.key
    val nextSeqId = nextEventSequenceValue(domain)

    val query = "upgradePreviouslyReportedEvent"
    val params = Map(
      "seq_id"          -> reportableUnmatched.seqId,
      "new_seq_id"      -> nextSeqId,
      "domain"          -> domain,
      "pair"            -> pair,
      "entity_id"       -> reportableUnmatched.objId.id,
      "is_match"        -> reportableUnmatched.isMatch,
      "detected_at"     -> columnMapper.toNonNullValue(reportableUnmatched.detectedAt),
      "last_seen"       -> columnMapper.toNonNullValue(reportableUnmatched.lastSeen),
      "upstream_vsn"    -> reportableUnmatched.upstreamVsn,
      "downstream_vsn"  -> reportableUnmatched.downstreamVsn,
      "ignored"         -> reportableUnmatched.ignored
    )

    val rows = db.execute(query, params)

    // TODO Theorectically this should never happen ....
    if (rows == 0) {
      val alert = formatAlertCode(domain, pair, INCONSISTENT_DIFF_STORE)
      val msg = " %s No rows updated for pending event %s, next sequence id was %s".format(alert, reportableUnmatched, nextSeqId)
      log.error(msg)
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

    val domain = event.objId.pair.domain
    val nextSeqId = nextEventSequenceValue(domain)

    db.execute("updateIgnoredForPreviouslyReportedDiff", Map(
      "seq_id"     -> event.seqId,
      "new_seq_id" -> nextSeqId,
      "pair"       -> event.objId.pair.key,
      "domain"     -> domain,
      "last_seen"  -> columnMapper.toNonNullValue(event.lastSeen),
      "ignored"    -> true
    ))

    updateSequenceValueAndCache(event, nextSeqId)
  }

  private def nextEventSequenceValue(domain:String) = sequenceProvider.nextSequenceValue(eventSequenceKey(domain))
  private def nextPendingEventSequenceValue(domain:String) = sequenceProvider.nextSequenceValue(pendingEventSequenceKey(domain))

  private def createReportedEvent(tx:Option[Transaction], evt:ReportedDifferenceEvent) = {

    val domain = evt.objId.pair.domain
    val pair = evt.objId.pair.key
    val nextSeqId = nextEventSequenceValue(domain)


    val query = "createNewReportedDiff"
    val params = Map(
      "seq_id" -> nextSeqId,
      "domain" -> domain,
      "pair" -> pair,
      "entity_id" -> evt.objId.id,
      "is_match" -> evt.isMatch,
      "detected_at" -> columnMapper.toNonNullValue(evt.detectedAt),
      "last_seen" -> columnMapper.toNonNullValue(evt.lastSeen),
      "upstream_vsn" -> evt.upstreamVsn,
      "downstream_vsn"-> evt.downstreamVsn,
      "ignored" -> evt.ignored
    )

    val rows = tx match {
      case None => {
        db.execute(query, params)
      }
      case Some(tx) => {
        tx.registerRollbackHandler(new RollbackHandler {
          def onRollback() = reportedEvents.evict(evt.objId)
        })
        tx.execute(DatabaseCommand(query, params))
      }
    }

    updateAggregateCache(evt.objId.pair, evt.detectedAt)
    updateSequenceValueAndCache(evt, nextSeqId)
  }

  private def updateAggregateCache(pair:DiffaPairRef, detectedAt:DateTime) =
    aggregationCache.onStoreUpdate(pair, detectedAt)


  private def removeLatestRecordedVersion(pair:DiffaPairRef) = sessionFactory.withSession(s => {
    getStoreCheckpoint(pair) match {
      case Some(checkpoint) => s.delete(checkpoint)
      case None             => //
    }
  })

  private def inTransaction[T](f:Transaction => T) = {
    val tx = db.beginTransaction
    val result = f(tx)
    tx.commit()
    result
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

/**
 * Workaround for injecting JNDI string - basically because I couldn't find a way to due this just with the Spring XML file.
 */
class HibernateDomainDifferenceStoreFactory(val sessionFactory:SessionFactory,
                                            val db:DatabaseFacade,
                                            val cacheProvider:CacheProvider,
                                            sequenceProvider:SequenceProvider,
                                            val cacheManager:CacheManager,
                                            val dialectString:String,
                                            val hookManager:HookManager) {

  def create = {
    val dialect = Class.forName(dialectString).newInstance().asInstanceOf[Dialect]
    new HibernateDomainDifferenceStore(sessionFactory, db, cacheProvider, sequenceProvider, cacheManager, dialect, hookManager)
  }
}

case class StoreCheckpoint(
  @BeanProperty var pair:DiffaPair,
  @BeanProperty var latestVersion:java.lang.Long = null
) {
  def this() = this(pair = null)
}

/**
 * Convenience wrapper for a compound primary key
 */
case class DomainNameScopedKey(@BeanProperty var pair:String = null,
                               @BeanProperty var domain:String = null) extends java.io.Serializable
{
  def this() = this(pair = null)
}
