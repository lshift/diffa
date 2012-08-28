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

import org.apache.commons.io.IOUtils
import org.junit.Assert._
import org.junit.Assume.assumeTrue
import org.junit.Test
import net.lshift.accent.AccentConnection
import com.rabbitmq.client.ConnectionFactory
import com.eaio.uuid.UUID
import net.lshift.diffa.kernel.frontend.Changes
import net.lshift.diffa.participant.changes.ChangeEvent
import net.lshift.diffa.participant.common.JSONHelper
import org.joda.time.{DateTimeZone, DateTime}

class AmqpProducerConsumerTest {

  assumeTrue(AmqpConnectionChecker.isConnectionAvailable)

  val factory = new ConnectionFactory()
  val failureHandler = new AccentConnectionFailureHandler()

  @Test
  def receiverShouldBeAbleToProcessMessage() {
    val con = new AccentConnection(factory, failureHandler)

    val monitor = new Object
    var messageProcessed = false

    val params = new ReceiverParameters(new UUID().toString) {
      autoDelete = true;
    }

    val expectedEvent = ChangeEvent.forChange("id", "version", new DateTime().withZone(DateTimeZone.UTC))

    new AccentReceiver(con, params, 1L, "endpoint", new Changes(null,null,null,null) {

      override def onChange(space:Long, endpoint:String, evt:ChangeEvent) {
        assertEquals(expectedEvent, evt)
        messageProcessed = true
        monitor.synchronized {
          monitor.notifyAll
        }
      }

    })
    
    val producer = new AccentSender(con, params.queueName)

    producer.send(JSONHelper.writeChangeEvent(expectedEvent))

    monitor.synchronized {
      monitor.wait(5000)
    }

    assertTrue(messageProcessed)
  }

  //@Test(timeout = 15000)
  def applicationExceptionsShouldBeRejected() = {

    val con = new AccentConnection(factory, failureHandler)
    val monitor = new Object

    val params = new ReceiverParameters(queueName = new UUID().toString) {
      autoDelete = false;
    }


    val receiver = new AccentReceiver(con, params, 2L, "endpoint", new Changes(null,null,null,null) {

      override def onChange(space:Long, endpoint:String, evt:ChangeEvent) {
        monitor.synchronized {
          monitor.notifyAll()
        }
        throw new Exception("Deliberate exception")
      }

    })

    val producer = new AccentSender(con, params.queueName)

    val event = ChangeEvent.forChange("id", "version", new DateTime)
    producer.send(JSONHelper.writeChangeEvent(event))

    monitor.synchronized {
      monitor.wait(1000)
    }

    receiver.close()

    // TODO The call to con.close/0 _appears_ to be necessary to make sure that when you try to deliberately
    // break the test by commenting out the call to reject the message, the new channel
    // created below can see the unrejected message, and hence the test fails
    con.close()


    val channel = factory.newConnection().createChannel()

    try {
      assertNull(channel.basicGet(params.queueName, false))
    }
    finally {

      // Make sure that the queue is disposed of

      channel.queueDelete(params.queueName)
      channel.close()
    }
  }
}