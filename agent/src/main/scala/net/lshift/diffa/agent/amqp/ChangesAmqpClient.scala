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

import net.lshift.diffa.kernel.client.ChangesClient
import org.slf4j.LoggerFactory
import net.lshift.diffa.participant.changes.ChangeEvent
import java.io.ByteArrayOutputStream
import net.lshift.diffa.participant.common.JSONHelper
import net.lshift.accent.AccentConnection

/**
 * RPC client wrapper for clients to report change events to the diffa agent using JSON over AMQP.
 */
class ChangesAmqpClient(con: AccentConnection,
                        queueName: String,
                        timeout: Long)

  extends AccentSender(con, queueName)
  with ChangesClient {

  private val log = LoggerFactory.getLogger(getClass)

  def onChangeEvent(evt: ChangeEvent) = send(JSONHelper.writeChangeEvent(evt))

  def inboundURL = AmqpQueueUrl(queueName).toString
}
