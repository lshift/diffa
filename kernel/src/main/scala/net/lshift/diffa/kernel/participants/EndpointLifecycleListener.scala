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
 * Trait for receiving notifications about the lifecycle of endpoints within the system.
 */
trait EndpointLifecycleListener {
  /**
   * Indicates that the given endpoint has become available (or has been updated).
   */
  def onEndpointAvailable(e:DomainEndpointDef)

  /**
   * Indicates that the endpoint with the given domain and name is no longer available within the system.
   */
  def onEndpointRemoved(domain: String, endpoint: String)
}