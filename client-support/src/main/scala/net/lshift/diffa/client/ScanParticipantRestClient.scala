/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.client

import net.lshift.diffa.participant.scanning.{ScanResultEntry, ScanConstraint}
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.kernel.participants.{CategoryFunction, ScanningParticipantRef}
import org.slf4j.LoggerFactory
import java.net.{SocketTimeoutException, SocketException, ConnectException, URI}
import net.lshift.diffa.kernel.differencing.ScanFailedException
import net.lshift.diffa.kernel.util.AlertCodes._
import net.lshift.diffa.kernel.config.PairRef
import net.lshift.diffa.kernel.config.QueryParameterCredentials
import net.lshift.diffa.kernel.config.BasicAuthCredentials
import scala.Some
import org.apache.http.NoHttpResponseException

class ScanParticipantRestClient(pair: PairRef,
                                scanUrl: String,
                                credentialsLookup: DomainCredentialsLookup,
                                httpClient: DiffaHttpClient,
                                parser: JsonScanResultParser)
  extends ScanningParticipantRef {

  private val log = LoggerFactory.getLogger(getClass)

  def scan(constraints: Seq[ScanConstraint], aggregations: Seq[CategoryFunction]) : Seq[ScanResultEntry] = {
    val query = DiffaHttpQuery(scanUrl).accepting("application/json").
      withConstraints(constraints).
      withAggregations(aggregations)

    val credentials = credentialsLookup.credentialsForUri(pair.space, new URI(scanUrl))

    val queryWithCredentials = credentials match {
      case None => query
      case Some(BasicAuthCredentials(user, password)) => query.withBasicAuth(user, password)
      case Some(QueryParameterCredentials(name, value)) => query.withQuery(Map(name -> Seq(value)))
    }

    try {
      this.httpClient.get(queryWithCredentials, parser)
    } catch {
      case ex => handleHttpError(ex, queryWithCredentials)
    }
  }

  def handleHttpError(ex: Throwable, query: DiffaHttpQuery) = ex match {
    case ex: ConnectException =>
      log.error("%s Connection to %s refused".format(SCAN_CONNECTION_REFUSED, scanUrl))
      // NOTICE: ScanFailedException is handled specially (see its class documentation).
      throw new ScanFailedException("Could not connect to " + scanUrl)
    case ex: SocketException =>
      log.error("%s Socket closed to %s".format(SCAN_CONNECTION_CLOSED, scanUrl))
      // NOTICE: ScanFailedException is handled specially (see its class documentation).
      throw new ScanFailedException("Connection to %s closed unexpectedly, query %s".format(
        scanUrl, query.query))
    case ex: SocketTimeoutException =>
      log.error("%s Socket time out for %s".format(SCAN_SOCKET_TIMEOUT, scanUrl))
      // NOTICE: ScanFailedException is handled specially (see its class documentation).
      throw new ScanFailedException("Socket to %s timed out unexpectedly, query %s".format(
        scanUrl, query.query))
    case ex => throw ex
  }
}


