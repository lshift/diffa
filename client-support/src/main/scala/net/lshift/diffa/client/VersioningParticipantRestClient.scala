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

import com.sun.jersey.core.util.MultivaluedMapImpl
import net.lshift.diffa.kernel.participants._
import com.sun.jersey.api.client.ClientResponse
import org.apache.commons.io.IOUtils
import javax.ws.rs.core.MediaType
import net.lshift.diffa.participant.common.JSONHelper
import net.lshift.diffa.participant.correlation.ProcessingResponse

/**
 * JSON/REST versioning participant client.
 */
class VersioningParticipantRestClient(scanUrl:String)
    extends AbstractRestClient(scanUrl, "")
    with VersioningParticipantRef {


  def generateVersion(entityBody: String) = {
    val params = new MultivaluedMapImpl()
    params.add("body", entityBody)

    val formEndpoint = resource.`type`("application/x-www-form-urlencoded")
    val response = formEndpoint.post(classOf[ClientResponse], params)
    response.getStatus match {
      case 200 => JSONHelper.readProcessingResponse(response.getEntityInputStream)
      case _   =>
        log.error(response.getStatus + "")
        throw new Exception("Participant version generation failed: " + response.getStatus + "\n" + IOUtils.toString(response.getEntityInputStream, "UTF-8"))
    }
  }
}