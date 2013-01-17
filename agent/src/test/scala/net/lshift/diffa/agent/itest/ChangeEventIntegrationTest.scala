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
package net.lshift.diffa.agent.itest

import org.junit.{Ignore, Before, Test}
import org.junit.Assert._
import support.TestConstants._
import net.lshift.diffa.adapter.changes.ChangeEvent
import net.lshift.diffa.kernel.frontend.EndpointDef
import net.lshift.diffa.agent.client.ConfigurationRestClient
import net.lshift.diffa.client.{InvalidChangeEventException, ChangesRestClient}
import org.apache.commons.lang3.RandomStringUtils


@Ignore
class ChangeEventIntegrationTest extends IsolatedDomainTest {

  val endpoint = RandomStringUtils.randomAlphanumeric(10)

  val configurationClient:ConfigurationRestClient = new ConfigurationRestClient(agentURL, isolatedDomain)
  val changesClient:ChangesRestClient = new ChangesRestClient(agentURL, isolatedDomain, endpoint)

  @Before
  def createEndpoint {
    configurationClient.declareEndpoint(EndpointDef(name = endpoint))
  }

  @Test
  def shouldRejectEventWithMissingMandatoryFields = {

    val bogus = new ChangeEvent()

    try {
      changesClient.onChangeEvent(bogus)
      fail("Bogus change event should not have been accepted")
    }
    catch {
      case x:InvalidChangeEventException => // this is expected
    }
  }
}
