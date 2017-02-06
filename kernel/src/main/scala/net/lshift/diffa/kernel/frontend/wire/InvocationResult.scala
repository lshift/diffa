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

package net.lshift.diffa.kernel.frontend.wire

import scala.beans.BeanProperty
import net.lshift.diffa.kernel.util.AlertCodes

/**
 * This encapsulates the result of invoking an action against a participant
 */
case class InvocationResult (
  @BeanProperty var code:String,
  @BeanProperty var output:String) {

  def this() = this(null, null)
}

object InvocationResult {
  /**
   * Factory for a InvocationResult indicating that the agent received a response from the endpoint
   */
  def received(httpStatus: Int, httpEntity: String) = InvocationResult(httpStatus.toString, httpEntity)

  /**
   * Factory for an InvocationResult indicating that the agent could not communicate with the endpoint
   */
  def failure(thrown: Throwable) = InvocationResult(AlertCodes.ACTION_ENDPOINT_FAILURE.toString, thrown.getStackTraceString)
}
