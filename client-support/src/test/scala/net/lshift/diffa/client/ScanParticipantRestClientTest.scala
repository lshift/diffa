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

import org.easymock.EasyMock._
import net.lshift.diffa.kernel.config._
import org.junit.Test
import java.io.{InputStream, ByteArrayInputStream}
import org.junit.Assert._
import org.hamcrest.CoreMatchers._
import net.lshift.diffa.participant.scanning.{ScanConstraint, StringPrefixConstraint, ScanResultEntry}
import org.joda.time.DateTime
import net.lshift.diffa.kernel.participants.CategoryFunction
import java.net.{SocketTimeoutException, SocketException, ConnectException, URI}
import net.lshift.diffa.kernel.differencing.ScanFailedException
import net.lshift.diffa.kernel.config.DiffaPairRef
import net.lshift.diffa.kernel.config.QueryParameterCredentials
import net.lshift.diffa.kernel.config.BasicAuthCredentials
import net.lshift.diffa.kernel.participants.StringPrefixCategoryFunction
import org.junit.experimental.theories.{DataPoint, Theories, Theory}
import org.junit.runner.RunWith

class ScanParticipantRestClientTest {
  lazy val httpClient =  createMock(classOf[DiffaHttpClient])
  lazy val credentialsLookup = createMock(classOf[DomainCredentialsLookup])

  lazy val parser = createMock(classOf[JsonScanResultParser])
  val pair = DiffaPairRef("key", "domain")
  val scanUrl = "http://dummy/url"

  val JSON = "application/json"
  val nullAggregations: scala.Seq[CategoryFunction] = Seq()
  val nullConstraints: scala.Seq[ScanConstraint] = Seq()



  lazy val scanningParticipant = {
    new ScanParticipantRestClient(pair, scanUrl, credentialsLookup, httpClient, parser)
  }

  val emptyResponseContent = "[]" + " " * 40

  lazy val emptyResponse = new ByteArrayInputStream(emptyResponseContent.getBytes("UTF8"))


  lazy val nullQuery = Map[String, Seq[String]]()

  lazy val scanQuery = DiffaHttpQuery(scanUrl).
    accepting(JSON)

  lazy val sampleConstraints: Seq[ScanConstraint] = Seq(new StringPrefixConstraint("property", "thePrefix"))
  lazy val sampleAggregations: Seq[CategoryFunction] = Seq(new StringPrefixCategoryFunction("property", 1, 2, 3))

  val nullResponse = Right(emptyResponse): Either[Throwable, InputStream]

  @Test
  def participantShouldMakeGetRequestOnScan {

    expect(httpClient.get(scanQuery)).andReturn(nullResponse)
    replay(httpClient)
    expectingNullCredentials()

    scanningParticipant.scan(Seq(), Seq())
    verify(httpClient)
  }


  @Test
  def participantShouldMakeGetRequestWithAggregationsOnScan {
    val nullResponse = Right(emptyResponse): Either[Throwable, InputStream]
    val query = scanQuery.withAggregations(sampleAggregations).
      withConstraints(sampleConstraints)


    expect(httpClient.get(query)).andReturn(nullResponse)
    replay(httpClient)
    expectingNullCredentials()

    scanningParticipant.scan(sampleConstraints, sampleAggregations)
    verify(httpClient)
  }

  @Test
  def participantParsesResponse {
    expect(httpClient.get(scanQuery)).andStubReturn(Right(emptyResponse))
    expect(parser.parse(emptyResponse)).andReturn(Seq())
    replay(httpClient, parser)
    expectingNullCredentials()

    scanningParticipant.scan(nullConstraints, nullAggregations)
    verify(parser)
  }


  @Test
  def participantReturnsParsedResponse {
    val entities = Seq(ScanResultEntry.forEntity("id", "version", DateTime.now()))

    expect(httpClient.get(scanQuery)).andStubReturn(Right(emptyResponse))
    expect(parser.parse(emptyResponse)).andReturn(entities)
    replay(httpClient, parser)
    expectingNullCredentials()

    assertThat(scanningParticipant.scan(nullConstraints, nullAggregations),
      equalTo(entities))
  }


  @Test(expected= classOf[ScanFailedException])
  def shouldHandleConnectExceptionsAndRethrow {
    expectHttpError(new ConnectException())
    expectingNullCredentials()

    scanningParticipant.scan(nullConstraints, nullAggregations)
  }

  @Test(expected=classOf[ScanFailedException])
  def shouldHandleSocketExceptionsAndRethrow {
    expectHttpError(new SocketException())
    expectingNullCredentials()

    scanningParticipant.scan(nullConstraints, nullAggregations)
  }

  @Test(expected=classOf[ScanFailedException])
  def shouldHandleSocketTimeoutExceptionsAndRethrow {
    expectHttpError(new SocketTimeoutException())
    expectingNullCredentials()
    scanningParticipant.scan(nullConstraints, nullAggregations)
  }


  def expectHttpError(ex: Throwable) {
    expect(httpClient.get(scanQuery)).andStubReturn(Left(ex))
    replay(httpClient)
  }

  def expectingNullCredentials() : Unit = {
    expect(credentialsLookup.credentialsForUri(pair.domain, new URI(scanUrl))) andReturn(None)
    replay(credentialsLookup)
  }


  @Test
  def shouldQueryForAuthMechanism = {
    expect(credentialsLookup.credentialsForUri(pair.domain, new URI(scanUrl))) andReturn(None)
    expect(httpClient.get(anyObject())) andStubReturn(nullResponse)

    replay(credentialsLookup, httpClient)

    scanningParticipant.scan(nullConstraints, nullAggregations)
    verify(credentialsLookup)
  }
  @Test
  def itAddsQueryParameterCredentialsToTheRequest {
    val credentials = QueryParameterCredentials("fred",  "foobar")
    expect(credentialsLookup.credentialsForUri(pair.domain, new URI(scanUrl))) andReturn(Some(credentials))

    val expectedQuery = scanQuery.withQuery(Map(credentials.name -> Seq(credentials.value)))
    expect(httpClient.get(expectedQuery)) andReturn(nullResponse)

    replay(credentialsLookup, httpClient)

    scanningParticipant.scan(nullConstraints, nullAggregations)
  }

  @Test
  def itAddsBasicAuthToTheRequest {
    val credentials = BasicAuthCredentials("fred",  "foobar")
    expect(credentialsLookup.credentialsForUri(pair.domain, new URI(scanUrl))) andReturn(Some(credentials))

    val expectedQuery = scanQuery.withBasicAuth(credentials.username, credentials.password)
    expect(httpClient.get(expectedQuery)) andReturn(nullResponse)

    replay(credentialsLookup, httpClient)

    scanningParticipant.scan(nullConstraints, nullAggregations)
  }
}