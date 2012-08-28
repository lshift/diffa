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

package net.lshift.diffa.kernel.differencing

import net.lshift.diffa.kernel.events.PairChangeEvent
import net.jcip.annotations.NotThreadSafe
import net.lshift.diffa.kernel.participants.{UpstreamParticipant, DownstreamParticipant}
import net.lshift.diffa.kernel.config.{PairRef, Endpoint, DiffaPairRef}
import net.lshift.diffa.kernel.util.EndpointSide
import net.lshift.diffa.participant.scanning.{ScanAggregation, ScanRequest, ScanResultEntry, ScanConstraint}

/**
 * Policy implementations of this trait provide different mechanism for handling the matching of upstream
 * and downstream events. This functionality is pluggable since different systems may have different views
 * on how to compare version information between participants.
 *
 * Please note that this trait is by design <em>NOT</em> thread safe and hence any access to it must be
 * serialized by the caller.
 *
 */
@NotThreadSafe
trait VersionPolicy {

  /**
   * Indicates to the policy that a change has occurred within a participant.
   */
  def onChange(writer: LimitedVersionCorrelationWriter, evt:PairChangeEvent) : Unit

  /**
   * Requests that the policy return details of how to start an inventory.
   */
  def startInventory(pairRef:PairRef, endpoint:Endpoint, view:Option[String], writer: LimitedVersionCorrelationWriter, side:EndpointSide):Seq[ScanRequest]

  /**
   * Requests that the policy process an inventory of changes.
   */
  def processInventory(pairRef:PairRef, endpoint:Endpoint, writer: LimitedVersionCorrelationWriter, side:EndpointSide,
                       constraints:Seq[ScanConstraint], aggregations:Seq[ScanAggregation], entries:Seq[ScanResultEntry]):Seq[ScanRequest]

  /**
   * Requests that the policy scan the upstream participants for the given pairing. Differences that are
   * detected will be reported to the listener provided.
   * @throws If the shouldRun variable is set to false, this will throw a ScanCancelledException
   */
  def scanUpstream(scanId:Long, pairRef:PairRef, upstream:Endpoint, view:Option[String], writer: LimitedVersionCorrelationWriter,
                   participant:UpstreamParticipant, listener:DifferencingListener,
                   handle:FeedbackHandle)

  /**
   * Requests that the policy scan the downstream participants for the given pairing. Differences that are
   * detected will be reported to the listener provided.
   * @throws If the shouldRun variable is set to false, this will throw a ScanCancelledException
   */
  def scanDownstream(scanId:Long, pairRef:PairRef, downstream:Endpoint,  view:Option[String], writer: LimitedVersionCorrelationWriter,
                     us:UpstreamParticipant, ds:DownstreamParticipant,
                     listener:DifferencingListener, handle:FeedbackHandle)

}

/**
 * This provides an invoker with the ability to notify an invokee that a submitted task should be cancelled.
 */
trait FeedbackHandle {
  /**
   * This cancels the current running task.
   */
  def cancel()

  /**
   * This indicates whether the current running task has been cancelled.
   */
  def isCancelled : Boolean

}

/**
 * Thrown when a scan has been cancelled.
 */
class ScanCancelledException(pair:PairRef) extends Exception(pair.identifier)

/**
 * Thrown when a participant driver encounters an issue that it can't solve, but is well known, so it uses this
 * exception to keep the logging less noisy.
 * The reason is exposed through the UI.
 */
class ScanFailedException(reason:String) extends Exception(reason)

class ScanLimitBreachedException(reason:String) extends Exception(reason)
