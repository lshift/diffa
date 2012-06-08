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
import net.lshift.diffa.kernel.frontend.DomainDef
import com.eaio.uuid.UUID
import org.junit.{Test, After, Before}
import org.junit.Assert._
import net.lshift.diffa.agent.client.{DomainLimitsRestClient, SystemConfigRestClient}
import net.lshift.diffa.kernel.config.limits.ChangeEventRate

class DomainLimitsTest {

  val autoGeneratedDomain = DomainDef(name = new UUID().toString)

  val systemConfig = new SystemConfigRestClient(agentURL)
  val domainLimits = new DomainLimitsRestClient(agentURL, autoGeneratedDomain.name)

  @Before def generateDomain = systemConfig.declareDomain(autoGeneratedDomain)
  @After def removeDomain {
    systemConfig.removeDomain(autoGeneratedDomain.name)
  }

  @Test
  def shouldSetDomainHardLimit {
    domainLimits.setDomainHardLimit(ChangeEventRate.key, 14)

    val limit = domainLimits.effectiveDomainLimit(ChangeEventRate.key)
    assertEquals(14, limit)
  }

}
