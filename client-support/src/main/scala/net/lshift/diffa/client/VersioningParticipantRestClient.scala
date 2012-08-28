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
import org.apache.http.util.EntityUtils
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.util.AlertCodes._
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.config._

/**
 * JSON/REST versioning participant client.
 */
class VersioningParticipantRestClient(pair: PairRef,
                                      scanUrl: String,
                                      serviceLimitsView: PairServiceLimitsView,
                                      credentialsLookup:DomainCredentialsLookup)
  extends InternalRestClient(pair, scanUrl, serviceLimitsView, credentialsLookup)
  with VersioningParticipantRef {

  val log = LoggerFactory.getLogger(getClass)

  def generateVersion(entityBody: String) = {

    val queryParams = new MultivaluedMapImpl()
    val formParams = Map("body" -> entityBody)

    def prepareRequest(query:Option[QueryParameterCredentials]) = buildPostRequest(queryParams, formParams, query)
    val (httpClient, httpPost) = maybeAuthenticate(prepareRequest)

    try {
      val response = httpClient.execute(httpPost)
      response.getStatusLine.getStatusCode match {
        case 200 => JSONHelper.readProcessingResponse(response.getEntity.getContent)
        case _   =>
          log.error("%s - %s".format(formatAlertCode(pair, VERSION_GENERATION_FAILED), EntityUtils.toString(response.getEntity)))
          throw new Exception("Participant version generation failed")
      }
    }
    finally {
      shutdownImmediate(httpClient)
    }

  }
}