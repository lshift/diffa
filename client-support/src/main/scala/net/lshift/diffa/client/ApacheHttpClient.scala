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

import org.apache.http.client.HttpResponseException
import org.apache.http.impl.client.{BasicAuthCache, DefaultHttpClient}
import org.apache.http.client.methods.HttpGet
import org.apache.http.auth.{UsernamePasswordCredentials, AuthScope}
import org.apache.http.params.{HttpConnectionParams, BasicHttpParams}
import org.slf4j.LoggerFactory
import org.apache.http.HttpHost
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.client.protocol.ClientContext

class ApacheHttpClient(connectionTimeout: Int,
                        socketTimeout: Int) extends DiffaHttpClient {

  private val logger = LoggerFactory.getLogger(getClass)

  lazy val client = {
    val httpParams = new BasicHttpParams
    HttpConnectionParams.setConnectionTimeout(httpParams,connectionTimeout)
    HttpConnectionParams.setSoTimeout(httpParams, socketTimeout)
    new DefaultHttpClient(httpParams)
  }

  private def basicAuthContext(targetHost: HttpHost): BasicHttpContext = {
    val authCache = new BasicAuthCache
    val basicAuth = new BasicScheme
    authCache.put(targetHost, basicAuth)

    val context = new BasicHttpContext
    context.setAttribute(ClientContext.AUTH_CACHE, authCache)
    context
  }

  override def get(r : DiffaHttpQuery) = {
    val req = new HttpGet(r.fullUri)
    r.basicAuth.foreach { case (user, pass) =>
      client.getCredentialsProvider.setCredentials(
        new AuthScope(r.fullUri.getHost, r.fullUri.getPort),
        new UsernamePasswordCredentials(user, pass))
      debugLog("Set credentials: %s/%s", user, pass)
    }

    val uri = req.getURI
    val targetHost = new HttpHost(uri.getHost, uri.getPort, uri.getScheme)
    debugLog("Request: %s", req.getURI)
    try {
      val resp = client.execute(req, basicAuthContext(targetHost))
      debugLog("Statusline for %s: %s ", req.getURI, resp.getStatusLine.getStatusCode.toString)

      resp.getStatusLine.getStatusCode match {
        case code: Int if (200 to 299) contains code => Right(resp.getEntity.getContent)
        case code =>
          resp.getEntity.getContent.close()
          logger.warn("Query for URI: %s returned %s", resp.getStatusLine)
          Left(new HttpResponseException(code, resp.getStatusLine.getReasonPhrase))
      }

    } catch {
      case e: Throwable => Left(e)
    }
  }

  private def debugLog(format: String, args: AnyRef*) = {
    if (logger.isDebugEnabled)
      logger.debug(format, args)
  }

}
