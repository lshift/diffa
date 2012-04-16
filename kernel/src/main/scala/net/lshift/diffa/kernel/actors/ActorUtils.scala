/**
 * Copyright (C) 2010-2012 LShift Ltd.
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

import akka.actor.{ActorRef, Actor}
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.config.DiffaPairRef
import net.lshift.diffa.kernel.util.AlertCodes._

object ActorUtils {

  case class ActorKey(pair: DiffaPairRef, keyFunc: DiffaPairRef => String) {
    lazy val key = keyFunc(pair)
    override def toString = key
  }

  val log = LoggerFactory.getLogger(getClass)

  def findActor(actorKey: ActorKey): Option[ActorRef] = Actor.registry.actorsFor(actorKey.key) match {
    case Array()            => None
    case Array(actor)       => Some(actor)
    case Array(actors @ _*) =>
        log.error("{} Too many actors for key: {}; actors = {}",
          Array[Object](formatAlertCode(actorKey.pair, MULTIPLE_ACTORS_FOR_KEY), actorKey, actors))
        throw new RuntimeException("Too many actors for key: " + actorKey)
  }
}