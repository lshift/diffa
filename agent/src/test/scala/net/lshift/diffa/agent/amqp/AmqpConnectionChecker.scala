package net.lshift.diffa.agent.amqp

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

import com.rabbitmq.client.ConnectionFactory
import org.slf4j.LoggerFactory

/**
 * Utility object that can detect whether a RabbitMQ connection is available
 * on localhost, using the default port number.
 *
 * Example usage: assumeTrue(AmqpConnectionChecker.isConnectionAvailable)
 *
 */
object AmqpConnectionChecker {

  val log = LoggerFactory.getLogger(AmqpConnectionChecker.getClass)

  val isConnectionAvailable = try {
    log.info("Checking for AMQP connection")
    val cf = new ConnectionFactory
    val conn = cf.newConnection()

    log.info("AMQP server properties: " + conn.getServerProperties)
    try {
      conn.close
    } catch {
      case e =>
        log.error("Couldn't close AMQP connection after checking", e)
    }

    true
  } catch {
    case e =>
      println("*" * 80)
      println("No AMQP server running on localhost - tests that require AMQP will not be run!")
      println("*" * 80)
      false
  }
}