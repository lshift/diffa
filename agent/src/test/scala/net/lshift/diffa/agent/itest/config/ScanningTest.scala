/**
 * Copyright (C) 2010-2011 LShift Ltd.
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

import org.junit.{Ignore, Test}
import org.junit.Assert._
import net.lshift.diffa.agent.itest.support.TestConstants._
import net.lshift.diffa.client.{RestClientParams, BadRequestException, NotFoundException}
import net.lshift.diffa.agent.client.{SecurityRestClient, ConfigurationRestClient, ScanningRestClient}
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.frontend.{DomainDef, UserDef, EndpointDef, PairDef}
import net.lshift.diffa.agent.itest.IsolatedDomainTest
import org.apache.commons.lang.RandomStringUtils
import net.lshift.diffa.config.RangeCategoryDescriptor

/**
 * Smoke tests for the scan interface.
 */
@Ignore
class ScanningTest extends IsolatedDomainTest {
  val securityClient = new SecurityRestClient(agentURL)
  val configClient = new ConfigurationRestClient(agentURL, isolatedDomain)
  val scanClient = new ScanningRestClient(agentURL, isolatedDomain)

  private def withSubspace(parentSpace: String, subspace: String, memberUsername: String, fn: => Unit) {
    try {
      systemConfig.declareDomain(DomainDef(name = parentSpace))
      systemConfig.declareDomain(DomainDef(name = subspace))
      val subspaceConfigClient = new ConfigurationRestClient(agentURL, subspace)
      subspaceConfigClient.makeDomainMember(userName = memberUsername, policy = "Admin")
      val pair = RandomStringUtils.randomAlphanumeric(10)
      val up = RandomStringUtils.randomAlphanumeric(10)
      val down = RandomStringUtils.randomAlphanumeric(10)
      subspaceConfigClient.declareEndpoint(EndpointDef(name = up, scanUrl = "http://upstream.com"))
      subspaceConfigClient.declareEndpoint(EndpointDef(name = down, scanUrl = "http://downstream.com"))
      subspaceConfigClient.declarePair(PairDef(key = pair, upstreamName = up, downstreamName = down))
      Thread.sleep(5000L) // declaring the pair is asynchronous, but we need it to be done before proceeding to next

      fn
    } finally {
      systemConfig.removeDomain(subspace)
      systemConfig.removeDomain(parentSpace)
    }
  }
  @Test
  def scanStatesShouldBeProvidedToSubspaceMember {
    // GIVEN space a/b; AND u is a member of a/b; AND space a/b has at least one pair
    // WHEN  m retrieves scan state of a/b
    // THEN  m should get the scan status of pairs in a/b (not an exception)
    val parentSpace = RandomStringUtils.randomAlphanumeric(10)
    val subspace = "%s/%s".format(parentSpace, RandomStringUtils.randomAlphanumeric(10))
    val nonRootUser = UserDef(name = RandomStringUtils.randomAlphanumeric(10),superuser = false, external = true)
    securityClient.declareUser(nonRootUser)

    val nonRootUserScanClient = {
      val token = securityClient.getUserToken(nonRootUser.name)
      val invokingCreds = RestClientParams(token = Some(token))
      new ScanningRestClient(agentURL, subspace, invokingCreds)
    }

    withSubspace(parentSpace, subspace, nonRootUser.name,
      assertFalse("Scan state query should return scan states", nonRootUserScanClient.getScanStatus.isEmpty))
  }

  @Test(expected = classOf[NotFoundException])
  def nonExistentPairShouldGenerateNotFoundError = {
    scanClient.cancelScanning(RandomStringUtils.randomAlphanumeric(10))
    ()
  }

  @Test
  def existentPairShouldNotGenerateErrorWhenCancellingAScanThatIsNotRunning = {
    val up = RandomStringUtils.randomAlphanumeric(10)
    val down = RandomStringUtils.randomAlphanumeric(10)
    val pair = RandomStringUtils.randomAlphanumeric(10)

    val categories = Map("bizDate" -> new RangeCategoryDescriptor("datetime"))

    configClient.declareEndpoint(EndpointDef(name = up, scanUrl = "http://upstream.com", categories = categories))
    configClient.declareEndpoint(EndpointDef(name = down, scanUrl = "http://downstream.com", categories = categories))
    configClient.declarePair(PairDef(key = pair, upstreamName = up, downstreamName = down))

    // Simple smoke test - you could kick off a scan and verify that it gets interrupted,
    // but this code path is tested in the unit test

    assertTrue(scanClient.cancelScanning(pair))
  }

  @Test(expected = classOf[BadRequestException])
  def shouldGenerateErrorWhenAScanIsTriggeredForAPairWhereNeitherEndpointSupportsScanning = {

    val up = RandomStringUtils.randomAlphanumeric(10)
    val down = RandomStringUtils.randomAlphanumeric(10)
    val pair = RandomStringUtils.randomAlphanumeric(10)

    // Neither endpoint supports scanning

    configClient.declareEndpoint(EndpointDef(name = up))
    configClient.declareEndpoint(EndpointDef(name = down))
    configClient.declarePair(PairDef(key = pair, upstreamName = up, downstreamName = down))

    scanClient.startScan(pair)
  }
}