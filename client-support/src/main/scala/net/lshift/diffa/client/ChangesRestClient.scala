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

package net.lshift.diffa.client

import net.lshift.diffa.kernel.client.ChangesClient
import net.lshift.diffa.participant.changes.ChangeEvent
import java.io.ByteArrayOutputStream
import net.lshift.diffa.participant.common.JSONHelper
import java.lang.Long

/**
 * JSON-over-REST client for the changes endpoint.
 */
class ChangesRestClient(serverRootUrl:String, domain:String, endpoint:String, params: RestClientParams = RestClientParams.default)
    extends ExternalRestClient(serverRootUrl, "domains/" + domain + "/changes/", params)
        with ChangesClient {

  def onChangeEvent(evt:ChangeEvent) {
    val baos = new ByteArrayOutputStream
    JSONHelper.writeChangeEvent(baos, evt)
    val response = submit(endpoint, new String(baos.toByteArray, "UTF-8"))
    response.getStatus match {
      case 202 => // This is fine, just continue
      case 400 => throw new InvalidChangeEventException(evt)
      case 420 => throw new RateLimitExceededException(evt)
      case x   => throw new Exception("Got %s response for event %s".format(x, evt))
    }
  }
}
// Denotes an invalid change event
class InvalidChangeEventException(e:ChangeEvent) extends Exception(e.toString)

// The agent refused to process a change event because the submission rate exceeded the allowed limit.
class RateLimitExceededException(e: ChangeEvent) extends Exception(e.toString)
