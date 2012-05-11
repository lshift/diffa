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
import org.junit.Test
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

class ExternalHttpCredentialsTest {

  val credentialsClient = new CredentialsRestClient(agentURL, domain)
  val scanClient = new ScanningRestClient(agentURL, domain)
  val configClient = new ConfigurationRestClient(agentURL, domain)



  @Test
  def shouldUseExternalCredentialsForScan = {

    val port = 55689
    val baseUrl = "http://localhost:" + port
    val scanUrl = baseUrl + "/scan"

    // Make that the creds do not exist the DB before this test is run (e.g. from a previosu test run)

    try {
      credentialsClient.deleteCredentials(baseUrl)
    }
    catch {
      case _ => // ignore
    }

    val creds = InboundExternalHttpCredentialsDef(url = baseUrl,
                                                  key = "scott",
                                                  value = "tiger",
                                                  `type` = ExternalHttpCredentials.BASIC_AUTH)

    val authentication = BasicAuthenticationMechanism(Map("scott" -> "tiger"))

    val scanning = new ScanningParticipantDelegator(new ScanningParticipantHandler() {

      def determineConstraints(req: HttpServletRequest) = Seq[ScanConstraint]().toList
      def determineAggregations(req: HttpServletRequest) = Seq[ScanAggregation]().toList
      def doQuery(constraints: List[ScanConstraint], aggregations: List[ScanAggregation]) = Seq[ScanResultEntry]().toList

    })

    val server = new ParticipantRpcServer(port, scanning, null, null, authentication)
    server.start

    val up = new UUID().toString
    val down = new UUID().toString
    val pair = new UUID().toString

    configClient.declareEndpoint(EndpointDef(name = up, scanUrl = scanUrl))
    configClient.declareEndpoint(EndpointDef(name = down, scanUrl = scanUrl))
    configClient.declarePair(PairDef(pair, "same", 1, up, down))

    // Kick off a scan without having set any credentials -> expect this to fail

    scanClient.startScan(pair)
    ScanningHelper.waitForScanStatus(scanClient, pair, PairScanState.FAILED)

    // Now set the correct credentials and kick off another scan -> expect this to pass

    credentialsClient.addCredentials(creds)

    scanClient.startScan(pair)
    ScanningHelper.waitForScanStatus(scanClient, pair, PairScanState.UP_TO_DATE)

    server.stop
  }


}
