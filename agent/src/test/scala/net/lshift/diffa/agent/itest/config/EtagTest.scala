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
package net.lshift.diffa.agent.itest.config

import net.lshift.diffa.agent.itest.support.TestConstants._
import org.junit.Test
import net.lshift.diffa.kernel.frontend.{PairDef, EndpointDef}
import net.lshift.diffa.agent.client.ConfigurationRestClient
import org.apache.http.impl.client.{BasicAuthCache, DefaultHttpClient}
import org.apache.http.client.methods.HttpGet
import org.junit.Assert._
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.HttpHost
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.client.protocol.ClientContext

class EtagTest {

  val configClient = new ConfigurationRestClient(agentURL, defaultDomain)

  @Test
  def configChangeShouldUpgradeEtag {

    val up = EndpointDef(name = "some-upstream-endpoint")
    val down = EndpointDef(name = "some-downstream-endpoint")
    val pair = PairDef(key = "some-pair", upstreamName = up.name, downstreamName = down.name)

    val oldTag = getAggregatesEtag

    configClient.declareEndpoint(up)
    configClient.declareEndpoint(down)
    configClient.declarePair(pair)

    val newTag = getAggregatesEtag

    assertNotSame("Old etag was %s, new etag was %s".format(oldTag,newTag), oldTag, newTag)

  }

  private def getAggregatesEtag = {
    val targetHost = new HttpHost(agentHost, agentPort, "http")
    val httpClient = new DefaultHttpClient

    httpClient.getCredentialsProvider().setCredentials(
      new AuthScope(agentHost, agentPort),
      new UsernamePasswordCredentials(agentUsername, agentPassword))

    val httpResponse = httpClient.execute(
      new HttpGet(agentURL + "/%s/diffa/diffs/aggregates".format(domainsLabel)),
      basicAuthContext(targetHost))

    val etag = httpResponse.getLastHeader("ETag")
    httpResponse.getAllHeaders foreach { header =>
      println("Header [%s]".format(header))
    }
    httpClient.getConnectionManager.shutdown()
    etag.getValue
  }

  private def basicAuthContext(targetHost: HttpHost): BasicHttpContext = {
    val authCache = new BasicAuthCache
    val basicAuth = new BasicScheme
    authCache.put(targetHost, basicAuth)

    val context = new BasicHttpContext
    context.setAttribute(ClientContext.AUTH_CACHE, authCache)
    context
  }
}
