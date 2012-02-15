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
import com.eaio.uuid.UUID
import net.lshift.diffa.agent.client.{ConfigurationRestClient, UsersRestClient, SystemConfigRestClient}
import net.lshift.diffa.kernel.frontend.{UserDef, DomainDef}
import net.lshift.diffa.client.{RestClientParams, AccessDeniedException}
import com.sun.jersey.api.client.UniformInterfaceException

/**
 * Tests whether domain membership admin is accessible via the REST API
 */
class MembershipTest {

  val username = new UUID().toString
  val email = username + "@test.diffa.io"
  val domain = DomainDef(name = new UUID().toString)
  val password = "foo"

  val systemConfigClient = new SystemConfigRestClient(agentURL)
  val usersClient = new UsersRestClient(agentURL)
  val configClient = new ConfigurationRestClient(agentURL, domain.name)
  val userConfigClient = new ConfigurationRestClient(agentURL, domain.name, RestClientParams(username = Some(username), password = Some(password)))

  @Test
  def shouldBeAbleToManageDomainMembership = {

    def assertIsDomainMember(username:String, expectation:Boolean) = {
      val members = configClient.listDomainMembers
      val isMember = members.toSeq.find(m => m.name == username).isDefined
      assertEquals(expectation, isMember)
    }
    systemConfigClient.declareDomain(domain)
    usersClient.declareUser(UserDef(username,email,false,password))

    configClient.makeDomainMember(username)

    assertIsDomainMember(username, true)

    configClient.removeDomainMembership(username)
    assertIsDomainMember(username, false)
  }

  @Test
  def shouldBeAbleToListDomainsUserIsAMemberOf() = {
    systemConfigClient.declareDomain(domain)
    usersClient.declareUser(UserDef(username, email, false, password))
    configClient.makeDomainMember(username)

    assertEquals(List(domain), usersClient.getMembershipDomains(username).toList)

    configClient.removeDomainMembership(username)

    assertEquals(List(), usersClient.getMembershipDomains(username).toList)
  }

  @Test(expected = classOf[AccessDeniedException])
  def shouldNotBeAbleToAccessDomainConfigurationWhenNotADomainMember() {
    systemConfigClient.declareDomain(domain)
    usersClient.declareUser(UserDef(username,email,false,password))
    configClient.removeDomainMembership(username)   // Ensure the user isn't a domain member
    userConfigClient.listDomainMembers
  }

  @Test
  def shouldBeAbleToAccessDomainConfigurationWhenDomainMember() {
    systemConfigClient.declareDomain(domain)
    usersClient.declareUser(UserDef(username,email,false,password))
    configClient.makeDomainMember(username)
    userConfigClient.listDomainMembers
  }

  @Test
  def shouldBeAbleToCreateExternalUser() {
    usersClient.declareUser(UserDef(name = username, email = email, external = true))
  }

  @Test
  def shouldNotBeAbleToAuthenticateWithExternalUser() {
    usersClient.declareUser(UserDef(name = username, email = email, external = true))

    val noPasswordConfigClient = new ConfigurationRestClient(agentURL, domain.name, RestClientParams(username = Some(username), password = Some("")))
    try {
      noPasswordConfigClient.listDomainMembers
      fail("Should have thrown 401")
    } catch {
      case ex:UniformInterfaceException => assertEquals(401, ex.getResponse.getStatus)
    }

    val dummyPasswordConfigClient = new ConfigurationRestClient(agentURL, domain.name, RestClientParams(username = Some(username), password = Some("abcdef")))
    try {
      noPasswordConfigClient.listDomainMembers
      fail("Should have thrown 401")
    } catch {
      case ex:UniformInterfaceException => assertEquals(401, ex.getResponse.getStatus)
    }
  }
}