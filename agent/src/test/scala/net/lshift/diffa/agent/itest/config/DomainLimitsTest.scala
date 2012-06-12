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

import net.lshift.diffa.agent.itest.support.TestConstants._
import com.eaio.uuid.UUID
import org.junit.{Test, After, Before}
import org.junit.Assert._
import net.lshift.diffa.agent.client.{ConfigurationRestClient, DomainLimitsRestClient, SystemConfigRestClient}
import net.lshift.diffa.kernel.frontend.{EndpointDef, PairDef, DomainDef}
import net.lshift.diffa.schema.servicelimits.{ScanResponseSizeLimit, ChangeEventRate}
import net.lshift.diffa.client.NotFoundException

class DomainLimitsTest {

  val autoGeneratedDomain = DomainDef(name = new UUID().toString)
  val autoGeneratedUpstream = EndpointDef(name = new UUID().toString)
  val autoGeneratedDownstream = EndpointDef(name = new UUID().toString)
  val autoGeneratedPair = PairDef(key = new UUID().toString,
                                  upstreamName = autoGeneratedUpstream.name,
                                  downstreamName = autoGeneratedDownstream.name)

  val systemConfig = new SystemConfigRestClient(agentURL)
  val domainLimits = new DomainLimitsRestClient(agentURL, autoGeneratedDomain.name)
  val domainConfig = new ConfigurationRestClient(agentURL, autoGeneratedDomain.name)

  @Before def generateDomain {
    systemConfig.declareDomain(autoGeneratedDomain)
    domainConfig.declareEndpoint(autoGeneratedUpstream)
    domainConfig.declareEndpoint(autoGeneratedDownstream)
    domainConfig.declarePair(autoGeneratedPair)
  }

  @After def removeDomain {
    systemConfig.removeDomain(autoGeneratedDomain.name)
  }

  @Test(expected = classOf[NotFoundException])
  def unknownLimitNameShouldRaiseErrorWhenSettingHardLimit {
    domainLimits.setDomainHardLimit(new UUID().toString, 111)
  }

  @Test(expected = classOf[NotFoundException])
  def unknownLimitNameShouldRaiseErrorWhenSettingDefaultLimit {
    domainLimits.setDomainDefaultLimit(new UUID().toString, 111)
  }

  @Test(expected = classOf[NotFoundException])
  def unknownLimitNameShouldRaiseErrorWhenSettingPairLimit {
    domainLimits.setPairLimit(autoGeneratedPair.key, new UUID().toString, 111)
  }

  @Test(expected = classOf[NotFoundException])
  def unknownLimitNameShouldRaiseErrorWhenGettingEffectivePairLimit {
    domainLimits.effectivePairLimit(autoGeneratedPair.key, new UUID().toString)
  }

  @Test(expected = classOf[NotFoundException])
  def unknownLimitNameShouldRaiseErrorWhenGettingEffectiveDomainLimit {
    domainLimits.effectiveDomainLimit(new UUID().toString)
  }

  @Test
  def shouldSetDomainHardLimitThenDefaultLimit {
    domainLimits.setDomainHardLimit(ChangeEventRate.key, 144)
    domainLimits.setDomainDefaultLimit(ChangeEventRate.key, 144) // Set explicitly to guarantee the state of the agent up front

    val oldDomainLimit = domainLimits.effectiveDomainLimit(ChangeEventRate.key)
    assertEquals(144, oldDomainLimit)

    domainLimits.setDomainDefaultLimit(ChangeEventRate.key, 122)

    val newLimit = domainLimits.effectiveDomainLimit(ChangeEventRate.key)
    assertEquals(122, newLimit)
  }

  @Test
  def shouldSetPairLimitAfterInitiallyUsingDomainLimit {
    domainLimits.setDomainHardLimit(ScanResponseSizeLimit.key, 333)
    domainLimits.setDomainDefaultLimit(ScanResponseSizeLimit.key, 333)

    val domainLimit = domainLimits.effectivePairLimit(autoGeneratedPair.key, ScanResponseSizeLimit.key)
    assertEquals(333, domainLimit)

    domainLimits.setPairLimit(autoGeneratedPair.key, ScanResponseSizeLimit.key, 225)

    val pairLimit = domainLimits.effectivePairLimit(autoGeneratedPair.key, ScanResponseSizeLimit.key)
    assertEquals(225, pairLimit)
  }

}