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
import net.lshift.diffa.agent.client.ConfigurationRestClient
import org.junit.Test
import com.eaio.uuid.UUID
import net.lshift.diffa.client.NotFoundException
import org.junit.Assert._
import collection.JavaConversions._
import net.lshift.diffa.kernel.config.RangeCategoryDescriptor
import net.lshift.diffa.kernel.frontend.EndpointDef

/**
 * A bunch of smoke tests for the config of a known agent
 */
class AgentConfigTest {

  val client = new ConfigurationRestClient(agentURL, domain)

  @Test
  def shouldFindExistentEndpoint = {
    client.declareEndpoint(EndpointDef(name = "some-endpoint",
                                       scanUrl = "http://some-endpoint.com/scan", contentType = "application/json",
                                       categories = Map("bizDate" -> new RangeCategoryDescriptor("datetime"))))
    val endpoint = client.getEndpoint("some-endpoint")
    assertNotNull(endpoint)
    assertEquals("some-endpoint", endpoint.name)
  }

  @Test(expected = classOf[NotFoundException])
  def shouldGenerateNotFoundError = {
    client.getEndpoint(new UUID().toString)
    ()
  }
}