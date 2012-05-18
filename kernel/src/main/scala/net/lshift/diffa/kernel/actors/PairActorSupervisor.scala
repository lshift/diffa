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

import akka.actor.Actor
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.participants.ParticipantFactory
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.lifecycle.AgentLifecycleAware
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.differencing._
import net.lshift.diffa.kernel.events.PairChangeEvent
import net.lshift.diffa.kernel.config.{DiffaPairRef, DomainConfigStore}
import net.lshift.diffa.kernel.util.EndpointSide
import net.lshift.diffa.participant.scanning.{ScanAggregation, ScanRequest, ScanResultEntry, ScanConstraint}
import net.lshift.diffa.kernel.util.AlertCodes._

case class PairActorSupervisor(policyManager:VersionPolicyManager,
                               systemConfig:SystemConfigStore,
                               domainConfig:DomainConfigStore,
                               differencesManager:DifferencesManager,
                               pairScanListener:PairScanListener,
                               participantFactory:ParticipantFactory,
                               stores:VersionCorrelationStoreFactory,
                               diagnostics:DiagnosticsManager,
                               changeEventBusyTimeoutMillis:Long,
                               changeEventQuietTimeoutMillis:Long,
                               indexWriterCloseInterval: Int)
    extends AbstractActorSupervisor
    with PairPolicyClient
    with AgentLifecycleAware {

  private val log = LoggerFactory.getLogger(getClass)

  override def onAgentAssemblyCompleted = {
    // Initialize actors for any persistent pairs
    systemConfig.listPairs.foreach(p => startActor(p.asRef))
  }

 def createPairActor(pairRef: DiffaPairRef) = {
   val pair = systemConfig.getPair(pairRef)
   policyManager.lookupPolicy(pair.versionPolicyName) match {
     case Some(pol) =>
       val us = domainConfig.getEndpoint(pair.domain.name,  pair.upstream)
       val ds = domainConfig.getEndpoint(pair.domain.name,  pair.downstream)

       val usp = participantFactory.createUpstreamParticipant(us, pairRef)
       val dsp = participantFactory.createDownstreamParticipant(ds, pairRef)

       Some(Actor.actorOf(
         new PairActor(pair, us, ds, usp, dsp, pol, stores(pairRef),
           differencesManager, pairScanListener,
           diagnostics, domainConfig, changeEventBusyTimeoutMillis, changeEventQuietTimeoutMillis, indexWriterCloseInterval)
       ))
     case None =>
       log.error("Failed to find policy for name: {}", formatAlertCode(pair.versionPolicyName, INVALID_VERSION_POLICY))
       None
   }
 }

  def propagateChangeEvent(event:PairChangeEvent) = findActor(event.id) ! ChangeMessage(event)

  def startInventory(pair: DiffaPairRef, side: EndpointSide, view:Option[String]): Seq[ScanRequest] = {
    (findActor(pair) ? StartInventoryMessage(side, view)).as[Seq[ScanRequest]].get
  }

  def submitInventory(pair:DiffaPairRef, side:EndpointSide, constraints:Seq[ScanConstraint], aggregations:Seq[ScanAggregation], entries:Seq[ScanResultEntry]) = {
    (findActor(pair) ? InventoryMessage(side, constraints, aggregations, entries)).as[Seq[ScanRequest]].get
  }

  def difference(pairRef:DiffaPairRef) =
    findActor(pairRef) ! DifferenceMessage

  def scanPair(pair:DiffaPairRef, scanView:Option[String]) = {
    log.debug("Initiating scan %s with view %s".format(pair.identifier, scanView))
    // Update the scan state ourselves. The policy itself will send an update shortly, but since that happens
    // asynchronously, we might have returned before then, and this may potentially result in clients seeing
    // a "Up To Date" view, even though we're just about to transition out of that state.
    pairScanListener.pairScanStateChanged(pair, PairScanState.SCANNING)
    
    findActor(pair) ! ScanMessage(scanView)
  }

  def cancelScans(pairRef:DiffaPairRef) = {
    (findActor(pairRef) !! CancelMessage) match {
      case Some(flag) => true
      case None       => false
    }
  }

}
