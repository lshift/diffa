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
import net.lshift.diffa.kernel.util.AlertCodes._
import net.lshift.diffa.kernel.config.DiffaPairRef
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.util.{MissingObjectException, Lazy}
import java.io.Closeable
import scala.collection.JavaConversions._
import akka.actor.{ActorSystem, ActorRef}

/**
 * Common superclass for components that need to manage actors.
 */
abstract class AbstractActorSupervisor
  extends ActivePairManager
  with Closeable {

  private val logger = LoggerFactory.getLogger(getClass)

  private type PotentialActor = Lazy[Option[ActorRef]]
  private val pairActors = new HashMap[DiffaPairRef, PotentialActor]()


  val actorSystem: ActorSystem


  /**
   * Creates a topical actor for the given pair - supervising actor subclasses need to
   * define the concrete actor behavior they require.
   */
  def createPairActor(pair:DiffaPairRef) : Option[ActorRef]

    // Note: The .toSeq ensures we don't end up with ConcurrentModificationExceptions since we iterate off
    // a copy of the list
  def close = pairActors.toSeq.foreach{ case (pairRef,_) => stopActor(pairRef) }

  def startActor(pair: DiffaPairRef) {

    def createAndStartPairActor = createPairActor(pair) map { actor =>
      logger.info("{} actor started", formatAlertCode(pair, ACTOR_STARTED))
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
      val oldActor = unregisterActor(pair)
      val lazyActor = new Lazy(createAndStartPairActor)
      pairActors.put(pair, lazyActor)
      oldActor
    }

    // If there was an existing actor that needs to be stopped, do it outside
    // the synchronized block.  Stopping the actor can cause actions such as
    // scans to fail, which is expected.
    if (oldActor != null) {
      oldActor().map(actorSystem.stop(_))
      logger.info("{} Stopping existing actor for key: {}",
        formatAlertCode(pair, ACTOR_STOPPED), pair.identifier)
    }
  }

  def stopActor(pair: DiffaPairRef) {
    // TODO: replace this with actor = ConcurrentHashMap.remove(key)
    val actor = unregisterActor(pair)

    // Another thread may have obtained a reference to this actor prior to the
    // unregisterActor call, but the only such cases are safe cases such as
    // pair scans which will fail as expected if the actor is stopped just
    // before the scan attempts to use it.
    if (actor != null) {
      actor().map(actorSystem.stop(_))
      logger.info("{} actor stopped", formatAlertCode(pair, ACTOR_STOPPED))
    } else {
      logger.warn("{} Could not resolve actor for key: {}",
        formatAlertCode(pair,  MISSING_ACTOR_FOR_KEY), pair.identifier)
    }
  }

  /**
   * Removes an actor from the map in a critical section.
   *
   * @return the actor for the given pair, or null if there was no actor in the map
   */
  protected def unregisterActor(pair:DiffaPairRef) = pairActors.synchronized {
    val actor = pairActors.get(pair)
    if (actor != null) {
      pairActors.remove(pair)
    }
    actor
  }

  def findActor(id:VersionID) : ActorRef = findActor(id.pair)

  def findActor(pair: DiffaPairRef) = {

    val actor = pairActors.get(pair)

    if (actor == null) {
      logger.error("{} Could not resolve actor for key: {}; {} registered actors: {}",
        Array[Object](formatAlertCode(pair, MISSING_ACTOR_FOR_KEY), pair.identifier,
          Integer.valueOf(pairActors.size()), pairActors.keySet()))
      throw new MissingObjectException(pair.identifier)
    }

    actor().getOrElse {
      logger.error("{} Unusable actor due to failure to look up policy during actor creation",
        formatAlertCode(pair, BAD_ACTOR))
      throw new MissingObjectException(pair.identifier)
    }

  }

}