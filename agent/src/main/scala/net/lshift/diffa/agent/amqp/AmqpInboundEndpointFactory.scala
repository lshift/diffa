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

import collection.mutable.HashMap
import net.lshift.diffa.kernel.frontend.{DomainEndpointDef, Changes}
import net.lshift.diffa.kernel.participants.InboundEndpointFactory
import org.slf4j.LoggerFactory
import net.lshift.accent.AccentConnection
import com.rabbitmq.client.ConnectionFactory

class AmqpInboundEndpointFactory(changes: Changes)
  extends InboundEndpointFactory {

  case class ConnectionKey(host: String, port: Int, username: String, password: String, vHost: String)

  object ConnectionKey {
    def fromUrl(url: AmqpQueueUrl) =
      ConnectionKey(host = url.host, port = url.port, username = url.username, password = url.password, vHost = url.vHost)
  }

  case class ReceiverKey(space: Long, endpoint: String)

  object ReceiverKey {
    def fromEndpoint(e: DomainEndpointDef) =
      ReceiverKey(space = e.space, endpoint = e.name)
  }

  class Receivers(val connection: AccentConnection,
                  val connectionKey: ConnectionKey) extends HashMap[ReceiverKey, AccentReceiver]

  val log = LoggerFactory.getLogger(getClass)

  val receivers = new HashMap[ConnectionKey, Receivers]

  def canHandleInboundEndpoint(inboundUrl: String) =
    inboundUrl.startsWith("amqp://")

  def ensureEndpointReceiver(e: DomainEndpointDef) {
    log.info("Starting receiver for endpoint: %s".format(e))

    val amqpUrl = AmqpQueueUrl.parse(e.inboundUrl)


    val receiversForUrl = getReceiversByUrl(amqpUrl)
    val receiverKey = ReceiverKey.fromEndpoint(e)
    receiversForUrl.put(receiverKey,
                        createReceiver(receiversForUrl.connection, amqpUrl.queue, receiverKey))
  }

  def endpointGone(space:Long, endpoint: String) {
    val key = ReceiverKey(space, endpoint)
    
    getReceiversByKey(key) match {
      case None =>
        log.error("No receivers for endpoint name: %s".format(endpoint))

      case Some(rcv) =>
        rcv.get(key) map { c =>
          try {
            c.close()
          } catch {
            case _ => log.error("Unable to shutdown receiver for endpoint name %s".format(endpoint))
          }
        }
        rcv.remove(key)

        // if there are no more receivers on the connection, close it
        if (rcv.isEmpty) {
          try {
            rcv.connection.close()
          } catch {
            case _ => log.error("Unable to shutdown connection for endpoint name %s".format(endpoint))
          }
          receivers.remove(rcv.connectionKey)
        }
    }
  }

  protected def createConnectionFactory(url: AmqpQueueUrl) = {
    val cf = new ConnectionFactory()
    cf.setHost(url.host)
    cf.setPort(url.port)
    cf.setUsername(url.username)
    cf.setPassword(url.password)
    if (! url.isDefaultVHost) {
      cf.setVirtualHost(url.vHost)
    }
    cf
  }

  protected def createConnection(cf: ConnectionFactory) =
    new AccentConnection(cf, new AccentConnectionFailureHandler)

  protected def createReceiver(connection: AccentConnection, queue: String, key: ReceiverKey) = {
    val params = new ReceiverParameters(queue)
    new AccentReceiver(connection,
                       params,
                       key.space,
                       key.endpoint,
                       changes)
  }

  private def getReceiversByUrl(url: AmqpQueueUrl): Receivers = {
    val connectionKey = ConnectionKey.fromUrl(url)
    receivers.getOrElseUpdate(connectionKey, {
      val connection = createConnection(createConnectionFactory(url))
      new Receivers(connection, connectionKey)
    })
  }

  private def getReceiversByKey(key: ReceiverKey): Option[Receivers] = {
    receivers.values.find(_.contains(key))
  }
}