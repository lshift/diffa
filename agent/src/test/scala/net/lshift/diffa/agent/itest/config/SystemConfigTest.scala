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
import org.junit.Test
import org.junit.Assert._
import com.eaio.uuid.UUID
import net.lshift.diffa.kernel.frontend.DomainDef
import net.lshift.diffa.client.NotFoundException

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

}