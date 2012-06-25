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

import net.lshift.accent.ConnectionFailureListener
import java.lang.Exception
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.util.AlertCodes
import java.net.ConnectException

/**
 * Default handler for listening to failures in Accent connections.
 */
class AccentConnectionFailureHandler extends ConnectionFailureListener {

  val log = LoggerFactory.getLogger(getClass)

  def connectionFailure(x: Exception) = x match {
    case c:ConnectException => {
      log.warn("%s: Accent connection failure: %s".format(AlertCodes.GENERAL_MESSAGING_ERROR, x.getMessage))
    }
    case e => {
      log.error("%s: Accent error".format(AlertCodes.GENERAL_MESSAGING_ERROR), e)
    }
  }
}