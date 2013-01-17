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

import org.apache.http.impl.client.{DefaultHttpRequestRetryHandler, BasicAuthCache, DefaultHttpClient}
import org.apache.http.client.methods.HttpGet
import org.apache.http.auth.{UsernamePasswordCredentials, AuthScope}
import org.apache.http.params.{HttpConnectionParams, BasicHttpParams}
import org.slf4j.LoggerFactory
import org.apache.http.{NoHttpResponseException, HttpResponse, HttpHost}
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.client.protocol.ClientContext
import net.lshift.diffa.kernel.util.AlertCodes._
import net.lshift.diffa.kernel.util.AlertCodes

import org.apache.commons.io.IOUtils

class ApacheHttpClient(connectionTimeout: Int,
                        socketTimeout: Int) extends DiffaHttpClient {

  private val logger = LoggerFactory.getLogger(getClass)

  def newClient = {
    val httpParams = new BasicHttpParams
    HttpConnectionParams.setConnectionTimeout(httpParams,connectionTimeout)
    HttpConnectionParams.setSoTimeout(httpParams, socketTimeout)
    val client = new DefaultHttpClient(httpParams)
    client.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
    client
  }

  private def basicAuthContext(targetHost: HttpHost): BasicHttpContext = {
    val authCache = new BasicAuthCache
    val basicAuth = new BasicScheme
    authCache.put(targetHost, basicAuth)

    val context = new BasicHttpContext
    context.setAttribute(ClientContext.AUTH_CACHE, authCache)
    context
  }

  override def get(r : DiffaHttpQuery, parser: JsonScanResultParser) = {
    val client = newClient
    val req = new HttpGet(r.fullUri)
    r.basicAuth.foreach { case (user, pass) =>
      client.getCredentialsProvider.setCredentials(
        new AuthScope(r.fullUri.getHost, r.fullUri.getPort),
        new UsernamePasswordCredentials(user, pass))
    }

    val uri = req.getURI
    val targetHost = new HttpHost(uri.getHost, uri.getPort, uri.getScheme)
    var resp: HttpResponse = null
    try {
      resp = client.execute(req, basicAuthContext(targetHost))

      resp.getStatusLine.getStatusCode match {
        case code: Int if (200 to 299) contains code =>
          parser.parse(resp.getEntity.getContent)
        case code =>
          logger.warn("%s - Query for URI: %s returned %s".format(
            formatAlertCode(AlertCodes.EXTERNAL_SCAN_ERROR), r.fullUri, resp.getStatusLine))
          throw new RuntimeException("%d - %s".format(code, resp.getStatusLine.getReasonPhrase))
      }
    } catch {
      case x:NoHttpResponseException =>

        val content = try {
          "content received: " + IOUtils.toString(resp.getEntity.getContent)
        } catch {
          case _ => "no content received from remote peer"
        }

        logger.error("%s Non HTTP response from %s; %s".format(NON_HTTP_RESPONSE, uri, content))
        throw new RuntimeException("Non HTTP response from " + uri)
    }
    // This finally block is very important, so don't nuke it, otherwise the client will leak
    finally {
      try {
        resp.getEntity.getContent.close()
      } catch {
        case _ =>
      }
      client.getConnectionManager.shutdown()
    }
  }
}
