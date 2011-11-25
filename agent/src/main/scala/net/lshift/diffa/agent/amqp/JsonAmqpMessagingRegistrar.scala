/**
 *  Copyright (C) 2010-2011 LShift Ltd.
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

package net.lshift.diffa.agent.amqp

import net.lshift.diffa.kernel.frontend.Changes
import net.lshift.diffa.kernel.config.Endpoint
import collection.mutable.HashMap
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.participants.{InboundEndpointFactory, InboundEndpointManager}
import com.rabbitmq.client.AMQP.BasicProperties
import net.lshift.accent.AccentConnection

/**
 * Utility class responsible for registering JSON over AMQP protocol support with necessary factories
 * and request handlers.
 */
class JsonAmqpMessagingRegistrar(con: AccentConnection,
                                 inboundEndpointManager: InboundEndpointManager,
                                 changes: Changes,
                                 timeoutMillis: Long) {

  private val log = LoggerFactory.getLogger(getClass)

  // Register the inbound changes handler
  inboundEndpointManager.registerFactory(new InboundEndpointFactory {

    val consumers = new HashMap[String, AccentReceiver]

    def canHandleInboundEndpoint(inboundUrl: String, contentType: String) =
      inboundUrl.startsWith("amqp://")

    def ensureEndpointReceiver(e: Endpoint) {
      log.info("Starting consumer for endpoint: %s".format(e))

      val params = new ReceiverParameters(AmqpQueueUrl.parse(e.inboundUrl).queue)

      val c = new AccentReceiver(con,
                                 params,
                                 e.domain.name,
                                 e.name,
                                 changes)
      consumers.put(e.name, c)
    }

    def endpointGone(key: String) {
      consumers.get(key).map { c =>
        try {
          c.close()
        } catch {
          case _ => log.error("Unable to shutdown consumer for endpoint name %s".format(key))
        }
      }
      consumers.remove(key)
    }
  })
}