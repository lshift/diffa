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
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.participants.ParticipantFactory
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.lifecycle.AgentLifecycleAware
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.differencing._
import net.lshift.diffa.kernel.events.{VersionID, PairChangeEvent}
import net.lshift.diffa.kernel.config.{DiffaPairRef, DomainConfigStore, DiffaPair}

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

  private val log = LoggerFactory.getLogger(getClass)

  override def onAgentAssemblyCompleted = {
    // Initialize actors for any persistent pairs
    systemConfig.listPairs.foreach(p => startActor(p))
  }

  def startActor(pair:DiffaPair) = {
    val actors = Actor.registry.actorsFor(pair.identifier)
    actors.length match {
      case 0 => {
        policyManager.lookupPolicy(pair.versionPolicyName) match {
          case Some(pol) => {
            val us = domainConfig.getEndpoint(pair.domain.name,  pair.upstream)
            val ds = domainConfig.getEndpoint(pair.domain.name,  pair.downstream)

            val usp = participantFactory.createUpstreamParticipant(us)
            val dsp = participantFactory.createDownstreamParticipant(ds)
            val pairActor = Actor.actorOf(
              new PairActor(pair, us, ds, usp, dsp, pol, stores(pair.asRef),
                            differencesManager, pairScanListener,
                            diagnostics, changeEventBusyTimeoutMillis, changeEventQuietTimeoutMillis)
            )
            pairActor.start
            log.info("Started actor for key: " + pair.identifier)
          }
          case None    => log.error("Failed to find policy for name: " + pair.versionPolicyName)
        }

      }
      case 1    => log.warn("Attempting to re-spawn actor for key: " + pair.identifier)
      case x    => log.error("Too many actors for key: " + pair.identifier + "; actors = " + x)
    }
  }

  def stopActor(pair:DiffaPairRef) = {
    val actors = Actor.registry.actorsFor(pair.identifier)
    actors.length match {
      case 1 => {
        val actor = actors(0)
        actor.stop
        log.info("Stopped actor for key: " + pair.identifier)
      }
      case 0    => log.warn("Could not resolve actor for key: " + pair.identifier)
      case x    => log.error("Too many actors for key: " + pair.identifier + "; actors = " + x)
    }
  }

  def propagateChangeEvent(event:PairChangeEvent) = findActor(event.id) ! ChangeMessage(event)

  def difference(pairRef:DiffaPairRef) =
    findActor(pairRef) ! DifferenceMessage

  def scanPair(pair:DiffaPairRef, scanView:Option[String]) = {
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

  def findActor(pair:DiffaPairRef) = {
    val actors = Actor.registry.actorsFor(pair.identifier)
    actors.length match {
      case 1 => actors(0)
      case 0 => {
        log.error("Could not resolve actor for key: " + pair.identifier)
        log.error("Found %s actors: %s".format(Actor.registry.size, Actor.registry.actors))
        throw new MissingObjectException(pair.identifier)
      }
      case x => {
        log.error("Too many actors for key: " + pair.identifier + "; actors = " + x)
        throw new RuntimeException("Too many actors: " + pair.identifier)
      }
    }
  }
}