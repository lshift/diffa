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

import net.lshift.diffa.participant.scanning.{ScanResultEntry, ScanConstraint}
import net.lshift.diffa.kernel.util.AlertCodes._
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.HttpClient
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import net.lshift.diffa.participant.common.JSONHelper
import org.apache.http.util.EntityUtils
import com.sun.jersey.core.util.MultivaluedMapImpl
import net.lshift.diffa.kernel.participants.{ScanningParticipantRef, CategoryFunction}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.auth.{UsernamePasswordCredentials, AuthScope}
import org.apache.http.params.{HttpConnectionParams, BasicHttpParams}
import java.net.{ConnectException, SocketException, URI}
import net.lshift.diffa.kernel.config._
import limits.ScanResponseSizeLimit
import org.apache.http.HttpResponse
import java.io.{IOException, InputStream}
import net.lshift.diffa.kernel.differencing.{ScanLimitBreachedException, ScanFailedException}


/**
 * A ScanningParticipantRestClient is responsible for issuing scan queries to
 * Participants and mapping the JSON response to an Object.
 */
class ScanningParticipantRestClient(pair: DiffaPairRef,
                                    scanUrl: String,
                                    serviceLimitsView: PairServiceLimitsView,
                                    credentialsLookup: DomainCredentialsLookup)
  extends InternalRestClient(pair, scanUrl, serviceLimitsView, credentialsLookup)
  with ScanningParticipantRef {

  private val log = LoggerFactory.getLogger(getClass)

  /**
   * Issue a single query to a participant.
   *
   * @return upon successful receipt of a valid JSON response from the
   * participant which can be deserialized to a sequence of ScanResultEntry
   * objects, that sequence of objects is returned.
   * @throws ScanFailedException if normal exceptional conditions occur which
   * should be exposed via the UI.
   * @throws ScanLimitBreached if we go over a scan limit, such as response
   * size.
   * @throws Exception if abnormal exceptional conditions occur which should
   * not be exposed via the UI.
   */
  def scan(constraints: Seq[ScanConstraint], aggregations: Seq[CategoryFunction]): Seq[ScanResultEntry] = {

    val params = new MultivaluedMapImpl

    RequestBuildingHelper.constraintsToQueryArguments(params, constraints)
    RequestBuildingHelper.aggregationsToQueryArguments(params, aggregations)

    def prepareRequest(query:Option[QueryParameterCredentials]) = buildGetRequest(params, query)
    val (httpClient, httpGet) = maybeAuthenticate(prepareRequest)

    try {
      val response = httpClient.execute(httpGet)

      val statusCode = response.getStatusLine.getStatusCode
      statusCode match {
        case 200 => handleJsonResponse(response)
        case _   =>
          log.error("{} External scan error, response code: {}",
            Array(formatAlertCode(EXTERNAL_SCAN_ERROR), statusCode))
          throw new ScanFailedException("Participant scan failed: %s\n%s".format(
            statusCode, EntityUtils.toString(response.getEntity)))
      }
    } catch {
      case ex: ConnectException =>
        log.error("%s Connection to %s refused".format(SCAN_CONNECTION_REFUSED, scanUrl))
        // NOTICE: ScanFailedException is handled specially (see its class documentation).
        throw new ScanFailedException("Could not connect to " + scanUrl)
      case ex: SocketException =>
        log.error("Socket closed to %s closed".format(SCAN_CONNECTION_CLOSED, scanUrl))
        // NOTICE: ScanFailedException is handled specially (see its class documentation).
        throw new ScanFailedException("Connection to %s closed unexpectedly, query %s".format(
          scanUrl, uri.getQuery))
    } finally {
      shutdownImmediate(httpClient)
    }
  }

  def handleJsonResponse(response: HttpResponse) : Seq[ScanResultEntry] = {
    val responseSizeLimit = serviceLimitsView.getEffectiveLimitByNameForPair(
      pair.domain, pair.key, ScanResponseSizeLimit)

    val responseStream = response.getEntity.getContent
    val countedInputStream = new InputStream {
      var numBytes = 0;
      def read() = {
        val byte = responseStream.read()
        numBytes += 1

        if (numBytes > responseSizeLimit) {
          val msg = "Scan response size for pair %s exceeded configured limit of %d bytes".format(
            pair.key, responseSizeLimit)

          throw new IOException(msg, new ScanLimitBreachedException(msg))
        }

        byte
      }
    }

    try {
      JSONHelper.readQueryResult(countedInputStream)
    } catch { case e:IOException =>
      e.getCause match {
        case scanError : ScanLimitBreachedException => throw scanError
        case _ => throw e
      }
    }
  }
}
