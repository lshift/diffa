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

object AmqpQueueUrl {

  val DEFAULT_PORT = 5672
  val DEFAULT_USER = "guest"
  val DEFAULT_PASS = "guest"

  private val pattern = new Regex("""amqp://((.+):(.+)@)?(.*?)(:(\d+))?/(.*?)/queues/(.*?)""")

  def parse(url: String) = url match {
    case pattern(_, username, password, host, _, port, vHost, queue) =>
      AmqpQueueUrl(queue,
                   host,
                   if (port != null) port.toInt else DEFAULT_PORT,
                   vHost,
                   if (username != null) username else DEFAULT_USER,
                   if (password != null) password else DEFAULT_PASS)
    case _ =>
      throw new InvalidAmqpQueueUrlException(url)
  }
}

/**
 * Custom URL scheme for AMQP URLs. Extends existing AMQP URL scheme by adding a "queues" resource after the vhost section.
 */
case class AmqpQueueUrl(queue: String,
                        host: String = "localhost",
                        port: Int = AmqpQueueUrl.DEFAULT_PORT,
                        vHost: String = "",
                        username: String = AmqpQueueUrl.DEFAULT_USER,
                        password: String = AmqpQueueUrl.DEFAULT_PASS) {

  def isDefaultVHost = vHost.isEmpty

  private def portString = if (port == AmqpQueueUrl.DEFAULT_PORT) "" else ":%d".format(port)

  private def userInfoString =
    if (username == AmqpQueueUrl.DEFAULT_USER && password == AmqpQueueUrl.DEFAULT_PASS) "" else "%s:%s@".format(username, password)

  override def toString = "amqp://%s%s%s/%s/queues/%s".format(userInfoString,
                                                              host,
                                                              portString,
                                                              vHost,
                                                              queue)

}



case class InvalidAmqpQueueUrlException(url: String) extends RuntimeException {
  override def getMessage = "The given URL [%s] is not a valid AMQP queue URL".format(url)
}