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

import java.util.HashMap

import akka.actor._
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.participants.ParticipantFactory
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.lifecycle.AgentLifecycleAware
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.differencing._
import net.lshift.diffa.kernel.events.{VersionID, PairChangeEvent}
import net.lshift.diffa.kernel.config.{DiffaPairRef, DomainConfigStore, DiffaPair}
import net.lshift.diffa.kernel.util.{EndpointSide, Lazy, MissingObjectException}
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
                               changeEventQuietTimeoutMillis:Long)
    extends ActivePairManager
    with PairPolicyClient

    with AgentLifecycleAware {

  private type PotentialActor = Lazy[Option[ActorRef]]

  private val log = LoggerFactory.getLogger(getClass)
  private val pairActors = new HashMap[String, PotentialActor]()

  override def onAgentAssemblyCompleted = {
    // Initialize actors for any persistent pairs
    systemConfig.listPairs.foreach(p => startActor(p))
  }

  private def createPairActor(pair: DiffaPair) = policyManager.lookupPolicy(pair.versionPolicyName) match {
    case Some(pol) =>
      val us = domainConfig.getEndpoint(pair.domain.name,  pair.upstream)
      val ds = domainConfig.getEndpoint(pair.domain.name,  pair.downstream)

      val usp = participantFactory.createUpstreamParticipant(us, pair.asRef)
      val dsp = participantFactory.createDownstreamParticipant(ds, pair.asRef)

      Some(Actor.actorOf(
        new PairActor(pair, us, ds, usp, dsp, pol, stores(pair.asRef),
          differencesManager, pairScanListener,
          diagnostics, domainConfig, changeEventBusyTimeoutMillis, changeEventQuietTimeoutMillis)
      ))
    case None =>
      log.error("Failed to find policy for name: {}", formatAlertCode(pair.versionPolicyName, INVALID_VERSION_POLICY))
      None
  }

  /**
   * Removes an actor from the map in a critical section.
   *
   * @return the actor for the given pair, or null if there was no actor in the map
   */
  private def unregisterActor(pair: DiffaPairRef) = pairActors.synchronized {
    val actor = pairActors.get(pair.identifier)
    if (actor != null) {
      pairActors.remove(pair.identifier)
    }
    actor
  }

  def startActor(pair: DiffaPair) = {
    def createAndStartPairActor = createPairActor(pair) map { actor =>
      actor.start()
      log.info("{} actor started", formatAlertCode(pair.asRef, ACTOR_STARTED))
      actor
    }

    // This block is essentially an atomic replace with delayed initialization
    // of the pair actor.  Initialization (Lazy(...)) must be performed
    // outside the critical section, since it involves expensive
    // (time-consuming) database calls.
    // The return value of the block is the actor that was replaced, or null if
    // there was no actor previously registered.
    // TODO: replace this with oldActor = ConcurrentHashMap.replace(key, value)
    val oldActor = pairActors.synchronized {
      val oldActor = unregisterActor(pair.asRef)
      val lazyActor = new Lazy(createAndStartPairActor)
      pairActors.put(pair.identifier, lazyActor)
      oldActor
    }

    // If there was an existing actor that needs to be stopped, do it outside
    // the synchronized block.  Stopping the actor can cause actions such as
    // scans to fail, which is expected.
    if (oldActor != null) {
      oldActor().map(_.stop())
      log.info("{} Stopping existing actor for key: {}",
        formatAlertCode(pair.asRef, ACTOR_STOPPED), pair.identifier)
    }
  }

  def stopActor(pair: DiffaPairRef) = {
    // TODO: replace this with actor = ConcurrentHashMap.remove(key)
    val actor = unregisterActor(pair)

    // Another thread may have obtained a reference to this actor prior to the
    // unregisterActor call, but the only such cases are safe cases such as
    // pair scans which will fail as expected if the actor is stopped just
    // before the scan attempts to use it.
    if (actor != null) {
      actor().map(_.stop())
      log.info("{} actor stopped", formatAlertCode(pair, ACTOR_STOPPED))
    } else {
      log.warn("{} Could not resolve actor for key: {}",
        formatAlertCode(pair,  MISSING_ACTOR_FOR_KEY), pair.identifier)
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

  def findActor(id:VersionID) : ActorRef = findActor(id.pair)

  def findActor(pair: DiffaPairRef) = {
    val actor = pairActors.get(pair.identifier)
    if (actor == null) {
      log.error("{} Could not resolve actor for key: {}; {} registered actors: {}",
        Array[Object](formatAlertCode(pair, MISSING_ACTOR_FOR_KEY), pair.identifier,
                      Integer.valueOf(pairActors.size()), pairActors.keySet()))
      throw new MissingObjectException(pair.identifier)
    }
    actor().getOrElse {
      log.error("{} Unusable actor due to failure to look up policy during actor creation",
        formatAlertCode(pair, BAD_ACTOR))
      throw new MissingObjectException(pair.identifier)
    }
  }
}
