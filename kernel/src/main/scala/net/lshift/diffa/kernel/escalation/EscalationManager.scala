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

package net.lshift.diffa.kernel.escalation

import net.lshift.diffa.kernel.events.VersionID
import org.joda.time.DateTime
import net.lshift.diffa.kernel.actors.ActorUtils
import net.lshift.diffa.kernel.config.EscalationEvent._
import net.lshift.diffa.kernel.config.EscalationActionType._
import net.lshift.diffa.kernel.client.{ActionableRequest, ActionsClient}
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.lifecycle.{NotificationCentre, AgentLifecycleAware}
import net.lshift.diffa.kernel.differencing._
import net.lshift.diffa.kernel.config.{DiffaPairRef, DomainConfigStore}
import net.lshift.diffa.kernel.reporting.ReportManager
import akka.actor.Actor
import net.lshift.diffa.kernel.util.AlertCodes._
import java.io.Closeable

/**
 * This deals with escalating mismatches based on configurable escalation policies.
 *
 *
 * TODO Revise this description when the full blown escalation manager lands.
 * ATM the scope of this manager is just to invoke actions that are triggered by mismatches
 * that still exist after a scan.
 *
 * THis means that for now, escalations don't have to be persistent, because the only thing that can
 * can trigger an escalation is a scan and it is assumed that a sane deployment will not have too
 * many scans configured.
 *
 * In future versions, this procedure will make escalations persistent and the process of escalation
 * will not be driven by difference events, rather there will be a poll loop to drive the procedure
 * through configurable steps.
 */
class EscalationManager(val config:DomainConfigStore,
                        val actionsClient:ActionsClient,
                        val reportManager:ReportManager)
    extends DifferencingListener
    with AgentLifecycleAware
    with PairScanListener
    with Closeable {

  val log = LoggerFactory.getLogger(getClass)

  private class EscalationActor(pair: DiffaPairRef) extends Actor {
    self.id = EscalationActor.key(pair)
    
    def receive = {
      case (UpstreamMissing, id: VersionID)     => escalateEntityEvent(id, UPSTREAM_MISSING)
      case (DownstreamMissing, id: VersionID)   => escalateEntityEvent(id, DOWNSTREAM_MISSING)
      case (ConflictingVersions, id: VersionID) => escalateEntityEvent(id, MISMATCH)
      case other =>
        log.warn("{} EscalationActor received unexpected message: {}",
          formatAlertCode(pair, SPURIOUS_ACTOR_MESSAGE), other)
    }
  }

  private object EscalationActor {
    def key(pair: DiffaPairRef) = "escalations:" + pair.identifier
  }

  /**
   * Since escalations are currently only driven off mismatches, matches can be safely ignored.
   */
  override def onAgentInstantiationCompleted(nc: NotificationCentre) {
    nc.registerForDifferenceEvents(this, MatcherFiltered)
    nc.registerForPairScanEvents(this)
  }

  def onMatch(id: VersionID, vsn: String, origin: MatchOrigin) = ()

  /**
   * Asynchronously escalate matches that occur as part of a scan.
   */
  def onMismatch(id: VersionID, lastUpdated: DateTime, upstreamVsn: String, downstreamVsn: String,
                 origin: MatchOrigin, level:DifferenceFilterLevel) = origin match {
    case TriggeredByScan =>
      val differenceType = DifferenceUtils.differenceType(upstreamVsn, downstreamVsn)
      findOrCreateActor(id.pair) ! (differenceType, id)

    case _               => // ignore this for now
  }

  def pairScanStateChanged(pair: DiffaPairRef, scanState: PairScanState) {
    scanState match {
      case PairScanState.FAILED     => escalatePairEvent(pair, SCAN_FAILED)
      case PairScanState.UP_TO_DATE => escalatePairEvent(pair, SCAN_COMPLETED)
      case _                        => // Not interesting
    }
  }

  def escalateEntityEvent(id: VersionID, eventType:String) = {
    findEscalations(id.pair, eventType, REPAIR).foreach(e => {
      val result = actionsClient.invoke(ActionableRequest(id.pair.key, id.pair.domain, e.action, id.id))
      log.debug("Escalation result for action [%s] using %s is %s".format(e.name, id, result))
    })
  }

  def escalatePairEvent(pair: DiffaPairRef, eventType:String) = {
    findEscalations(pair, eventType, REPORT).foreach(e => {
      log.debug("Escalating pair event as report %s".format(e.name))
      reportManager.executeReport(pair, e.action)
    })
  }

  def findEscalations(pair: DiffaPairRef, eventType:String, actionTypes:String*) =
    config.listEscalationsForPair(pair.domain, pair.key).
      filter(e => e.event == eventType && actionTypes.contains(e.actionType))

  def close() {
    Actor.registry.actorsFor[EscalationActor].foreach(_.stop())
  }

  private def findOrCreateActor(pair: DiffaPairRef) = {
    val key = ActorUtils.ActorKey(pair, EscalationActor.key(_))

    ActorUtils.findActor(key).getOrElse {
      val actor = Actor.actorOf(new EscalationActor(pair))
      actor.start()
      log.info("{} Escalations actor started", formatAlertCode(pair, ACTOR_STARTED))
      actor
    }
  }
}