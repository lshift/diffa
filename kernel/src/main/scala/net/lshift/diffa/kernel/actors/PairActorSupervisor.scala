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

package net.lshift.diffa.kernel.actors

import akka.actor._
import akka.pattern.ask
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.participants.ParticipantFactory
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.lifecycle.AgentLifecycleAware
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.differencing._
import net.lshift.diffa.kernel.events.PairChangeEvent
import net.lshift.diffa.kernel.config.{PairRef, DiffaPairRef, DomainConfigStore}
import net.lshift.diffa.kernel.util.EndpointSide
import net.lshift.diffa.participant.scanning.{ScanAggregation, ScanRequest, ScanResultEntry, ScanConstraint}
import net.lshift.diffa.kernel.util.AlertCodes._
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Timeout
import net.lshift.diffa.kernel.scanning.ScanActivityStore

case class PairActorSupervisor(policyManager:VersionPolicyManager,
                               systemConfig:SystemConfigStore,
                               domainConfig:DomainConfigStore,
                               differencesManager:DifferencesManager,
                               pairScanListener:PairScanListener,
                               participantFactory:ParticipantFactory,
                               stores:VersionCorrelationStoreFactory,
                               diagnostics:DiagnosticsManager,
                               scanActivityStore:ScanActivityStore,
                               changeEventBusyTimeoutMillis:Long,
                               changeEventQuietTimeoutMillis:Long,
                               indexWriterCloseInterval: Int,
                               actorSystem: ActorSystem)
    extends AbstractActorSupervisor
    with PairPolicyClient
    with AgentLifecycleAware {

  private val log = LoggerFactory.getLogger(getClass)

  override def onAgentAssemblyCompleted = {
    // Initialize actors for any persistent pairs
    systemConfig.listPairs.foreach(p => startActor(p.asRef))
  }

 def createPairActor(pairRef: PairRef) = {
   val pair = domainConfig.getPairDef(pairRef)
   policyManager.lookupPolicy(pair.versionPolicyName) match {
     case Some(pol) =>
       val us = domainConfig.getEndpoint(pair.space,  pair.upstreamName)
       val ds = domainConfig.getEndpoint(pair.space,  pair.downstreamName)

       val usp = participantFactory.createUpstreamParticipant(us, pairRef)
       val dsp = participantFactory.createDownstreamParticipant(ds, pairRef)

       Some(actorSystem.actorOf(Props(
         new PairActor(pair, us, ds, usp, dsp, pol, stores(pairRef),
           differencesManager, pairScanListener,
           diagnostics, domainConfig, scanActivityStore, changeEventBusyTimeoutMillis, changeEventQuietTimeoutMillis,
           indexWriterCloseInterval, actorSystem)
       )))
     case None =>
       log.error("Failed to find policy for name: {}", formatAlertCode(pair.versionPolicyName, INVALID_VERSION_POLICY))
       None
   }
 }

  def propagateChangeEvent(event:PairChangeEvent) = findActor(event.id) ! ChangeMessage(event)

  // TODO: Pick more appropriate value.
  implicit val waitTimeout = Timeout(10 seconds)
  def startInventory(pair: PairRef, side: EndpointSide, view:Option[String]): Seq[ScanRequest] = {
    val future = (findActor(pair) ? StartInventoryMessage(side, view))

    Await.result(future, waitTimeout.duration).asInstanceOf[Seq[ScanRequest]]
  }

  def submitInventory(pair:PairRef, side:EndpointSide, constraints:Seq[ScanConstraint], aggregations:Seq[ScanAggregation], entries:Seq[ScanResultEntry]) = {
    val res = (findActor(pair) ? InventoryMessage(side, constraints, aggregations, entries))

    Await.result(res, waitTimeout.duration).asInstanceOf[Seq[ScanRequest]]
  }

  def difference(pairRef:PairRef) =
    findActor(pairRef) ! DifferenceMessage

  def scanPair(pair:PairRef, scanView:Option[String], initiatingUser:Option[String]) = {
    log.debug("Initiating scan %s with view %s".format(pair.identifier, scanView))
    // Update the scan state ourselves. The policy itself will send an update shortly, but since that happens
    // asynchronously, we might have returned before then, and this may potentially result in clients seeing
    // a "Up To Date" view, even though we're just about to transition out of that state.
    pairScanListener.pairScanStateChanged(pair, PairScanState.SCANNING)
    
    findActor(pair) ! ScanMessage(scanView, initiatingUser)
  }

  def cancelScans(pairRef:PairRef) = {
    val future = findActor(pairRef) ? CancelMessage
    Await.result(future, waitTimeout.duration) match {
      case flag => true
    }
  }

}
