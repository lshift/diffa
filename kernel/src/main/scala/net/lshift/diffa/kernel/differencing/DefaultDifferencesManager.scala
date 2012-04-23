/**
 * Copyright (C) 2010-2011 LShift Ltd.
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

import collection.mutable.{ListBuffer, HashMap}
import org.slf4j.{Logger, LoggerFactory}
import net.lshift.diffa.kernel.matching.{MatchingManager, MatchingStatusListener}
import net.lshift.diffa.kernel.actors.PairPolicyClient
import net.lshift.diffa.kernel.participants._
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.lifecycle.{NotificationCentre, AgentLifecycleAware}
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.config.{DiffaPairRef, Endpoint, DomainConfigStore, DiffaPair}
import org.joda.time.{DateTime, Interval}

/**
 * Standard implementation of the DifferencesManager.
 *
 * Terminology:
 *  - Pending events are events that have resulted in differences, but the matching manager is still waiting for a
 *     timeout on;
 *  - Reportable events are events that have resulted in differences, and the matching manager has expired its window for it;
 *
 * Events sent to clients all have sequence identifiers, allowing clients to incrementally update. Internally, the
 * differences manager will not allocate a sequence number for an event until an event goes reportable, since many events
 * are likely to be generated internally in normal flows that will never be shown externally (eg, a message sent from
 * A -> B will likely be marked as mismatched by the differencing engine for a short period of time, but be suppressed
 * whilst the matching manager waits for it to expire).
 */
class DefaultDifferencesManager(
        val systemConfig:SystemConfigStore,
        val domainConfig:DomainConfigStore,
        val domainDifferenceStore:DomainDifferenceStore,
        val matching:MatchingManager,
        val participantFactory:ParticipantFactory,
        val differenceListener:DifferencingListener)
    extends DifferencesManager
    with DifferencingListener with MatchingStatusListener with AgentLifecycleAware {

  private val log:Logger = LoggerFactory.getLogger(getClass)

  private val participants = new HashMap[Endpoint, Participant]

  // Subscribe to events from the matching manager
  matching.addListener(this)

  //
  // DifferencesManager Implementation
  //

  def createDifferenceWriter(domain:String, pair:String, overwrite: Boolean) = new DifferenceWriter {
    // Record when we started the write so all differences can be tagged
    val writerStart = new DateTime
    val pairRef = DiffaPairRef(pair,domain)
    var latestStoreVersion:Long = domainDifferenceStore.lastRecordedVersion(pairRef) match {
      case Some(version) => version
      case None          => 0L
    }

    def writeMismatch(id: VersionID, lastUpdate: DateTime, upstreamVsn: String, downstreamVsn: String, origin: MatchOrigin, storeVersion:Long) {
      onMismatch(id, lastUpdate, upstreamVsn, downstreamVsn, origin, Unfiltered)
      if (storeVersion > latestStoreVersion) {
        latestStoreVersion = storeVersion
      }
    }

    def evictTombstones(tombstones:Iterable[Correlation]) = domainDifferenceStore.removeEvents(tombstones.map(_.asVersionID))

    def abort() {
      // Nothing to do
    }

    def close() {
      domainDifferenceStore.recordLatestVersion(pairRef, latestStoreVersion)
    }
  }

  def retrieveDomainSequenceNum(id:String) = domainDifferenceStore.currentSequenceId(id)

  def retrieveEventTiles(domain:String, zoomLevel:Int, timespan:Interval) = {
    domainConfig.listPairs(domain).map(p => {
      p.key -> retrieveEventTiles(DiffaPairRef(p.key,domain), zoomLevel, timespan)
    }).toMap
  }

  def retrieveEventTiles(pair:DiffaPairRef, zoomLevel:Int, timespan:Interval) : Array[Int] = {
    val groupStartTimes = ZoomCache.containingTileGroupEdges(timespan, zoomLevel)
    // There must be a better way to do this map filter ......
    val tileGroups = groupStartTimes.map(t => domainDifferenceStore.retrieveEventTiles(pair, zoomLevel, t)).filter(_.isDefined).map(_.get)
    val unfiltered = tileGroups.flatMap(g => g.tiles).map{case (k,v) => k -> v }
    val alignedInterval = ZoomCache.alignInterval(timespan, zoomLevel)
    // Note the half open semantics of the contains method
    val filtered = unfiltered.filter{case (d, i) => alignedInterval.contains(d) || alignedInterval.getEnd == d}.toMap
    val tileEdges = ZoomCache.individualTileEdges(timespan, zoomLevel)
    tileEdges.map(s => filtered.getOrElse(s, 0)).toArray
  }

  def ignoreDifference(domain:String, seqId:String) = {
    domainDifferenceStore.ignoreEvent(domain, seqId)
  }

  def unignoreDifference(domain:String, seqId:String) = {
    domainDifferenceStore.unignoreEvent(domain, seqId)
  }

  def lastRecordedVersion(pair:DiffaPairRef) = domainDifferenceStore.lastRecordedVersion(pair)

  def retrieveAllEventsInInterval(domain:String, interval:Interval) =
    domainDifferenceStore.retrieveUnmatchedEvents(domain, interval)

  def retrievePagedEvents(domain:String, pairKey:String, interval:Interval, offset:Int, length:Int, options:EventOptions) =
    domainDifferenceStore.retrievePagedEvents(DiffaPairRef(key = pairKey, domain = domain), interval, offset, length, options)

  def countEvents(domain: String, pairKey: String, interval: Interval) =
    domainDifferenceStore.countUnmatchedEvents(DiffaPairRef(key = pairKey, domain = domain), interval)

  def retrieveEventDetail(domain:String, evtSeqId:String, t: ParticipantType.ParticipantType) = {
    log.trace("Requested a detail query for domain (" + domain + ") and seq (" + evtSeqId + ") and type (" + t + ")")
    t match {
      case ParticipantType.UPSTREAM => {
        withValidEvent(domain, evtSeqId,
                      {e:DifferenceEvent => e.upstreamVsn != null},
                      {p:DiffaPair => p.upstream},
                      {e:Endpoint => participantFactory.createUpstreamParticipant(e)})
      }
      case ParticipantType.DOWNSTREAM => {
        withValidEvent(domain, evtSeqId,
                      {e:DifferenceEvent => e.downstreamVsn != null},
                      {p:DiffaPair => p.downstream},
                      {e:Endpoint => participantFactory.createDownstreamParticipant(e)})
      }
    }
  }

  // TODO The fact that 3 lambdas are passed in probably indicates bad factoring
  // -> the participant factory call is probably low hanging fruit for refactoring
  private def withValidEvent(domain:String, evtSeqId:String,
                     check:Function1[DifferenceEvent,Boolean],
                     resolve:(DiffaPair) => String,
                     p:(Endpoint) => Participant): String = {
    val event = domainDifferenceStore.getEvent(domain, evtSeqId)

    check(event) match {
      case false => "Expanded detail not available"
      case true  => {
       val id = event.objId
       val pair = systemConfig.getPair(id.pair.domain, id.pair.key)
       val endpointName = resolve(pair)
       val endpoint = domainConfig.getEndpoint(domain, endpointName)
       if (endpoint.contentRetrievalUrl != null) {
         if (!participants.contains(endpoint)) {
           participants(endpoint) = p(endpoint)
         }
         val participant = participants(endpoint)
         participant.retrieveContent(id.id)
       } else {
         "Content retrieval not supported"
       }
      }
    }

  }

  //
  // Lifecycle Management
  //

  override def onAgentInstantiationCompleted(nc: NotificationCentre) {
    nc.registerForDifferenceEvents(this, Unfiltered)
  }

  //
  // Differencing Input
  //

  /**
   * This is the callback that channels mismatch events from the version policy into the domain cache.
   *
   * Queries the matching manager to see if it is actively monitoring this VersionID (ie, it has unexpired events around it).
   * If yes -> just record it as a pending event. Don't tell clients anything yet.
   * If no -> this is a reportable event. Record it in the active list, and emit an event to our clients.
   */
  def onMismatch(id: VersionID, lastUpdate:DateTime, upstreamVsn: String, downstreamVsn: String, origin:MatchOrigin, level:DifferenceFilterLevel) = {
    log.trace("Processing mismatch for " + id + " with upstreamVsn '" + upstreamVsn + "' and downstreamVsn '" + downstreamVsn + "'")
    matching.getMatcher(id.pair) match {
      case Some(matcher) => {
        matcher.isVersionIDActive(id) match {
          case true  => reportPending(id, lastUpdate, upstreamVsn, downstreamVsn, origin)
          case false => reportUnmatched(id, lastUpdate, upstreamVsn, downstreamVsn, origin)
        }
      }
      case None    => {
        // If no matcher is configured, then report mis-matches immediately
        reportUnmatched(id, lastUpdate, upstreamVsn, downstreamVsn, origin)
      }
    }
  }

  /**
   * This is the callback that channels match events from the version policy into the domain cache.
   * If the ID is currently in our list of reportable events, generate a match event to reverse it,
   * and end the reportable unmatched event.
   * If the ID is current in our list of pending events, then just end the id from our list of events.
   * If we don't know about this id (no mismatches for this id reported), just ignore.
   */
  def onMatch(id: VersionID, vsn: String, origin:MatchOrigin) {
    if (log.isTraceEnabled) {
      log.trace("Processing match for " + id + " with version '" + vsn + "'")
    }
    addMatched(id, vsn)
  }
  
  //
  // Matching Status Input
  //

  def onDownstreamExpired(id: VersionID, vsn: String) = upgradePending(id)  
  def onUpstreamExpired(id: VersionID, vsn: String) = upgradePending(id)

  /**
   * This event is unimportant from the perspective of maintaining the domain, hence just drop it
   */
  def onPaired(id: VersionID, vsn: String) = cancelPending(id, vsn)


  //
  // Configuration Change Notifications
  //



  // Internal plumbing

  /**
   * When pairs are updated, perform a differencing run to scan with their status.
   */
  def onUpdatePair(pairRef: DiffaPairRef) {
  }

  def onDeletePair(pair: DiffaPairRef) {
    domainDifferenceStore.removePair(pair)
  }


  def onUpdateDomain(domain: String) {
  }

  def onDeleteDomain(domain: String) {
    domainDifferenceStore.removeDomain(domain)
  }


  //
  // Visible Difference Reporting
  //

  def reportPending(id:VersionID, lastUpdate:DateTime, upstreamVsn: String, downstreamVsn: String, origin: MatchOrigin) {
    // TODO: Record origin as well
    domainDifferenceStore.addPendingUnmatchedEvent(id, lastUpdate, upstreamVsn, downstreamVsn, new DateTime)

    // TODO: Generate external event for pending difference?
  }


  def reportUnmatched(id:VersionID, lastUpdate:DateTime, upstreamVsn: String, downstreamVsn: String, origin: MatchOrigin) {
    domainDifferenceStore.addReportableUnmatchedEvent(id, lastUpdate, upstreamVsn, downstreamVsn, new DateTime)

    differenceListener.onMismatch(id, lastUpdate, upstreamVsn, downstreamVsn, origin, MatcherFiltered)
  }

  def addMatched(id:VersionID, vsn:String) {
    domainDifferenceStore.addMatchedEvent(id, vsn)

    // TODO: Generate external event for matched? (Interested parties will already have seen the raw event)
  }
  def upgradePending(id:VersionID) {
    val evt = domainDifferenceStore.upgradePendingUnmatchedEvent(id)
    if (evt != null) {
      log.trace("Processing upgrade from pending to unmatched for " + id)
      differenceListener.onMismatch(id, evt.detectedAt, evt.upstreamVsn, evt.downstreamVsn, LiveWindow, MatcherFiltered)
    } else {
      log.trace("Skipped upgrade from pending to unmatched for " + id + " as the event was not pending")
    }
  }
  def cancelPending(id:VersionID, vsn:String) {
    val wasDeleted = domainDifferenceStore.cancelPendingUnmatchedEvent(id, vsn)
    if (wasDeleted) {
      log.trace("Cancelling pending event for " + id)
    }
  }
}
