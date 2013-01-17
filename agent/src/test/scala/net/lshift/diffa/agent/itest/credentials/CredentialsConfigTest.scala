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
import net.lshift.diffa.agent.client.CredentialsRestClient
import org.junit.Assert._
import net.lshift.diffa.kernel.frontend.{OutboundExternalHttpCredentialsDef, InboundExternalHttpCredentialsDef}
import org.junit.runner.RunWith
import org.junit.experimental.theories.{Theories, Theory, DataPoint}
import net.lshift.diffa.kernel.config.ExternalHttpCredentials
import net.lshift.diffa.agent.itest.IsolatedDomainTest
import org.junit.Ignore

@Ignore
@RunWith(classOf[Theories])
class CredentialsConfigTest extends IsolatedDomainTest {
  import CredentialsConfigTest.Scenario

  val client = new CredentialsRestClient(agentURL, isolatedDomain)

  @Theory
  def credentialsShouldRoundTrip(scenario: Scenario) = {

    client.addCredentials(scenario.inbound)
    assertTrue(client.listCredentials.contains(scenario.outbound))

    client.deleteCredentials(scenario.inbound.url)
    assertFalse(client.listCredentials.contains(scenario.outbound))
  }
}


object CredentialsConfigTest {
  private[CredentialsConfigTest] case class Scenario(inbound: InboundExternalHttpCredentialsDef,
                                                     outbound: OutboundExternalHttpCredentialsDef)

  @DataPoint def basicAuth = Scenario(
    InboundExternalHttpCredentialsDef(url = "https://acme.com", key = "scott", value = "tiger", `type` = ExternalHttpCredentials.BASIC_AUTH),
    OutboundExternalHttpCredentialsDef(url = "https://acme.com", key = "scott", `type` = ExternalHttpCredentials.BASIC_AUTH)
  )

  @DataPoint def queryParameters = Scenario(
    InboundExternalHttpCredentialsDef(url = "http://foo.acme.com:9090", key = "mandy", value = "water", `type` = ExternalHttpCredentials.QUERY_PARAMETER),
    OutboundExternalHttpCredentialsDef(url = "http://foo.acme.com:9090", key = "mandy", `type` = ExternalHttpCredentials.QUERY_PARAMETER)
  )

}
