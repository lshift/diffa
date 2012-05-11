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

package net.lshift.diffa.kernel.participants

import java.io.Closeable
import net.lshift.diffa.participant.correlation.ProcessingResponse
import net.lshift.diffa.participant.scanning.{ScanConstraint, ScanResultEntry}

/**
 * Trait supported by various RPC bindings providing communications with participants.
 */
trait Participant
{
  /**
   * Scans this participant with the given constraints and aggregations.
   */
  def scan(constraints:Seq[ScanConstraint], aggregations:Seq[CategoryFunction]): Seq[ScanResultEntry]

  /**
   * Requests that the participant return a serialized form of the item with the given identifier.
   */
  def retrieveContent(identifier:String): String
}

trait UpstreamParticipant extends Participant {}

trait DownstreamParticipant extends Participant {
  /**
   * Requests that the participant generate a processing response based on the given incoming entity data. The downstream
   * participant is not expected to re-admit the data into its system - it should simply parse the data so as to be able
   * to determine it's own version information.
   */
  def generateVersion(entityBody: String): ProcessingResponse
}

object ParticipantType extends Enumeration {
  type ParticipantType = Value
  val UPSTREAM = Value("upstream")
  val DOWNSTREAM = Value("downstream")
}