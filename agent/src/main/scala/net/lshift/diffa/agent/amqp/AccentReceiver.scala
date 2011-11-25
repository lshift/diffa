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

import org.slf4j.LoggerFactory
import java.io.{IOException, OutputStream, ByteArrayInputStream}
import com.rabbitmq.client.{Envelope, Consumer, ShutdownSignalException, Channel}
import com.rabbitmq.client.AMQP.BasicProperties
import java.util.concurrent.Executors
import net.lshift.accent.{AccentConsumer, ChannelListenerAdapter, AccentConnection}
import java.util.concurrent.atomic.AtomicBoolean
import java.lang.String
import net.lshift.diffa.kernel.frontend.Changes
import net.lshift.diffa.participant.common.JSONHelper


/**
 * AMQP message consumer. Uses a ProtocolHandler to process messages as TransportRequests,
 * and produces no response.
 */
class AccentReceiver(con: AccentConnection,
                     params:ReceiverParameters,
                     domain:String,
                     endpoint:String,
                     changes: Changes)
  extends AccentAwareComponent(con) with Consumer {

  protected val log = LoggerFactory.getLogger(getClass)

  val isClosing = new AtomicBoolean(false)

  private val pool = Executors.newCachedThreadPool()

  channel.addChannelSetupListener(new ChannelListenerAdapter() {
    override def channelCreated(c: Channel) {
      log.debug("Declaring queue: %s".format(params.queueName))
      c.queueDeclare(params.queueName, params.queueIsDurable, params.queueIsExclusive, params.autoDelete, null)
      c.basicQos(params.prefetchCount)
    }
  })

  val consumer = new AccentConsumer(channel, params.queueName, this)

  def handleConsumeOk(consumerTag: String) {}
  def handleCancelOk(consumerTag: String) {}
  def handleCancel(consumerTag: String) {}
  def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException) {}
  def handleRecoverOk() {}

  def handleDelivery(consumerTag: String, header: Envelope, properties: BasicProperties, body: Array[Byte]) = {
    if (!pool.isShutdown() && !isClosing.get()) {
      pool.submit(new Runnable() {

        def run() {
          try {

            JSONHelper.readChangeEvents(new ByteArrayInputStream(body)).foreach(
              changes.onChange(domain, endpoint, _)
            )

            consumer.reliableAck(header.getDeliveryTag, false)
          }
          catch {
            case e => {
              consumer.reliableReject(header.getDeliveryTag, false)
              log.error("Rejected message (%s) due to error".format(new String(body)), e)
            }
          }
        }
      })
    }
    else {
      val msg = "Ignoring message (%s) with delivery (%s) because the worker pool has been shutdown"
      log.warn(msg.format(new String(body), header.getDeliveryTag))
    }
  }

  override def close() {
    if (!isClosing.getAndSet(true)) {
      pool.shutdown()
      consumer.close()
    }
    super.close()
  }
}

class ReceiverParameters (
  var queueName:String,
  var prefetchCount:Int = 1,
  var autoDelete:Boolean = false,
  var queueIsDurable:Boolean = true,
  var queueIsExclusive:Boolean = false
)
