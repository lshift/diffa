/**
 * Copyright (C) 2010-2011 LShift Ltd.
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

import com.sun.jersey.core.util.MultivaluedMapImpl
import java.net.URLDecoder
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.schema.servicelimits.ServiceLimit
import net.lshift.diffa.participant.scanning.{ScanningParticipantDelegator, ScanningParticipantHandler}
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.{Request, Server}
import org.junit.runner.RunWith
import org.junit.{Before, Test}
import org.junit.Assert._
import org.junit.experimental.theories.{DataPoints, Theories, DataPoint, Theory}

@RunWith(classOf[Theories])
class InternalRestClientTest {

  import InternalRestClientTest._

  @Theory
  def shouldBeAbleToCorrectlyAddQueryStringToBaseUrlWithQueryString(example: (String, Example)) =
    example match { case (rootUrl, ex) =>

      val query = new MultivaluedMapImpl()
      for ((k, v) <- ex.query) query.add(k, v)
      val client = clientFor(rootUrl, ex.requestUrl)

      assertEquals(rootUrl + ex.expected,
        client.queryRequestUrlFor(query, None))
  }

  @Theory
  def shouldBeAbleToCorrectlyAddQueryStringToBaseUrlWithQueryStringForSubmission(example: (String, Example)) =
    example match { case (rootUrl, ex) =>
      val query = new MultivaluedMapImpl()
      for ((k, v) <- ex.query) query.add(k, v)
      val client = clientFor(rootUrl, ex.requestUrl)

      assertEquals(rootUrl + ex.expected,
        client.querySubmissionUrlFor(query, None))
  }
}

object InternalRestClientTest {
  val rootUrl:String =  null
  case class Example(requestUrl: String,
                     query: Map[String, String],
                     expected: String)

  def nullExample = Example("/", Map(), "/")

  def simpleExample = Example("/", Map("dummy" -> "value"), "/?dummy=value")

  def baseWithAuth = Example("/?auth=dummy", Map(), "/?auth=dummy")

  def baseWithAuthPlusQuery = Example(
    "/?auth=dummy", Map("query" -> "value"), "/?auth=dummy&query=value")

  val pair = new DiffaPairRef("some-domain", "some-pair")
  val domainCredentialsLookup = new FixedDomainCredentialsLookup(pair.domain, None)
  val limits = new PairServiceLimitsView {
    def getEffectiveLimitByNameForPair(domainName: String, pairKey: String, limit:ServiceLimit): Int = limit.defaultLimit
  }
  val protocols = List("http", "https")
  val authorities = List("localhost", "localhost:534")

  @DataPoints
  def data = {
    val cases = for {
      protocol <- protocols
      authority <- authorities
      example <- List(nullExample, simpleExample, baseWithAuth, baseWithAuthPlusQuery)
    } yield ("%s://%s".format(protocol, authority), example)
    cases.toArray
  }

  def clientFor(baseUrl: String, reqUrl:String) = new InternalRestClient(pair, baseUrl + reqUrl, limits, domainCredentialsLookup) {
    def queryRequestUrlFor(queryParams: MultivaluedMapImpl,
                           credentials: Option[QueryParameterCredentials]) = {
      buildGetRequest(queryParams, credentials).getURI().toString

    }

    def querySubmissionUrlFor(queryParams: MultivaluedMapImpl,
                           credentials: Option[QueryParameterCredentials]) = {
      buildPostRequest(queryParams, Map(), credentials).getURI().toString

    }

  }


}