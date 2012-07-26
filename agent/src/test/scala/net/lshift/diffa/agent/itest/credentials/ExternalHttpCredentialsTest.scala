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
package net.lshift.diffa.agent.itest.credentials

import net.lshift.diffa.agent.itest.support.TestConstants._
import net.lshift.diffa.agent.itest.IsolatedDomainTest

import net.lshift.diffa.participants.{QueryParameterAuthenticationMechanism, AuthenticationMechanism}
import net.lshift.diffa.agent.client.{ConfigurationRestClient, ScanningRestClient, CredentialsRestClient}
import net.lshift.diffa.participants.{BasicAuthenticationMechanism, ParticipantRpcServer}
import javax.servlet.http.HttpServletRequest
import com.eaio.uuid.UUID
import java.util.List
import net.lshift.diffa.agent.itest.support.ScanningHelper
import net.lshift.diffa.kernel.differencing.PairScanState
import scala.collection.JavaConversions._
import net.lshift.diffa.participant.scanning._
import net.lshift.diffa.kernel.frontend.{InboundExternalHttpCredentialsDef, PairDef, EndpointDef}
import net.lshift.diffa.kernel.config.ExternalHttpCredentials
import org.junit.experimental.theories.{DataPoints, Theories, Theory}
import org.junit.runner.RunWith
import scala.collection.JavaConversions._
import org.junit.Before
import org.apache.commons.lang.RandomStringUtils

@RunWith(classOf[Theories])
class ExternalHttpCredentialsTest extends IsolatedDomainTest {
  import ExternalHttpCredentialsTest._

  val credentialsClient = new CredentialsRestClient(agentURL, isolatedDomain)
  val scanClient = new ScanningRestClient(agentURL, isolatedDomain)
  val configClient = new ConfigurationRestClient(agentURL, isolatedDomain)
  var pair: String = ""

  @Before
  override def setup {
    super.setup
    // Make that the creds do not exist the DB before this test is run (e.g. from a previous test run)
    try {
      credentialsClient.deleteCredentials(baseUrl)
    }
    catch {
      case _ => // ignore
    }

    val up = RandomStringUtils.randomAlphanumeric(10)
    val down = RandomStringUtils.randomAlphanumeric(10)
    pair = RandomStringUtils.randomAlphanumeric(10)

    configClient.declareEndpoint(EndpointDef(name = up, scanUrl = scanUrl))
    configClient.declareEndpoint(EndpointDef(name = down, scanUrl = scanUrl))
    configClient.declarePair(PairDef(pair, "same", 1, up, down))
  }

  @Theory
  def scanShouldFailWithoutCredentials(scenario: Scenario) = scenario match {
    case NoCredsScenario(authMechanism) =>
      // Given (no client credentials)

      withRunningServer(newServer(authMechanism), {
        // When
        scanClient.startScan(pair)
        // Then
        ScanningHelper.waitForScanStatus(scanClient, pair, PairScanState.FAILED)
      })
    case _ =>
  }

  @Theory
  def scanShouldSucceedWithMatchingCredentials(scenario: Scenario) = scenario match {
    case CredsMatchScenario(clientCreds, authMechanism) =>
      // Given
      credentialsClient.addCredentials(clientCreds)

      withRunningServer(newServer(authMechanism), {
        // When
        scanClient.startScan(pair)
        // Then
        ScanningHelper.waitForScanStatus(scanClient, pair, PairScanState.UP_TO_DATE)
      })
    case _ =>
  }

  @Theory
  def scanShouldFailWithMismatchingCredentials(scenario: Scenario) = scenario match {
    case CredsTypeMismatchScenario(clientCreds, authMechanism) =>
      // Given invalid credentials (not matching server requirements)
      credentialsClient.addCredentials(clientCreds)

      withRunningServer(newServer(authMechanism), {
        // When
        scanClient.startScan(pair)
        // Then
        ScanningHelper.waitForScanStatus(scanClient, pair, PairScanState.FAILED)
      })
    case _ =>
  }

  private def withRunningServer(server: ParticipantRpcServer, verify: => Unit) {
    try {

      log.info("Starting participant server on port " + server.port)
      server.start
      log.info("Participant server on port " + server.port + " started")

      verify
    }
    finally {

      log.info("Stopping participant server on port " + server.port)
      server.stop
      log.info("Participant server on port " + server.port + " stopped")

    }
  }

  private def newServer(authMechanism: AuthenticationMechanism) = {
    val scanning = new ScanningParticipantDelegator(new ScanningParticipantHandler() {
      def determineConstraints(req: HttpServletRequest) = Seq[ScanConstraint]().toList
      def determineAggregations(req: HttpServletRequest) = Seq[ScanAggregation]().toList
      def doQuery(constraints: List[ScanConstraint], aggregations: List[ScanAggregation]) = Seq[ScanResultEntry]().toList
    })

    new ParticipantRpcServer(port, scanning, null, null, authMechanism)
  }
}

object ExternalHttpCredentialsTest {
  private[ExternalHttpCredentialsTest] trait Scenario

  private[ExternalHttpCredentialsTest]
  case class NoCredsScenario(authMechanism: AuthenticationMechanism) extends Scenario

  private[ExternalHttpCredentialsTest]
  case class CredsTypeMismatchScenario(clientCreds: InboundExternalHttpCredentialsDef,
                                       authMechanism: AuthenticationMechanism) extends Scenario

  private[ExternalHttpCredentialsTest]
  case class CredsMatchScenario(clientCreds: InboundExternalHttpCredentialsDef,
                                authMechanism: AuthenticationMechanism) extends Scenario

  val port = 55689
  val baseUrl = "http://localhost:" + port
  val scanUrl = baseUrl + "/scan"
  val username = "scott"
  val password = "tiger"
  val queryParamAuthParamName = "authToken"
  val tokenValue = "23823ab23ef3243ccd6c7" // just an arbitrary string

  @DataPoints
  def credentialsMatch = Array(
    CredsMatchScenario(
      InboundExternalHttpCredentialsDef(url = baseUrl, key = username, value = password, `type` = ExternalHttpCredentials.BASIC_AUTH),
      BasicAuthenticationMechanism(Map(username -> password))),
    CredsMatchScenario(
      InboundExternalHttpCredentialsDef(url = baseUrl, key = queryParamAuthParamName, value = tokenValue, `type` = ExternalHttpCredentials.QUERY_PARAMETER),
      QueryParameterAuthenticationMechanism(name = queryParamAuthParamName, value = tokenValue))
  )

  @DataPoints
  def incorrectCredentialsType = Array(
    CredsTypeMismatchScenario(
      InboundExternalHttpCredentialsDef(url = baseUrl, key = username, value = password, `type` = ExternalHttpCredentials.BASIC_AUTH),
      QueryParameterAuthenticationMechanism(name = queryParamAuthParamName, value = tokenValue)
    )
  )
  @DataPoints
  def noCredentials = Array(
    NoCredsScenario(
      QueryParameterAuthenticationMechanism(name = queryParamAuthParamName, value = tokenValue)
    )
  )
}
