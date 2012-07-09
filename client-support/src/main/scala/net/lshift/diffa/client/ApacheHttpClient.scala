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

import org.apache.http.client.{HttpResponseException, HttpClient}
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet
import java.net.URI
import org.apache.http.auth.{UsernamePasswordCredentials, AuthScope}
import org.apache.http.params.{HttpConnectionParams, BasicHttpParams}
import org.slf4j.LoggerFactory

class ApacheHttpClient(connectionTimeout: Int,
                        socketTimeout: Int) extends DiffaHttpClient {

  private val logger = LoggerFactory.getLogger(getClass)

  lazy val client = {
    val httpParams = new BasicHttpParams
    HttpConnectionParams.setConnectionTimeout(httpParams,connectionTimeout)
    HttpConnectionParams.setSoTimeout(httpParams, socketTimeout)
    new DefaultHttpClient(httpParams)
  }

  override def get(r : DiffaHttpQuery) = {
    val req = new HttpGet(r.fullUri)
    r.basicAuth.foreach { case (user, pass) =>
      client.getCredentialsProvider.setCredentials(
        new AuthScope(r.fullUri.getHost, r.fullUri.getPort),
        new UsernamePasswordCredentials(user, pass))
      debugLog("Set credentials: %s/%s", user, pass)
    }

    debugLog("Request: %s", req.getURI)
    try {
      val resp = client.execute(req)
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
