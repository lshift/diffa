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
import net.lshift.diffa.agent.client.SystemConfigRestClient
import org.junit.Assert._
import com.eaio.uuid.UUID
import net.lshift.diffa.kernel.frontend.DomainDef
import net.lshift.diffa.client.NotFoundException
import net.lshift.diffa.kernel.config.limits.ChangeEventRate
import org.junit.Test

class SystemConfigTest {

  val client = new SystemConfigRestClient(agentURL)

  @Test
  def shouldDeclareAndDeleteDomain {
    val domain = DomainDef(name = new UUID().toString)
    client.declareDomain(domain)
    client.removeDomain(domain.name)
  }

  @Test(expected = classOf[NotFoundException])
  def nonExistentDomainShouldRaiseError {
    client.removeDomain(new UUID().toString)
  }

  @Test(expected = classOf[NotFoundException])
  def shouldSetSystemConfigOption {
    client.setConfigOption("foo", "bar")
    assertEquals("bar", client.getConfigOption("foo"))
    client.deleteConfigOption("foo")

    // This should provoke a 404
    client.getConfigOption("foo")
  }

  @Test
  def shouldSetMultipleSystemConfigOptions {
    client.setConfigOptions(Map("foo" -> "bar", "foz" -> "boz"))
    assertEquals("bar", client.getConfigOption("foo"))
    assertEquals("boz", client.getConfigOption("foz"))
  }

  @Test(expected = classOf[NotFoundException])
  def unknownLimitNameShouldRaiseErrorWhenSettingHardLimit {
    client.setHardSystemLimit(new UUID().toString, 111)
  }

  @Test(expected = classOf[NotFoundException])
  def unknownLimitNameShouldRaiseErrorWhenSettingDefaultLimit {
    client.setDefaultSystemLimit(new UUID().toString, 111)
  }

  @Test(expected = classOf[NotFoundException])
  def unknownLimitNameShouldRaiseErrorWhenGettingEffectiveLimit {
    client.getEffectiveSystemLimit(new UUID().toString)
  }

  @Test
  def shouldSetSystemHardLimitFollowedBySoftLimit {

    client.setHardSystemLimit(ChangeEventRate.key, 19)
    client.setDefaultSystemLimit(ChangeEventRate.key, 19) // Assert the default value, since this might have been set by a previous test run

    val oldlimit = client.getEffectiveSystemLimit(ChangeEventRate.key)
    assertEquals(19, oldlimit)

    client.setDefaultSystemLimit(ChangeEventRate.key, 18)

    val newlimit = client.getEffectiveSystemLimit(ChangeEventRate.key)
    assertEquals(18, newlimit)
  }
}