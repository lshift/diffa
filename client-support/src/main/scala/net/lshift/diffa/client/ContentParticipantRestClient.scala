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
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.util.AlertCodes._
import org.apache.http.util.EntityUtils
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.config._

/**
 * JSON/REST content adapter client.
 */
class ContentParticipantRestClient(pair: PairRef,
                                   scanUrl: String,
                                   serviceLimitsView: PairServiceLimitsView,
                                   credentialsLookup:DomainCredentialsLookup)
  extends InternalRestClient(pair, scanUrl, serviceLimitsView, credentialsLookup) {

  val log = LoggerFactory.getLogger(getClass)

  def retrieveContent(identifier: String) = {

    val params = new MultivaluedMapImpl()
    params.add("identifier", identifier)

    def prepareRequest(query:Option[QueryParameterCredentials]) = buildGetRequest(params, query)
    val (httpClient, httpGet) = maybeAuthenticate(prepareRequest)

    try {
      val response = httpClient.execute(httpGet)
      response.getStatusLine.getStatusCode match {
        case 200 => EntityUtils.toString(response.getEntity)
        case 404 => throw new MissingObjectException(identifier)
        case _   =>
          log.error("%s - %s".format(formatAlertCode(pair, CONTENT_RETRIEVAL_FAILED), EntityUtils.toString(response.getEntity)))
          throw new Exception("Participant content retrieval failed")
      }
    }
    finally {
      shutdownImmediate(httpClient)
    }
  }
}