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
import net.lshift.diffa.kernel.config.{ServiceLimit, DiffaPairRef, PairServiceLimitsView}
import org.apache.http.params.{HttpConnectionParams, BasicHttpParams}
import java.net.{ConnectException, SocketException, URI}
import net.lshift.diffa.kernel.differencing.ScanFailedException


/**
 * A ScanningParticipantRestClient is responsible for issuing scan queries to
 * Participants and mapping the JSON response to an Object.
 */
class ScanningParticipantRestClient(serviceLimitsView: PairServiceLimitsView,
                                    scanUrl: String,
                                    params: RestClientParams = RestClientParams.default,
                                    pair: DiffaPairRef = DiffaPairRef("", ""))
  extends ScanningParticipantRef {

  private val logger = LoggerFactory.getLogger(getClass)
  private final val ACCEPTABLE_STATUS_CODE = 200
  private val uri = new URI(scanUrl)

  /**
   * Issue a single query to a participant.
   *
   * @return upon successful receipt of a valid JSON response from the
   * participant which can be deserialized to a sequence of ScanResultEntry
   * objects, that sequence of objects is returned.
   * @throws ScanFailedException if normal exceptional conditions occur which
   * should be exposed via the UI.
   * @throws Exception if abnormal exceptional conditions occur which should
   * not be exposed via the UI.
   */
  def scan(constraints: Seq[ScanConstraint], aggregations: Seq[CategoryFunction]): Seq[ScanResultEntry] = {
    val httpClient = createHttpClient(new BasicHttpParams)
    val httpGet = setQueryParams(constraints, aggregations)

    try {
      val response = httpClient.execute(httpGet)

      val statusCode = response.getStatusLine.getStatusCode
      statusCode match {
        case ACCEPTABLE_STATUS_CODE =>
          JSONHelper.readQueryResult(response.getEntity.getContent)
        case _ =>
          logger.error("{} External scan error, response code: {}",
            Array(formatAlertCode(EXTERNAL_SCAN_ERROR), statusCode))
          throw new ScanFailedException("Participant scan failed: %s\n%s".format(
            statusCode, EntityUtils.toString(response.getEntity)))
      }
    } catch {
      case ex: ConnectException =>
        logger.error("%s Connection to %s refused".format(SCAN_CONNECTION_REFUSED, scanUrl))
        // NOTICE: ScanFailedException is handled specially (see its class documentation).
        throw new ScanFailedException("Could not connect to " + scanUrl)
      case ex: SocketException =>
        logger.error("Socket closed to %s closed".format(SCAN_CONNECTION_CLOSED, scanUrl))
        // NOTICE: ScanFailedException is handled specially (see its class documentation).
        throw new ScanFailedException("Connection to %s closed unexpectedly, query %s".format(
          scanUrl, uri.getQuery))
    } finally {
      shutdownImmediate(httpClient)
    }

  }

  def setQueryParams(constraints: Seq[ScanConstraint], aggregations: Seq[CategoryFunction]) = {
    val qryParams = new MultivaluedMapImpl
    RequestBuildingHelper.constraintsToQueryArguments(qryParams, constraints)
    RequestBuildingHelper.aggregationsToQueryArguments(qryParams, aggregations)

    val qParams = {
      for (p <- qryParams.entrySet; v <- p.getValue)
      yield new BasicNameValuePair(p.getKey, v)
    }.toSeq

    val paramString = URLEncodedUtils.format(qParams, "UTF-8")
    val httpGet = new HttpGet("%s?%s".format(scanUrl, paramString))
    httpGet
  }

  private def zeroIfUnlimited(limitName: String) = {
    serviceLimitsView.getEffectiveLimitByNameForPair(
      limitName, pair.domain, pair.key) match {
      case ServiceLimit.UNLIMITED => 0
      case timeout => timeout
    }
  }

  private def createHttpClient(httpParams: BasicHttpParams): DefaultHttpClient = {
    HttpConnectionParams.setConnectionTimeout(httpParams,
      zeroIfUnlimited(ServiceLimit.SCAN_CONNECT_TIMEOUT_KEY))
    HttpConnectionParams.setSoTimeout(httpParams,
      zeroIfUnlimited(ServiceLimit.SCAN_READ_TIMEOUT_KEY))

    val httpClient = new DefaultHttpClient(httpParams)

    httpClient.getCredentialsProvider.setCredentials(
      new AuthScope(uri.getHost, uri.getPort),
      new UsernamePasswordCredentials(params.username.get, params.password.get)
    )
    httpClient
  }

  private def shutdownImmediate(client: HttpClient) {
    try {
      client.getConnectionManager.shutdown
    } catch {
      case e =>
        logger.warn("Could not shut down HTTP client: {} {}",
          Array[Object](formatAlertCode(ACTION_HTTP_CLEANUP_FAILURE), e.getClass, e.getMessage))
    }
  }
}
