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
import net.lshift.diffa.kernel.config.{PairRef, DiffaPairRef, Endpoint, DomainConfigStore}
import org.joda.time.{DateTime, Interval}
import net.lshift.diffa.kernel.frontend.DomainPairDef
import net.lshift.diffa.kernel.escalation.EscalationHandler

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
        val differenceListener:DifferencingListener,
        val escalationHandler:EscalationHandler)
    extends DifferencesManager
    with DifferencingListener with MatchingStatusListener with AgentLifecycleAware {

  private val log:Logger = LoggerFactory.getLogger(getClass)

  private val participants = new HashMap[Endpoint, Participant]

  // Subscribe to events from the matching manager
  matching.addListener(this)

  //
  // DifferencesManager Implementation
  //

  def createDifferenceWriter(space:Long, pair:String, overwrite: Boolean) = new DifferenceWriter {
    // Record when we started the write so all differences can be tagged
    val writerStart = new DateTime
    val pairRef = PairRef(pair,space)
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

    def evictTombstones(tombstones:Iterable[Correlation]) {
      tombstones.foreach(t => onMatch(t.asVersionID, t.upstreamVsn, TriggeredByScan))
    }

    def abort() {
      // Nothing to do
    }

    def close() {
      domainDifferenceStore.recordLatestVersion(pairRef, latestStoreVersion)
    }
  }

  def retrieveDomainSequenceNum(space:Long) = domainDifferenceStore.currentSequenceId(space)

  def retrieveAggregates(pair:PairRef, start:DateTime, end:DateTime, aggregation:Option[Int]) =
    domainDifferenceStore.retrieveAggregates(pair, start, end, aggregation)

  def ignoreDifference(space:Long, seqId:String) = {
    domainDifferenceStore.ignoreEvent(space, seqId)
  }

  def unignoreDifference(space:Long, seqId:String) = {
    domainDifferenceStore.unignoreEvent(space, seqId)
  }

  def lastRecordedVersion(pair:PairRef) = domainDifferenceStore.lastRecordedVersion(pair)

  def retrieveAllEventsInInterval(space:Long, interval:Interval) =
    domainDifferenceStore.retrieveUnmatchedEvents(space, interval)

  def retrievePagedEvents(space:Long, pairKey:String, interval:Interval, offset:Int, length:Int, options:EventOptions) =
    domainDifferenceStore.retrievePagedEvents(PairRef(name = pairKey, space = space), interval, offset, length, options)

  def countEvents(space:Long, pairKey: String, interval: Interval) =
    domainDifferenceStore.countUnmatchedEvents(PairRef(name = pairKey, space = space), interval.getStart, interval.getEnd)

  def retrieveEventDetail(space:Long, evtSeqId:String, t: ParticipantType.ParticipantType) = {
    log.trace("Requested a detail query for domain (" + space + ") and seq (" + evtSeqId + ") and type (" + t + ")")
    // TODO This really needs refactoring :-(
    t match {
      case ParticipantType.UPSTREAM => {
        withValidEvent(space, evtSeqId,
                      {e:DifferenceEvent => e.upstreamVsn != null},
                      {p:DomainPairDef => p.upstreamName},
                      participantFactory.createUpstreamParticipant)
      }
      case ParticipantType.DOWNSTREAM => {
        withValidEvent(space, evtSeqId,
                      {e:DifferenceEvent => e.downstreamVsn != null},
                      {p:DomainPairDef => p.downstreamName},
                      participantFactory.createDownstreamParticipant)
      }
    }
  }

  // TODO The fact that 3 lambdas are passed in probably indicates bad factoring
  // -> the participant factory call is probably low hanging fruit for refactoring
  private def withValidEvent(space:Long, evtSeqId:String,
                     check:Function1[DifferenceEvent,Boolean],
                     resolve:(DomainPairDef) => String,
                     p:(Endpoint, PairRef) => Participant): String = {
    val event = domainDifferenceStore.getEvent(space, evtSeqId)

    check(event) match {
      case false => "Expanded detail not available"
      case true  => {
       val id = event.objId
       val pair = domainConfig.getPairDef(id.pair)
       val endpointName = resolve(pair)
       val endpoint = domainConfig.getEndpoint(space, endpointName)
       if (endpoint.contentRetrievalUrl != null) {
         if (!participants.contains(endpoint)) {
           participants(endpoint) = p(endpoint, pair.asRef)
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
  def onUpdatePair(pairRef: PairRef) {
  }

  def onDeletePair(pair: PairRef) {
    domainDifferenceStore.removePair(pair)
  }


  def onUpdateDomain(space:Long) {
  }

  def onDeleteDomain(space:Long) {
    domainDifferenceStore.removeDomain(space)
  }


  //
  // Visible Difference Reporting
  //

  def reportPending(id:VersionID, lastUpdate:DateTime, upstreamVsn: String, downstreamVsn: String, origin: MatchOrigin) {
    log.trace("Report pending for %s at %s, upstream %s, downstream %s, origin %s".format(id,lastUpdate, upstreamVsn, downstreamVsn, origin))
    // TODO: Record origin as well
    domainDifferenceStore.addPendingUnmatchedEvent(id, lastUpdate, upstreamVsn, downstreamVsn, new DateTime)

    // TODO: Generate external event for pending difference?
  }


  def reportUnmatched(id:VersionID, lastUpdate:DateTime, upstreamVsn: String, downstreamVsn: String, origin: MatchOrigin) {
    log.trace("Report unmatched for %s at %s, upstream %s, downstream %s, origin %s".format(id,lastUpdate, upstreamVsn, downstreamVsn, origin))
    val (status, event) = domainDifferenceStore.addReportableUnmatchedEvent(id, lastUpdate, upstreamVsn, downstreamVsn, new DateTime)
    differenceListener.onMismatch(id, lastUpdate, upstreamVsn, downstreamVsn, origin, MatcherFiltered)

    status match {
      case NewUnmatchedEvent | ReturnedUnmatchedEvent => escalationHandler.initiateEscalation(event)
      case _  => // The event is either unchanged or just updated. Don't start escalation.
    }
  }

  def addMatched(id:VersionID, vsn:String) {
    log.trace("Add matched, id = %s, vsn = %s".format(id,vsn))
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
