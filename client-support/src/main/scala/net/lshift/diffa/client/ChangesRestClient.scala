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
    extends AbstractRestClient(serverRootUrl, "rest/domains/" + domain + "/changes/", params)
        with ChangesClient {

  def onChangeEvent(evt:ChangeEvent) {
    val baos = new ByteArrayOutputStream
    JSONHelper.writeChangeEvent(baos, evt)
    submit(endpoint, new String(baos.toByteArray, "UTF-8"))
  }
}