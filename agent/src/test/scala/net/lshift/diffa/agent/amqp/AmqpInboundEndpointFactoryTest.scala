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

package net.lshift.diffa.agent.amqp


import com.rabbitmq.client.ConnectionFactory
import net.lshift.accent.AccentConnection
import net.lshift.diffa.kernel.participants.InboundEndpointFactory
import org.junit._
import org.junit.Assert._
import org.easymock.EasyMock.expect
import org.easymock.classextension.EasyMock._
import net.lshift.diffa.kernel.config.{Domain, Endpoint}
import scala.collection.mutable.ListBuffer
import net.lshift.diffa.kernel.frontend.DomainEndpointDef

class AmqpInboundEndpointFactoryTest { thisTest =>

  var inboundEndpointFactory: InboundEndpointFactory = _
  val connections = ListBuffer[AccentConnection]()
  val receivers = ListBuffer[AccentReceiver]()

  @Before
  def setup() {
    inboundEndpointFactory = new AmqpInboundEndpointFactory(null) {
      protected override def createConnectionFactory(url: AmqpQueueUrl): ConnectionFactory =
        null

      protected override def createConnection(cf: ConnectionFactory): AccentConnection = {
        val c = createMock(classOf[AccentConnection])
        thisTest.connections += c
        c
      }

      protected override def createReceiver(connection: AccentConnection, queue: String, key: ReceiverKey): AccentReceiver = {
        val c = createMock(classOf[AccentReceiver])
        thisTest.receivers += c
        c
      }
    }
  }

  private def replayAll() {
    connections.foreach { replay(_) }
    receivers.foreach { replay(_) }
  }

  private def verifyAll() {
    connections.foreach { verify(_) }
    receivers.foreach { verify(_) }
  }

  private def resetAll() {
    connections.foreach { reset(_) }
    receivers.foreach { reset(_) }
  }

  private def endpointWithAmqpUrl(name: String, amqpUrl: String) = {
    DomainEndpointDef(name = name, inboundUrl = amqpUrl, domain = "domain")
  }

  @Test
  def shutsDownSingleReceiverAndConnection() {
    val e = endpointWithAmqpUrl("ep0", "amqp://localhost//queues/q0")
    inboundEndpointFactory.ensureEndpointReceiver(e)

    assertEquals(1, connections.size)
    assertEquals(1, receivers.size)

    expect(receivers(0).close())
    expect(connections(0).close())
    replayAll()
    inboundEndpointFactory.endpointGone(e.space, e.name)
    verifyAll()
  }

  @Test
  def shutsDownMultipleReceiversAndThenTheConnection() {
    val e1 = endpointWithAmqpUrl("ep1", "amqp://localhost//queues/q1")
    val e2 = endpointWithAmqpUrl("ep2", "amqp://localhost//queues/q2")
    inboundEndpointFactory.ensureEndpointReceiver(e1)
    inboundEndpointFactory.ensureEndpointReceiver(e2)

    assertEquals(1, connections.size)
    assertEquals(2, receivers.size)

    val c1 = receivers(0)
    val c2 = receivers(1)

    expect(c1.close())
    replayAll()
    inboundEndpointFactory.endpointGone(e1.space, e1.name)
    verifyAll()

    resetAll()

    expect(c2.close())
    expect(connections(0).close())
    replayAll()
    inboundEndpointFactory.endpointGone(e2.space, e2.name)
    verifyAll()
  }

  @Test
  def shutsDownMultipleReceiversOverMultipleConnections() {
    val e1 = endpointWithAmqpUrl("foo1", "amqp://foo//queues/foo1")
    val e2 = endpointWithAmqpUrl("foo2", "amqp://foo//queues/foo2")
    val e3 = endpointWithAmqpUrl("bar1", "amqp://bar//queues/bar1")
    val e4 = endpointWithAmqpUrl("bar2", "amqp://bar//queues/bar2")

    inboundEndpointFactory.ensureEndpointReceiver(e1)
    inboundEndpointFactory.ensureEndpointReceiver(e2)

    assertEquals(1, connections.size)
    assertEquals(2, receivers.size)

    inboundEndpointFactory.ensureEndpointReceiver(e3)
    inboundEndpointFactory.ensureEndpointReceiver(e4)

    assertEquals(2, connections.size)
    assertEquals(4, receivers.size)

    val foo1 = receivers(0)
    val foo2 = receivers(1)
    val bar1 = receivers(2)
    val bar2 = receivers(3)

    expect(foo1.close())
    replayAll()
    inboundEndpointFactory.endpointGone(e1.space, e1.name)
    verifyAll()

    resetAll()

    expect(foo2.close())
    expect(connections(0).close())
    replayAll()
    inboundEndpointFactory.endpointGone(e2.space, e2.name)
    verifyAll()

    resetAll()

    expect(bar1.close())
    replayAll()
    inboundEndpointFactory.endpointGone(e3.space, e3.name)
    verifyAll()

    resetAll()

    expect(bar2.close())
    expect(connections(1).close())
    replayAll()
    inboundEndpointFactory.endpointGone(e4.space, e4.name)
    verifyAll()
  }
}