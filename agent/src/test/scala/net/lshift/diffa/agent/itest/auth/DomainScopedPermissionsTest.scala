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

import org.junit.Test
import org.junit.Assert._
import net.lshift.diffa.agent.itest.support.TestConstants.{agentURL, defaultDomain}
import com.sun.jersey.api.client.{WebResource, ClientResponse, Client}
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter
import net.lshift.diffa.agent.itest.IsolatedDomainTest
import org.apache.commons.lang3.RandomStringUtils

class DomainScopedPermissionsTest extends IsolatedDomainTest {

  val client = Client.create()
  client.addFilter(new HTTPBasicAuthFilter("guest", "guest"))

  def domainScopedResource(domain: String) =
    client.resource(agentURL).path("domains/" + domain)

  def statusOf(resource: WebResource) =
    resource.get(classOf[ClientResponse]).getStatus

  val existing = domainScopedResource(isolatedDomain)
  val nonExistent = domainScopedResource(RandomStringUtils.randomAlphanumeric(10))

  @Test
  def configurationResourcesAreAccessibleInDefaultDomain {
    assertEquals(200, statusOf(existing.path("config/xml")))
    assertEquals(200, statusOf(existing.path("config/endpoints")))
    assertEquals(200, statusOf(existing.path("config/pairs")))
    assertEquals(200, statusOf(existing.path("config/members")))
  }

  @Test
  def configurationResourcesAreNotFoundInNonExistentDomain {
    assertEquals(404, statusOf(nonExistent.path("config/xml")))
    assertEquals(404, statusOf(nonExistent.path("config/endpoints")))
    assertEquals(404, statusOf(nonExistent.path("config/pairs")))
    assertEquals(404, statusOf(nonExistent.path("config/members")))
  }
}