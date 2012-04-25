package net.lshift.diffa.kernel.differencing

import net.lshift.diffa.kernel.events.VersionID
import reflect.BeanProperty
import org.hibernate.SessionFactory
import net.lshift.diffa.kernel.util.SessionHelper._
import org.hibernate.Session
import net.sf.ehcache.CacheManager
import net.lshift.diffa.kernel.util.{Cursor, HibernateQueryUtils}
import scala.collection.JavaConversions._
import org.hibernate.transform.ResultTransformer
import org.joda.time.{DateTimeZone, DateTime, Interval}
import java.math.BigInteger
import java.util.List
import org.jadira.usertype.dateandtime.joda.columnmapper.TimestampColumnDateTimeMapper
import java.sql.{Types, Timestamp}
import net.lshift.diffa.kernel.config.DomainScopedKey._
import net.lshift.diffa.kernel.config.Domain._
import net.lshift.diffa.kernel.hooks.HookManager
import net.lshift.diffa.kernel.config.{DomainScopedKey, Domain, DiffaPairRef, DiffaPair}
import org.hibernate.dialect.{Oracle10gDialect, Dialect}
import net.lshift.hibernate.migrations.dialects.{MySQL5DialectExtension, OracleDialectExtension, DialectExtensionSelector}
import org.hibernate.criterion.{Projections, Restrictions}

/**
 * Hibernate backed Domain Cache provider.
 */
class HibernateDomainDifferenceStore(val sessionFactory:SessionFactory, val cacheManager:CacheManager, val dialect:Dialect, val hookManager:HookManager)
    extends DomainDifferenceStore
    with HibernateQueryUtils {

  val aggregationCache = new DifferenceAggregationCache(this, cacheManager)
  val hook = hookManager.createDifferencePartitioningHook(sessionFactory)

  val columnMapper = new TimestampColumnDateTimeMapper()

  def removeDomain(domain:String) = {
    // If difference partitioning is enabled, ask the hook to clean up each pair. Note that we'll end up running a
    // delete over all pair differences later anyway, so we won't record the result of the removal operation.
    if (hook.isDifferencePartitioningEnabled) {
      listPairsInDomain(domain).foreach(p => hook.removeAllPairDifferences(domain, p.key))
    }

    removeDomainDifferences(domain)
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
  }
  
  def currentSequenceId(domain:String) = sessionFactory.withSession(s => {
    singleQueryOpt[java.lang.Integer](s, "maxSeqIdByDomain", Map("domain" -> domain)).getOrElse(0).toString
  })

  def maxSequenceId(pair: DiffaPairRef, start:DateTime, end:DateTime) = sessionFactory.withSession(s => {
    val c = s.createCriteria(classOf[ReportedDifferenceEvent])
    c.add(Restrictions.eq("objId.pair.domain", pair.domain))
    c.add(Restrictions.eq("objId.pair.key", pair.key))
    if (start != null) c.add(Restrictions.ge("detectedAt", start))
    if (end != null) c.add(Restrictions.lt("detectedAt", end))
    c.setProjection(Projections.max("seqId"))

    val count:Option[java.lang.Integer] = Option(c.uniqueResult().asInstanceOf[java.lang.Integer])
    count.getOrElse(new java.lang.Integer(0)).intValue
  })

  def addPendingUnmatchedEvent(id: VersionID, lastUpdate: DateTime, upstreamVsn: String, downstreamVsn: String, seen: DateTime) {
    sessionFactory.withSession(s => {
      getPendingEvent(s, id) match {
        case None           =>
          val pendingUnmatched = PendingDifferenceEvent(null, id, lastUpdate, upstreamVsn, downstreamVsn, seen)
          val oid = s.save(pendingUnmatched).asInstanceOf[java.lang.Integer]
          pendingUnmatched.oid = oid
        case Some(pending)  =>
          pending.upstreamVsn = upstreamVsn
          pending.downstreamVsn = downstreamVsn
          pending.lastSeen = seen
          s.update(pending)
      }
    })
  }

  def addReportableUnmatchedEvent(id: VersionID, lastUpdate: DateTime, upstreamVsn: String, downstreamVsn: String, seen: DateTime) = sessionFactory.withSession(s => {
    addReportableMismatch(s, ReportedDifferenceEvent(null, id, lastUpdate, false, upstreamVsn, downstreamVsn, seen))
  })

  def upgradePendingUnmatchedEvent(id: VersionID) = sessionFactory.withSession(s => {
    getPendingEvent(s, id) match {
      case None           =>
        // No pending difference, nothing to do
        null
      case Some(pending)  =>
        // Remove the pending and report a mismatch
        s.delete(pending)
        addReportableMismatch(s, pending.convertToUnmatched)
    }
  })

  def cancelPendingUnmatchedEvent(id: VersionID, vsn: String) = sessionFactory.withSession(s => {
    getPendingEvent(s, id).map(pending => {
      if (pending.upstreamVsn == vsn || pending.downstreamVsn == vsn) {
        s.delete(pending)
        true
      } else {
        false
      }
    }).getOrElse(false)
  })

  def addMatchedEvent(id: VersionID, vsn: String) = {
    sessionFactory.withSession(s => {
      // Remove any pending events with the given id
      getPendingEvent(s, id).map(pending => {
        if (pending.upstreamVsn == vsn || pending.downstreamVsn == vsn) {
          s.delete(pending)
        }
      })

      // Find any existing events we've got for this ID
      getEventById(s, id) match {
          // No unmatched event. Nothing to do.
        case None =>
          null
          
        case Some(existing) =>
          existing.state match {
            case MatchState.MATCHED => // Ignore. We've already got an event for what we want.
              existing.asDifferenceEvent
            case MatchState.UNMATCHED | MatchState.IGNORED =>
              // A difference has gone away. Remove the difference, and add in a match
              s.delete(existing)
              val previousDetectionTime = existing.detectedAt
              saveAndConvertEvent(s, ReportedDifferenceEvent(null, id, new DateTime, true, vsn, vsn, new DateTime), previousDetectionTime)
          }
      }
    })
  }

  /**
   * Convert all unmatched DifferenceEvents with a lastSeen time earlier
   * than the cut-off to matched DifferenceEvents (matched: false -> true).
   */
  def matchEventsOlderThan(pair:DiffaPairRef, cutoff: DateTime) = {

    /**
     * Convert an unmatched difference event to a matched difference event
     * and set its lastSeen and detectedAt values to the current time.
     * Converting from unmatched to matched means setting its matched value to 'true'.
     */
    def convertOldEvent(s:Session, old:ReportedDifferenceEvent) {
      s.delete(old)
      val lastSeen = new DateTime
      val detectedAt = lastSeen
      val matched = true // 'convert' to matched
      saveAndConvertEvent(s, ReportedDifferenceEvent(null, old.objId, detectedAt, matched, old.upstreamVsn, old.upstreamVsn, lastSeen) )
    }

     val params = Map("domain" -> pair.domain,
                      "pair"   -> pair.key,
                      "cutoff" -> cutoff)

     processAsStream[ReportedDifferenceEvent]("unmatchedEventsOlderThanCutoffByDomainAndPair", params, convertOldEvent)
  }

  def ignoreEvent(domain:String, seqId:String) = {
    sessionFactory.withSession(s => {
      val evt = getOrFail[ReportedDifferenceEvent](s, classOf[ReportedDifferenceEvent], new java.lang.Integer(seqId), "ReportedDifferenceEvent")
      if (evt.objId.pair.domain != domain) {
        throw new IllegalArgumentException("Invalid domain %s for sequence id %s (expected %s)".format(domain, seqId, evt.objId.pair.domain))
      }

      if (evt.isMatch) {
        throw new IllegalArgumentException("Cannot ignore a match for %s (in domain %s)".format(seqId, domain))
      }
      if (!evt.ignored) {
        // Remove this event, and replace it with a new event. We do this to ensure that consumers watching the updates
        // (or even just monitoring sequence ids) see a noticeable change.
        s.delete(evt)
        saveAndConvertEvent(s, ReportedDifferenceEvent(null, evt.objId, evt.detectedAt, false,
          evt.upstreamVsn, evt.downstreamVsn, evt.lastSeen, ignored = true))
      } else {
        evt.asDifferenceEvent
      }
    })
  }

  def unignoreEvent(domain:String, seqId:String) = {
    sessionFactory.withSession(s => {
      val evt = getOrFail[ReportedDifferenceEvent](s, classOf[ReportedDifferenceEvent], new java.lang.Integer(seqId), "ReportedDifferenceEvent")
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
      s.delete(evt)
      saveAndConvertEvent(s, ReportedDifferenceEvent(null, evt.objId, evt.detectedAt,
        false, evt.upstreamVsn, evt.downstreamVsn, new DateTime))
    })
  }

  def lastRecordedVersion(pair:DiffaPairRef) = getStoreCheckpoint(pair) match {
    case None             => None
    case Some(checkpoint) => Some(checkpoint.latestVersion)
  }

  def removeLatestRecordedVersion(pair:DiffaPairRef) = sessionFactory.withSession(s => {
    getStoreCheckpoint(pair) match {
      case Some(checkpoint) => s.delete(checkpoint)
      case None             => //
    }
  })

  def recordLatestVersion(pairRef:DiffaPairRef, version:Long) = sessionFactory.withSession(s => {
    val pair = getPair(s, pairRef.domain, pairRef.key)
    s.saveOrUpdate(new StoreCheckpoint(pair, version))
  })

  def retrieveUnmatchedEvents(domain:String, interval: Interval) = sessionFactory.withSession(s => {
    listQuery[ReportedDifferenceEvent](s, "unmatchedEventsInIntervalByDomain",
      Map("domain" -> domain, "start" -> interval.getStart, "end" -> interval.getEnd)).map(_.asDifferenceEvent)
  })

  def streamUnmatchedEvents(pairRef:DiffaPairRef, handler:(ReportedDifferenceEvent) => Unit) =
    processAsStream[ReportedDifferenceEvent]("unmatchedEventsByDomainAndPair",
      Map("domain" -> pairRef.domain, "pair" -> pairRef.key), (s, diff) => handler(diff))

  // TODO consider removing this in favor of aggregateUnmatchedEvents/3
  @Deprecated
  def retrieveUnmatchedEvents(pair:DiffaPairRef, interval:Interval, f:ReportedDifferenceEvent => Unit) = {

    def processEvent(s:Session, e:ReportedDifferenceEvent) = f(e)

    val params = Map("domain" -> pair.domain,
                      "pair"  -> pair.key,
                      "start" -> interval.getStart,
                      "end"   -> interval.getEnd)

    processAsStream[ReportedDifferenceEvent]("unmatchedEventsInIntervalByDomainAndPair", params, processEvent)
  }

  def retrievePagedEvents(pair: DiffaPairRef, interval: Interval, offset: Int, length: Int, options:EventOptions = EventOptions()) = sessionFactory.withSession(s => {
    val queryName = if (options.includeIgnored) {
      "unmatchedEventsInIntervalByDomainAndPairWithIgnored"
    } else {
      "unmatchedEventsInIntervalByDomainAndPair"
    }

    listQuery[ReportedDifferenceEvent](s, queryName,
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

  def retrieveEventsSince(domain: String, evtSeqId: String) = sessionFactory.withSession(s => {
    listQuery[ReportedDifferenceEvent](s, "eventsSinceByDomain",
      Map("domain" -> domain, "seqId" -> Integer.parseInt(evtSeqId))).map(_.asDifferenceEvent)
  })

  def retrieveEventTiles(pair:DiffaPairRef, zoomLevel:Int, timestamp:DateTime) = {
    val alignedTimespan = ZoomLevels.containingTileGroupInterval(timestamp, zoomLevel)
    val aggregateMinutes = ZoomLevels.lookupZoomLevel(zoomLevel)
    val aggregates =
      aggregationCache.retrieveAggregates(pair, alignedTimespan.getStart, alignedTimespan.getEnd, Some(aggregateMinutes))

    val interestingAggregates = aggregates.filter(t => t.count > 0)
    Some(TileGroup(alignedTimespan.getStart, interestingAggregates.map(t => t.start -> t.count).toMap))
  }

  def retrieveAggregates(pair:DiffaPairRef, start:DateTime, end:DateTime, aggregateMinutes:Option[Int]):Seq[AggregateTile] =
    aggregationCache.retrieveAggregates(pair, start, end, aggregateMinutes)

  def getEvent(domain:String, evtSeqId: String) = sessionFactory.withSession(s => {
    singleQueryOpt[ReportedDifferenceEvent](s, "eventByDomainAndSeqId",
        Map("domain" -> domain, "seqId" -> Integer.parseInt(evtSeqId))) match {
      case None       => throw new InvalidSequenceNumberException(evtSeqId)
      case Some(evt)  => evt.asDifferenceEvent
    }
  })

  def expireMatches(cutoff:DateTime) {
    sessionFactory.withSession(s => {
      executeUpdate(s, "expireMatches", Map("cutoff" -> cutoff))
    })
  }

  def clearAllDifferences = sessionFactory.withSession(s => {
    aggregationCache.clear
    s.createQuery("delete from ReportedDifferenceEvent").executeUpdate()
    s.createQuery("delete from PendingDifferenceEvent").executeUpdate()
  })

  private def getPendingEvent(s:Session, id: VersionID) =
    singleQueryOpt[PendingDifferenceEvent](s, "pendingByDomainIdAndVersionID",
        Map("domain" -> id.pair.domain, "pair" -> id.pair.key, "objId" -> id.id))
  private def getEventById(s:Session, id: VersionID) =
    singleQueryOpt[ReportedDifferenceEvent](s, "eventByDomainAndVersionID",
        Map("domain" -> id.pair.domain, "pair" -> id.pair.key, "objId" -> id.id))
  private def addReportableMismatch(s:Session, reportableUnmatched:ReportedDifferenceEvent) = {
    getEventById(s, reportableUnmatched.objId) match  {
      case Some(existing) =>
        existing.state match {
          case MatchState.IGNORED =>
            if (identicalEventVersions(existing, reportableUnmatched)) {
              // Update the last time it was seen
              existing.lastSeen = reportableUnmatched.lastSeen
              s.update(existing)
              existing.asDifferenceEvent
            } else {
              s.delete(existing)
              reportableUnmatched.ignored = true
              saveAndConvertEvent(s, reportableUnmatched)
            }
          case MatchState.UNMATCHED =>
            // We've already got an unmatched event. See if it matches all the criteria.
            if (identicalEventVersions(existing, reportableUnmatched)) {
              // Update the last time it was seen
              existing.lastSeen = reportableUnmatched.lastSeen
              s.update(existing)
                // No need to update the aggregate cache, since it won't affect the aggregate counts
              existing.asDifferenceEvent
            } else {
              s.delete(existing)
              saveAndConvertEvent(s, reportableUnmatched)
            }

          case MatchState.MATCHED =>
              // The difference has re-occurred. Remove the match, and add a difference.
            s.delete(existing)
            saveAndConvertEvent(s, reportableUnmatched)
        }

      case None =>
        saveAndConvertEvent(s, reportableUnmatched)
    }
  }

  private def identicalEventVersions(first:ReportedDifferenceEvent, second:ReportedDifferenceEvent) =
    first.upstreamVsn == second.upstreamVsn && first.downstreamVsn == second.downstreamVsn


  private def saveAndConvertEvent(s:Session, evt:ReportedDifferenceEvent) = {
    val res = persistAndConvertEventInternal(s, evt)
    updateAggregateCache(evt.objId.pair, evt.detectedAt)
    res
  }

  private def saveAndConvertEvent(s:Session, evt:ReportedDifferenceEvent, previousDetectionTime:DateTime) = {
    val res = persistAndConvertEventInternal(s, evt)
    updateAggregateCache(evt.objId.pair, previousDetectionTime)
    res
  }

  private def persistAndConvertEventInternal(s:Session, evt:ReportedDifferenceEvent) = {
    val seqId = s.save(evt).asInstanceOf[java.lang.Integer]
    evt.seqId = seqId
    evt.asDifferenceEvent
  }

  private def updateAggregateCache(pair:DiffaPairRef, detectedAt:DateTime) =
    aggregationCache.onStoreUpdate(pair, detectedAt)
}

case class PendingDifferenceEvent(
  @BeanProperty var oid:java.lang.Integer = null,
  @BeanProperty var objId:VersionID = null,
  @BeanProperty var detectedAt:DateTime = null,
  @BeanProperty var upstreamVsn:String = null,
  @BeanProperty var downstreamVsn:String = null,
  @BeanProperty var lastSeen:DateTime = null
) {

  def this() = this(oid = null)

  def convertToUnmatched = ReportedDifferenceEvent(null, objId, detectedAt, false, upstreamVsn, downstreamVsn, lastSeen)
}

/**
 * This is an internal type that represents the structure of the SQL result set that aggregates mismatch events.
 */
case class AggregateEventsRow(
  @BeanProperty var year:java.lang.Integer = null,
  @BeanProperty var month:java.lang.Integer = null,
  @BeanProperty var day:java.lang.Integer = null,
  @BeanProperty var hour:java.lang.Integer = null,
  @BeanProperty var minute:java.lang.Integer = null,
  @BeanProperty var aggregate:java.lang.Integer = null
) {
  def this() = this(year = null)
}

/**
 * Workaround for injecting JNDI string - basically because I couldn't find a way to due this just with the Spring XML file.
 */
class HibernateDomainDifferenceStoreFactory(val sessionFactory:SessionFactory, val cacheManager:CacheManager, val dialectString:String, val hookManager:HookManager) {

  def create = {
    val dialect = Class.forName(dialectString).newInstance().asInstanceOf[Dialect]
    new HibernateDomainDifferenceStore(sessionFactory, cacheManager, dialect, hookManager)
  }
}

case class StoreCheckpoint(
  @BeanProperty var pair:DiffaPair,
  @BeanProperty var latestVersion:java.lang.Long = null
) {
  def this() = this(pair = null)
  //def this(pairRef:DiffaPairRef, latestVersion:java.lang.Long) = this(pairRef.domain, pairRef.key, latestVersion)
}

/**
 * Convenience wrapper for a compound primary key
 */
case class DomainNameScopedKey(@BeanProperty var pair:String = null,
                               @BeanProperty var domain:String = null) extends java.io.Serializable
{
  def this() = this(pair = null)
}
