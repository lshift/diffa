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

import net.jcip.annotations.ThreadSafe
import net.lshift.diffa.kernel.events.PairChangeEvent
import net.lshift.diffa.kernel.config.DiffaPairRef
import net.lshift.diffa.participant.scanning.{ScanResultEntry, ScanConstraint}

/**
 * This is a thread safe entry point to an underlying version policy.
 */
@ThreadSafe
trait PairPolicyClient {

  /**
   * Propagates the change event to the underlying policy implementation in a serial fashion.
   */
  def propagateChangeEvent(event:PairChangeEvent) : Unit

  /**
   * Submits an inventory of upstream entries for the given constrained space.
   */
  def submitInventory(pair:DiffaPairRef, side:EndpointSide, constraints:Seq[ScanConstraint], entries:Seq[ScanResultEntry])

  /**
   * Runs a replayUnmatchedDifferences report based on stored data for the given pair. Does not scan with the participants
   * beforehand - use <code>scanPair</code> to do the scan first.
   */
  def difference(pairRef:DiffaPairRef)

  /**
   * Scans the participants belonging to the given pair, then generates a different report.
   * Activities are performed on the underlying policy in a thread safe manner, allowing multiple
   * concurrent operations to be submitted safely against the same pair concurrently.
   * @param scanView the view of the participants that should be used when running the scan.
   */
  def scanPair(pair:DiffaPairRef, scanView:Option[String]) : Unit

  /**
   * Cancels any scan operation that may be in process.
   * This is a blocking call, so it will only return after all current and pending scans have been cancelled.
   */
  def cancelScans(pair:DiffaPairRef) : Boolean
}

abstract class EndpointSide
case object UpstreamEndpoint extends EndpointSide
case object DownstreamEndpoint extends EndpointSide