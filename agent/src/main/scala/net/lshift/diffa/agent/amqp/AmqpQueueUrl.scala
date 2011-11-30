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

import scala.util.matching.Regex
import com.rabbitmq.client.ConnectionFactory.{DEFAULT_USER, DEFAULT_PASS, USE_DEFAULT_PORT}

/**
 * Custom URL scheme for AMQP URLs. Extends existing AMQP URL scheme by adding a "queues" resource after the vhost section.
 */
case class AmqpQueueUrl(queue: String,
                        host: String = "localhost",
                        port: Int = USE_DEFAULT_PORT,
                        vHost: String = "",
                        username: String = DEFAULT_USER,
                        password: String = DEFAULT_PASS) {

  def isDefaultVHost = vHost.isEmpty

  private def portString = if (port == USE_DEFAULT_PORT) "" else ":%d".format(port)

  private def userInfoString =
    if (username == DEFAULT_USER && password == DEFAULT_PASS) "" else "%s:%s@".format(username, password)

  override def toString = "amqp://%s%s%s/%s/queues/%s".format(userInfoString,
                                                              host,
                                                              portString,
                                                              vHost,
                                                              queue)

}

object AmqpQueueUrl {

  private val pattern = new Regex("""amqp://((.+):(.+)@)?(.*?)(:(\d+))?/(.*?)/queues/(.*?)""")

  def parse(url: String) = url match {
    case pattern(_, username, password, host, _, port, vHost, queue) =>
      AmqpQueueUrl(queue,
                   host,
                   if (port != null) port.toInt else USE_DEFAULT_PORT,
                   vHost,
                   if (username != null) username else DEFAULT_USER,
                   if (password != null) password else DEFAULT_PASS)
    case _ =>
      throw new InvalidAmqpQueueUrlException(url)
  }
}

case class InvalidAmqpQueueUrlException(url: String) extends RuntimeException {
  override def getMessage = "The given URL [%s] is not a valid AMQP queue URL".format(url)
}