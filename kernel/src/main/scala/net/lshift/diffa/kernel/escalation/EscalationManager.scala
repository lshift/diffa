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
import net.lshift.diffa.kernel.config.EscalationEvent._
import net.lshift.diffa.kernel.config.EscalationActionType._
import net.lshift.diffa.kernel.client.{ActionableRequest, ActionsClient}
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.lifecycle.{NotificationCentre, AgentLifecycleAware}
import net.lshift.diffa.kernel.differencing._
import net.lshift.diffa.kernel.reporting.ReportManager
import net.lshift.diffa.kernel.util.AlertCodes._
import java.io.Closeable
import net.lshift.diffa.kernel.actors.AbstractActorSupervisor
import akka.actor.{ActorSystem, Props, Actor}
import scala.collection.JavaConversions._
import java.util.{Timer, TimerTask}
import net.lshift.diffa.kernel.frontend.EscalationDef
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import org.josql.filters.DefaultObjectFilter
import org.josql.QueryParseException
import net.lshift.diffa.kernel.config.{BreakerHelper, ConfigValidationException, DiffaPairRef, DomainConfigStore}

/**
 * This deals with escalating mismatches based on configurable escalation policies.
 *
 * There are two broad types of event that can be escalated. Pair level events, and entity level
 * event. Pair events are escalated in an event-driven manner - escalation is triggered as they
 * appear. Entity level escalation is managed in a persistent manner. When a candidate difference
 * is detected elsewhere in the system (currently the DifferenceManager), initiateEscalation should
 * be called with the event. Valid escalations will be identified and ordered (based upon their delay),
 * and then the first escalation will be scheduled for the difference. Periodically, the manager will
 * identify differences that have a pending escalation, and trigger this. Once an escalation has been
 * triggered, the difference will be progressed, and have the next escalation scheduled (or none if no
 * more valid escalations are configured).
 */
class EscalationManager(val config:DomainConfigStore,
                        val systemConfig:SystemConfigStore,
                        val diffs:DomainDifferenceStore,
                        val actionsClient:ActionsClient,
                        val reportManager:ReportManager,
                        val actorSystem: ActorSystem,
                        val breakerHelper: BreakerHelper)
    extends AbstractActorSupervisor
    with AgentLifecycleAware
    with PairScanListener
    with Closeable
    with EscalationHandler {

  val log = LoggerFactory.getLogger(getClass)

  private class EscalationActor(pair: DiffaPairRef) extends Actor {
    
    def receive = {
      case Escalate(d:DifferenceEvent)            =>
        if (breakerHelper.isEscalationEnabled(pair, d.nextEscalation)) {
          findEscalation(d.objId.pair, d.nextEscalation).map(e => {
            e.actionType match {
              case REPAIR =>
                val result = actionsClient.invoke(ActionableRequest(d.objId.pair.key, d.objId.pair.domain, e.action, d.objId.id))
                log.debug("Escalation result for action [%s] using %s is %s".format(e.name, d.objId, result))
              case IGNORE =>
                diffs.ignoreEvent(d.objId.pair.domain, d.seqId)
            }
          })
        } else {
          log.debug("{} Not processing escalation on {} as breaker has been tripped",
            formatAlertCode(pair, BREAKER_TRIPPED), d.objId)
        }

      case other =>
        log.warn("{} EscalationActor received unexpected message: {}",
          formatAlertCode(pair, SPURIOUS_ACTOR_MESSAGE), other)
    }
  }

  val timer = new Timer()
  val escalateTask = new TimerTask { def run() { escalateDiffs() } }
  val period = 1

  def start() {
    systemConfig.listPairs.foreach(p => startActor(p.asRef))

    timer.schedule(escalateTask, period * 1000, period * 1000)
  }

  override def close {
    timer.cancel()

    super.close
  }

  private object EscalationActor {
    def key(pair: DiffaPairRef) = "escalations:" + pair.identifier
  }

  def createPairActor(pair: DiffaPairRef) = Some(actorSystem.actorOf(
   Props(new EscalationActor(pair))))

  def initiateEscalation(e: DifferenceEvent) {
    progressDiff(e)
  }

  override def onAgentInstantiationCompleted(nc: NotificationCentre) {
    nc.registerForPairScanEvents(this)
  }

  def pairScanStateChanged(pair: DiffaPairRef, scanState: PairScanState) {
    scanState match {
      case PairScanState.FAILED     => escalatePairEvent(pair, SCAN_FAILED)
      case PairScanState.UP_TO_DATE => escalatePairEvent(pair, SCAN_COMPLETED)
      case _                        => // Not interesting
    }
  }

  def escalatePairEvent(pairRef: DiffaPairRef, eventType:String) = {
    findEscalationsForPair(pairRef, eventType).foreach(e => {
      log.debug("Escalating pair event as report %s".format(e.name))
      reportManager.executeReport(pairRef, e.action)
    })
  }

  def findEscalations(pair: DiffaPairRef, diff:DifferenceEvent) = {
    config.getPairDef(pair).escalations.
      filter(e => {
        if (e.rule == null) {
          true
        } else {
          val filter = new DefaultObjectFilter(e.rule, classOf[DifferenceEventRuleView])
          filter.accept(DifferenceEventRuleView(diff))
        }
    })
  }

  def findEscalationsForPair(pair: DiffaPairRef, eventType:String) = {
    config.getPairDef(pair).escalations.
      filter(e => e.rule == eventType && e.actionType == REPORT)
  }

  def findEscalation(pair: DiffaPairRef, name:String) =
    config.getPairDef(pair).escalations.find(_.name == name)

  def orderEscalations(escalations:Seq[EscalationDef]):Seq[EscalationDef] =
    escalations.sortBy(e => (e.delay, e.name))

  def escalateDiffs() {
    diffs.pendingEscalatees(DateTime.now(), escalateDiff(_))
  }

  def escalateDiff(diff:DifferenceEvent) {
    findActor(diff.objId) ! Escalate(diff)
    progressDiff(diff)
  }

  def progressDiff(diff:DifferenceEvent) {
    val diffType = DifferenceUtils.differenceType(diff.upstreamVsn, diff.downstreamVsn)
    val escalations = orderEscalations(findEscalations(diff.objId.pair, diff).toSeq)

    val selectedEscalation = diff.nextEscalation match {
      case null     => escalations.headOption
      case current  => escalations.map(_.name).indexOf(current) match {
          case -1 => None     // Current escalation doesn't exist. Don't try to apply any more.
          case i  => if (escalations.length > i+1) Some(escalations(i+1)) else None
      }
    }

    selectedEscalation match {
      case None       => diffs.scheduleEscalation(diff, null, null)
      case Some(esc)  =>
          // It is altogether possible that the escalation might already be due. We won't worry about that here, and
          // just let it be triggered on the next escalation run - otherwise we could end up getting stuck here for
          // quite a while if a whole bunch of escalations are due for the given difference. Forcing only a single
          // progression per run ensures that there might be a chance for the escalation to run.
          // TODO: Do we enforce a minimum time between escalations?
        val escalateTime = diff.detectedAt.plusSeconds(esc.delay)
        diffs.scheduleEscalation(diff, esc.name, escalateTime)
    }
  }
}

object EscalationManager {
  def validateRule(rule:String, path:String) {
    if (rule == null) return

    try {
      new DefaultObjectFilter(rule, classOf[DifferenceEventRuleView])
    } catch {
      case e:QueryParseException => throw new ConfigValidationException(path,
        "invalid rule '%s': %s".format(rule, e.getMessage))
    }
  }
}

case class Escalate(e:DifferenceEvent)

case class DifferenceEventRuleView(event:DifferenceEvent) {
  def getId = event.objId.id
  def getUpstream = event.upstreamVsn
  def getDownstream = event.downstreamVsn
  def getDetectedAt = event.detectedAt
  def getLastSeen = event.lastSeen

  def getHasUpstream = event.upstreamVsn != null
  def getUpstreamMissing = !getHasUpstream
  def getHasDownstream = event.downstreamVsn != null
  def getDownstreamMissing = !getHasDownstream
  def isMismatch = getHasUpstream && getHasDownstream && getUpstream != getDownstream
}