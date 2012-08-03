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

import org.junit.Test
import org.junit.Assert._
import net.lshift.diffa.agent.itest.support.TestConstants._
import net.lshift.diffa.client.{BadRequestException, NotFoundException}
import net.lshift.diffa.agent.client.{ConfigurationRestClient, ScanningRestClient}
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.config.RangeCategoryDescriptor
import net.lshift.diffa.kernel.frontend.{EndpointDef, PairDef}
import net.lshift.diffa.agent.itest.IsolatedDomainTest
import org.apache.commons.lang.RandomStringUtils

/**
 * Smoke tests for the scan interface.
 */
class ScanningTest extends IsolatedDomainTest {

  val scanClient = new ScanningRestClient(agentURL, isolatedDomain)
  val configClient = new ConfigurationRestClient(agentURL, isolatedDomain)

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
  def shouldGenerateErrorWhenAScanIsTriggerForAPairWhereNeitherEndpointSupportScanning = {

    val up = RandomStringUtils.randomAlphanumeric(10)
    val down = RandomStringUtils.randomAlphanumeric(10)
    val pair = RandomStringUtils.randomAlphanumeric(10)

    // Neither endpoint support scanning

    configClient.declareEndpoint(EndpointDef(name = up))
    configClient.declareEndpoint(EndpointDef(name = down))
    configClient.declarePair(PairDef(key = pair, upstreamName = up, downstreamName = down))

    scanClient.startScan(pair)

  }
}