/**
 * Copyright (C) 2012 LShift Ltd.
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
package net.lshift.diffa.agent.itest.auth

import org.junit.{After, Before, Test}
import net.lshift.diffa.agent.itest.support.TestConstants._
import net.lshift.diffa.kernel.frontend.{DomainDef, UserDef}
import net.lshift.diffa.agent.client.{SystemConfigRestClient, ScanningRestClient, SecurityRestClient}
import net.lshift.diffa.client.RestClientParams
import org.junit.Assert._
import org.apache.commons.lang.RandomStringUtils

/**
 * Integration test for token based authentication.
 */
class TokenAuthTest {
  val securityClient = new SecurityRestClient(agentURL)
  val systemClient = new SystemConfigRestClient(agentURL)
  val emptyDomain = RandomStringUtils.randomAlphabetic(10)

  @Before
  def declareUserAndDomain {
    securityClient.declareUser(UserDef(name = "TokenUser", email = "token@diffa.io", superuser = true, password = "password123"))
    systemClient.declareDomain(DomainDef(name = emptyDomain))
  }

  @After
  def cleanup {
    systemClient.removeDomain(emptyDomain)
  }

  @Test
  def shouldAllowUserToLoginWithToken() {
    val token = securityClient.getUserToken("TokenUser")
    val tokenScanningClient = new ScanningRestClient(agentURL, emptyDomain, RestClientParams(token = Some(token)))
    assertEquals(0, tokenScanningClient.getScanStatus.size)
  }

  @Test
  def shouldAllowUserTokenToBeRegenerated() {
    val token = securityClient.getUserToken("TokenUser")
    securityClient.clearUserToken("TokenUser")
    val token2 = securityClient.getUserToken("TokenUser")

    val badTokenScanningClient = new ScanningRestClient(agentURL, emptyDomain, RestClientParams(token = Some(token)))
    try {
      badTokenScanningClient.getScanStatus.size
      fail("Should have thrown exception")
    } catch {
      case ex => assertTrue(ex.getMessage.contains("Unauthorized"))
    }

    val goodTokenScanningClient = new ScanningRestClient(agentURL, emptyDomain, RestClientParams(token = Some(token2)))
    assertEquals(0, goodTokenScanningClient.getScanStatus.size)
  }
}