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

import net.lshift.diffa.kernel.frontend.DomainEndpointDef

/**
 * Trait implemented by factories that can generate inbound endpoint change receivers.
 */
trait InboundEndpointFactory {

  /**
   * Allows the agent to determine if this factory can handle inbound endpoints with the given url. If
   * the factory responds with true, then a subsequent request to ensure an endpoint is available will be sent.
   */
  def canHandleInboundEndpoint(url:String):Boolean

  /**
   * Indicates that the factory should ensure an receiving endpoint is available for the given endpoint. This call will
   * only be made if a previous call to <code>canHandleInboundEndpoint</code> has been made and returned a positive
   * result.
   */
  def ensureEndpointReceiver(e:DomainEndpointDef)

  /**
   * Indicates to the factory that the endpoint with the given domain and name has been removed from the system. Note that the
   * system will not filter these events based on factory support, so factories should expect to see more removal
   * events than ensure events, and should silently ignore any unknown endpoints.
   */
  def endpointGone(domain: String, endpoint: String)
}