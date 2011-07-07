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
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.{Logger, LoggerFactory}
import net.lshift.diffa.kernel.matching.{MatchingManager, MatchingStatusListener}
import net.lshift.diffa.kernel.actors.{PairPolicyClient, PairActor}
import net.lshift.diffa.kernel.config.{Endpoint, ConfigStore}
import net.lshift.diffa.kernel.participants._
import net.lshift.diffa.kernel.events.VersionID
import org.joda.time.{Interval, DateTime}
import net.lshift.diffa.kernel.util.MissingObjectException

/**
 * Standard implementation of the SessionManager.
 *
 * Terminology:
 *  - Pending events are events that have resulted in differences, but the matching manager is still waiting for a
 *     timeout on;
 *  - Reportable events are events that have resulted in differences, and the matching manager has expired its window for it;
 *
 * Events sent to clients all have sequence identifiers, allowing clients to incrementally update. Internally, the
 * session manager will not allocate a sequence number for an event until an event goes reportable, since many events
 * are likely to be generated internally in normal flows that will never be shown externally (eg, a message sent from
 * A -> B will likely be marked as mismatched by the differencing engine for a short period of time, but be suppressed
 * whilst the matching manager waits for it to expire).
 */
class DefaultSessionManager(
        val config:ConfigStore,
        val cacheProvider:SessionCacheProvider,
        val matching:MatchingManager,
        val vpm:VersionPolicyManager,
        val pairPolicyClient:PairPolicyClient,
        val participantFactory:ParticipantFactory)
    extends SessionManager
    with DifferencingListener with MatchingStatusListener with PairSyncListener {

  private val log:Logger = LoggerFactory.getLogger(getClass)

  private val listeners = new HashMap[String, ListBuffer[DifferencingListener]]

  /**
   * This is a map of every open session (keyed on session id) keyed on the pairing it is linked to
   */
  private val sessionsByKey = new HashMap[String, SessionCache]

  private val participants = new HashMap[Endpoint, Participant]

  private val pairStates = new HashMap[String, PairScanInfo]

  // Subscribe to events from the matching manager
  matching.addListener(this)

  //
  // SessionManager Implementation
  //

  def start(scope:SessionScope, start:DateTime, end:DateTime) = {

    def boot() = {
      val sessionId = generateSessionId(scope, start, end)

      // Only create a session if don't already have one for this key
      val sessionToInit = sessionsByKey.synchronized {
        sessionsByKey.get(sessionId) match {
          case Some(s) => None
          case None => {
            val session = cacheProvider.retrieveOrAllocateCache(sessionId, scope)
            sessionsByKey(sessionId) = session
            Some(session)
          }
        }
      }

      // If a session to initialize was return, we should do that.
      sessionToInit match {
        case Some(session) => {
          session.markAsInitialized
          runDifferenceForScope(scope, start, end, this)
        }
        case None => // Do nothing
      }

      log.debug("Created session <" + sessionId + "> for the scope " + scope)
      sessionId
    }
    
    withValidScope(scope, boot)
  }



  def start(scope: SessionScope, sessionStart:DateTime, sessionEnd:DateTime, listener: DifferencingListener) : String = {
    val sessionId = start(scope, sessionStart, sessionEnd)
    listeners.synchronized {
      val keyListeners = listeners.get(sessionId) match {
        case Some(ll) => ll
        case None => {
          val newListeners = new ListBuffer[DifferencingListener]
          listeners(sessionId) = newListeners
          newListeners
        }
      }
      keyListeners += listener
    }
    runDifferenceForScope(scope, sessionStart, sessionEnd, listener)
    sessionId
  }


  def end(sessionID: String) = {
    sessionsByKey.contains(sessionID) match {
      case false => ()
      case true  => sessionsByKey.remove(sessionID)
    }
  }

  // TODO: This should be using a SessionID, and not a pair
  def end(pair: String, listener: DifferencingListener) = {
    forEachSession(VersionID(pair, "dummy"), s => {
      listeners.synchronized {
        listeners.get(s.sessionId) match {
          case None =>
          case Some(keyListeners) => {
            keyListeners -= listener
          }
        }
      }
    })
  }

  def retrievePairSyncStates(sessionID: String) = {
    // Gather the states for all pairs in the given session. Since some session
    sessionsByKey.get(sessionID) match {
      case Some(cache) => pairScanStates(cache.scope)
      case None        => Map()     // No pairs in an inactive session
    }
  }

  def retrieveAllPairScanStates = pairScanStates(SessionScope.all)

  def pairScanStates(scope:SessionScope) = {
    val pairKeys = pairKeysForScope(scope)
    pairStates.synchronized {
      pairKeys.map(pairKey => pairKey -> pairStates.getOrElse(pairKey, PairScanInfo(state = PairScanState.UNKNOWN))).toMap
    }
  }

  def runScanForAllPairings() {
    runSyncForScope(SessionScope.all, null, null, this)
  }

  def runScanForPair(pairKey: String) {
    withPair[Unit](pairKey)(() => runSyncForPair(pairKey, this))
  }

  def runSync(sessionID:String) = {
    sessionsByKey.get(sessionID) match {
      case None => // No session. Nothing to do. TODO: Throw an exception?
      case Some(cache) => {
        runSyncForScope(cache.scope, null, null, this)
      }
    }
  }

  /**
   * If the session does not exist, throw a MissingObjectException which will be handled in a higher layer
   */
  def safeGetSession(id:String) = {
    sessionsByKey.get(id) match {
      case Some(s) => s
      case None    => {
        if (log.isTraceEnabled) {
          log.trace("Request for non-existent session: %s".format(id))
        }
        throw new MissingObjectException(id)
      }
    }
  }

  def retrieveSessionVersion(id:String) = safeGetSession(id).currentVersion
  def retrieveEventsSince(id:String, evtSeqId:String) = safeGetSession(id).retrieveEventsSince(evtSeqId)
  def retrieveAllEvents(id:String) = safeGetSession(id).retrieveAllUnmatchedEvents

  def retrieveAllEventsInInterval(sessionId:String, interval:Interval) =
    sessionsByKey(sessionId).retrieveUnmatchedEvents(interval)

  def retrievePagedEvents(sessionId:String, interval:Interval, offset:Int, length:Int) =
    sessionsByKey(sessionId).retrievePagedEvents(interval, offset,length)

  def retrieveEventDetail(sessionID:String, evtSeqId:String, t: ParticipantType.ParticipantType) = {
    log.debug("Requested a detail query for session (" + sessionID + ") and seq (" + evtSeqId + ") and type (" + t + ")")
    t match {
      case ParticipantType.UPSTREAM => {
        withValidEvent(sessionID, evtSeqId,
                      {e:SessionEvent => e.upstreamVsn != null},
                      {p:net.lshift.diffa.kernel.config.Pair => p.upstream},
                      {e:Endpoint => participantFactory.createUpstreamParticipant(e)})
      }
      case ParticipantType.DOWNSTREAM => {
        withValidEvent(sessionID, evtSeqId,
                      {e:SessionEvent => e.downstreamVsn != null},
                      {p:net.lshift.diffa.kernel.config.Pair => p.downstream},
                      {e:Endpoint => participantFactory.createDownstreamParticipant(e)})
      }
    }
  }

  // TODO The fact that 3 lambdas are passed in probably indicates bad factoring
  // -> the participant factory call is probably low hanging fruit for refactoring
  def withValidEvent(sessionID:String, evtSeqId:String,
                     check:Function1[SessionEvent,Boolean],
                     resolve:(net.lshift.diffa.kernel.config.Pair) => Endpoint,
                     p:(Endpoint) => Participant): String = {
    val event = safeGetSession(sessionID).getEvent(evtSeqId)
    check(event) match {
      case true  => {
       val versionID = event.objId
       val pair = config.getPair(versionID.pairKey)
       val endpoint = resolve(pair)
       if (!participants.contains(endpoint)) {
         participants(endpoint) = p(endpoint)
       }
       val participant = participants(endpoint)
       participant.retrieveContent(versionID.id)
      }
      case false => "Expanded detail not available"
    }

  }

  //
  // Differencing Input
  //

  /**
   * This is the callback that channels mismatch events from the version policy into the session cache.
   *
   * Queries the matching manager to see if it is actively monitoring this VersionID (ie, it has unexpired events around it).
   * If yes -> just record it as a pending event. Don't tell clients anything yet.
   * If no -> this is a reportable event. Record it in the active list, and emit an event to our clients.
   */
  def onMismatch(id: VersionID, lastUpdate:DateTime, upstreamVsn: String, downstreamVsn: String, origin:MatchOrigin) = {
    log.debug("Processing mismatch for " + id + " with upstreamVsn '" + upstreamVsn + "' and downstreamVsn '" + downstreamVsn + "'")

    matching.getMatcher(id.pairKey) match {
      case Some(matcher) => {
        matcher.isVersionIDActive(id) match {
          case true  => reportPending(id, lastUpdate, upstreamVsn, downstreamVsn)
          case false => reportUnmatched(id, lastUpdate, upstreamVsn, downstreamVsn)
        }
      }
      case None    => {
        // If no matcher is configured, then report mis-matches immediately
        reportUnmatched(id, lastUpdate, upstreamVsn, downstreamVsn)
      }
    }
  }

  /**
   * This is the callback that channels match events from the version policy into the session cache.
   * If the ID is currently in our list of reportable events, generate a match event to reverse it,
   * and end the reportable unmatched event.
   * If the ID is current in our list of pending events, then just end the id from our list of events.
   * If we don't know about this id (no mismatches for this id reported), just ignore.
   */
  def onMatch(id: VersionID, vsn: String, origin:MatchOrigin) {
    log.debug("Processing match for " + id + " with vsn '" + vsn + "'")

    // Rest API
    addMatched(id, vsn)

    // Streaming API
    forEachSession(id, s => forEachSessionListener(s, l => l.onMatch(id, vsn, LiveWindow)))
  }
  
  //
  // Matching Status Input
  //

  def onDownstreamExpired(id: VersionID, vsn: String) = upgradePending(id)  
  def onUpstreamExpired(id: VersionID, vsn: String) = upgradePending(id)

  /**
   * This event is unimportant from the perspective of maintaining the session, hence just drop it 
   */
  def onPaired(id: VersionID, vsn: String) = ()


  //
  // Pair Sync Notifications
  //

  def pairSyncStateChanged(pairKey: String, syncState: PairScanState, statusMessage:String) {
    updatePairSyncState(pairKey, Some(syncState), statusMessage)
  }
  def pairSyncStatusChanged(pairKey: String, statusMessage: String) {
    updatePairSyncState(pairKey, None, statusMessage)
  }



  //
  // Configuration Change Notifications
  //



  // Internal plumbing

  /**
   * When pairs are updated, perform a differencing run to sync with their status.
   */
  def onUpdatePair(pairKey: String) = {
    val isRelevantToASession =
      sessionsByKey.values.foldLeft(false)((currentlyRelevant, s) => currentlyRelevant || s.isInScope(VersionID(pairKey, "dummy")));
    if (isRelevantToASession) {
      val (from, until) = defaultDateBounds()

      runDifferenceForScope(SessionScope.forPairs(pairKey), from, until, this)
    }
  }

  /**
   * When pairs are deleted, we stop tracking their status in the pair sync map.
   */
  def onDeletePair(pairKey:String) = {
    pairStates.synchronized { pairStates.remove(pairKey) }
  }

  def withPair[T](pair:String)(f:Function0[T]) = withValidPair(pair, f)

  def runSyncForScope(scope:SessionScope, start:DateTime, end:DateTime, listener: DifferencingListener) {
    pairKeysForScope(scope).foreach(runSyncForPair(_, listener))
  }

  def runSyncForPair(pairKey:String, listener:DifferencingListener) {
    // Update the sync state ourselves. The policy itself will send an update shortly, but since that happens
    // asynchronously, we might have returned before then, and this may potentially result in clients seeing
    // a "Up To Date" view, even though we're just about to transition out of that state.
    updatePairSyncState(pairKey, Some(PairScanState.SYNCHRONIZING), "Scan Starting")

    pairPolicyClient.scanPair(pairKey, listener, this)
  }

  def runDifferenceForScope(scope:SessionScope, start:DateTime, end:DateTime, listener: DifferencingListener) {
    pairKeysForScope(scope).foreach(pairKey => {
      pairPolicyClient.difference(pairKey, listener)
    })
  }

  def pairKeysForScope(scope:SessionScope):Seq[String] = {
    scope.includedPairs.size match {
      case 0  => config.listGroups.flatMap(g => g.pairs.map(p => p.key))
      case _  => scope.includedPairs
    }
  }

  def forEachSession(id:VersionID, f: Function1[SessionCache,Any]) = {
    sessionsByKey.values.foreach(s => {
      if (s.isInScope(id)) {
        f(s)
      }
    })
  }

  def forEachSessionListener(s:SessionCache, f: (DifferencingListener) => Any) = {
    listeners.synchronized {
      listeners.get(s.sessionId) match {
        case None =>
        case Some(keyListeners) => {
          keyListeners.foreach(l => f(l))
        }
      }
    }
  }
  
  def reportPending(id:VersionID, lastUpdate:DateTime, upstreamVsn: String, downstreamVsn: String) =
    forEachSession(id, s => s.addPendingUnmatchedEvent(id, lastUpdate, upstreamVsn, downstreamVsn))

  def reportUnmatched(id:VersionID, lastUpdate:DateTime, upstreamVsn: String, downstreamVsn: String) = {
    forEachSession(id, s => {
      s.addReportableUnmatchedEvent(id, lastUpdate, upstreamVsn, downstreamVsn)

      // Streaming API
      forEachSessionListener(s, l => l.onMismatch(id, lastUpdate, upstreamVsn, downstreamVsn, LiveWindow))
    })
  }

  def addMatched(id:VersionID, vsn:String) = forEachSession(id, s => s.addMatchedEvent(id, vsn) )
  def upgradePending(id:VersionID) = {
    forEachSession(id, s => {
      val evt = s.upgradePendingUnmatchedEvent(id)
      if (evt != null) {
        log.debug("Processing upgrade from pending to unmatched for " + id)

        val timestamp = new DateTime()
        forEachSessionListener(s, l => l.onMismatch(id, timestamp, evt.upstreamVsn, evt.downstreamVsn, LiveWindow))
      } else {
        log.debug("Skipped upgrade from pending to unmatched for " + id + " as the event was not pending")
      }
    })
  }

  /**
   * Utility function to generate session ids based on hashing the query parameters
   */
  def generateSessionId(scope: SessionScope, start: DateTime, end: DateTime) = DigestUtils.md5Hex(scope.toString + start + end)

  /**
   * Utility function to make sure that the session refers to a valid pair
   */
  def withValidPair[T](pair:String, f: () => T) = {
    config.getPair(pair)
    f()
  }

  /**
   * Utility function to make sure that a scope refers only to valid pairs.
   */
  def withValidScope[T](scope:SessionScope, f: () => T) = {
    scope.includedPairs.foreach(p => config.getPair(p))
    f()
  }

  def updatePairSyncState(pairKey:String, state:Option[PairScanState], statusMessage:String = "") = {
    pairStates.synchronized {
      var pairInfo = pairStates.getOrElseUpdate(pairKey, PairScanInfo(state = PairScanState.UNKNOWN, statusMessage = statusMessage))

      state.foreach(s => pairInfo.state = s)
      pairInfo.statusMessage = statusMessage
    }
    if (state.isDefined)
      log.info("Pair " + pairKey + " entered synchronization state: " + state.get)
    if (statusMessage.length > 0)
      log.info("Pair " + pairKey + " has status: " + statusMessage)
  }
}
