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

import collection.mutable.ListBuffer
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.lifecycle.AgentLifecycleAware
import net.lshift.diffa.kernel.config.Endpoint
import net.lshift.diffa.kernel.config.system.SystemConfigStore

/**
 * Manager for delegating inbound endpoint setup/destruction to InboundEndpointFactory instances.
 */
class InboundEndpointManager(configStore:SystemConfigStore) extends EndpointLifecycleListener with AgentLifecycleAware {
  private val factories = new ListBuffer[InboundEndpointFactory]
  private val log = LoggerFactory.getLogger(classOf[InboundEndpointFactory])

  /**
   * Registers the given factory as being able to create inbound endpoints on demand.
   */
  def registerFactory(f:InboundEndpointFactory) = factories += f

  /**
   * Should be called whenever an endpoint becomes available (in either a new or updated form)
   */
  def onEndpointAvailable(e:Endpoint) {
    if (e.inboundUrl != null) {
      // We need to ensure we have an inbound receiver
      val validFactories = factories.filter(_.canHandleInboundEndpoint(e.inboundUrl))
      if (validFactories.length == 0) {
        log.error("No InboundEndpointFactories accepted the endpoint " + e + ". Change events may not be received.")
      } else {
        validFactories.foreach(f => {
          try {
            f.ensureEndpointReceiver(e)
          } catch {
            case ex => log.error("Failed to create an endpoint receiver for " + e, ex)
          }
        })
      }
    }
  }

  /**
   * Should be called whenever an endpoint is removed.
   */
  def onEndpointRemoved(domain: String, endpoint: String) {
    factories.foreach(_.endpointGone(domain, endpoint))
  }

  /**
   * Indicates to the endpoint manager that it should request factories to create endpoints for all registered
   * endpoints that reference an inbound address.
   */
  override def onAgentConfigurationActivated {
    configStore.listEndpoints.foreach(onEndpointAvailable(_))
  }
}