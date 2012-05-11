/**
 * Copyright (C) 2010-2012 LShift Ltd.
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

import java.net.URI
import scala.collection.JavaConversions._
import net.lshift.diffa.participant.scanning.ScanConstraint
import net.lshift.diffa.kernel.participants.CategoryFunction
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.{HttpConnectionParams, BasicHttpParams}
import org.apache.http.client.HttpClient
import net.lshift.diffa.kernel.util.AlertCodes._
import org.slf4j.LoggerFactory
import org.apache.http.auth.{UsernamePasswordCredentials, AuthScope}
import net.lshift.diffa.kernel.config._
import com.sun.jersey.core.util.MultivaluedMapImpl
import org.apache.http.client.methods.{HttpPost, HttpUriRequest, HttpGet}
import org.apache.http.client.entity.UrlEncodedFormEntity


/**
 * Abstract super class to create RESTful clients for usage within the agent.
 */
abstract class InternalRestClient(pair: DiffaPairRef,
                                  url: String,
                                  serviceLimitsView: PairServiceLimitsView,
                                  credentialsLookup:DomainCredentialsLookup) {

  private val logger = LoggerFactory.getLogger(getClass)

  protected val uri = new URI(url)

  protected def configureBasicAuth(httpClient:DefaultHttpClient, basic:BasicAuthCredentials) = {
    httpClient.getCredentialsProvider.setCredentials(
      new AuthScope(uri.getHost, uri.getPort),
      new UsernamePasswordCredentials(basic.username, basic.password)
    )
  }

  protected def maybeAuthenticate(prepareRequest:Option[QueryParameterCredentials] => HttpUriRequest) = {
    val httpClient = createHttpClient(new BasicHttpParams)

    val request = credentialsLookup.credentialsForUri(pair.domain, uri) match {
      case None        => prepareRequest(None)
      case Some(creds) => creds match {
        case query:QueryParameterCredentials  => prepareRequest(Some(query))
        case basic:BasicAuthCredentials       => {

          httpClient.getCredentialsProvider.setCredentials(
            new AuthScope(uri.getHost, uri.getPort),
            new UsernamePasswordCredentials(basic.username, basic.password)
          )

          prepareRequest(None)
        }
      }
    }

    (httpClient, request)
  }

  protected def constructQueryString(queryParams:MultivaluedMapImpl,
                                                credentials:Option[QueryParameterCredentials]) = {
    credentials match {
      case None        => // doesn't matter so don't worry about it
      case Some(param) => queryParams.add(param.name, param.value)
    }

    val qParams = {
      for (p <- queryParams.entrySet; v <- p.getValue)
      yield new BasicNameValuePair(p.getKey, v)
    }.toSeq

    URLEncodedUtils.format(qParams, "UTF-8")
  }

  protected def buildGetRequest(queryParams:MultivaluedMapImpl,
                                credentials:Option[QueryParameterCredentials]) = {

    val queryString = constructQueryString(queryParams, credentials)
    new HttpGet("%s?%s".format(url, queryString))

  }

  protected def buildPostRequest(queryParams:MultivaluedMapImpl,
                                 formParams:Map[String,String],
                                 credentials:Option[QueryParameterCredentials]) = {

    val queryString = constructQueryString(queryParams, credentials)

    val form = formParams.map{ case (k,v) => new BasicNameValuePair(k,v) }.toList
    val entity = new UrlEncodedFormEntity(form, "UTF-8")

    val httpPost = new HttpPost("%s?%s".format(url, queryString))
    httpPost.setEntity(entity)

    httpPost
  }

  protected def zeroIfUnlimited(limitName: String) = {
    serviceLimitsView.getEffectiveLimitByNameForPair(
      limitName, pair.domain, pair.key) match {
      case ServiceLimit.UNLIMITED => 0
      case timeout => timeout
    }
  }

  protected def createHttpClient(httpParams: BasicHttpParams): DefaultHttpClient = {
    HttpConnectionParams.setConnectionTimeout(httpParams,
      zeroIfUnlimited(ServiceLimit.SCAN_CONNECT_TIMEOUT_KEY))
    HttpConnectionParams.setSoTimeout(httpParams,
      zeroIfUnlimited(ServiceLimit.SCAN_READ_TIMEOUT_KEY))

    new DefaultHttpClient(httpParams)
  }

  protected def shutdownImmediate(client: HttpClient) {
    try {
      client.getConnectionManager.shutdown
    } catch {
      case e =>
        logger.warn("Could not shut down HTTP client: {} {}",
          Array[Object](formatAlertCode(ACTION_HTTP_CLEANUP_FAILURE), e.getClass, e.getMessage))
    }
  }

}
