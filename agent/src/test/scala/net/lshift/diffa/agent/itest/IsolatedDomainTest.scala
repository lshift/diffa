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
package net.lshift.diffa.agent.itest

import com.eaio.uuid.UUID
import net.lshift.diffa.agent.client.SystemConfigRestClient
import support.TestConstants._
import org.slf4j.LoggerFactory
import org.junit.{After, Before}
import net.lshift.diffa.kernel.frontend.DomainDef

/**
 * Common superclass to provide a random domain for isolation purposes during testing.
 */
abstract class IsolatedDomainTest {

  val log = LoggerFactory.getLogger(getClass)

  protected val isolatedDomain = new UUID().toString
  protected val systemConfig = new SystemConfigRestClient(agentURL)

  @Before
  def declareDomain {
    log.info("Creating domain: " + isolatedDomain)
    systemConfig.declareDomain(DomainDef(name = isolatedDomain))
  }

  @After
  def deleteDomain {
    log.info("Deleting domain: " + isolatedDomain)
    systemConfig.removeDomain(isolatedDomain)
  }

}
