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

import org.junit.{Ignore, Test}
import org.junit.Assert._
import net.lshift.diffa.agent.itest.support.TestConstants._
import com.eaio.uuid.UUID
import net.lshift.diffa.client.{RestClientParams, AccessDeniedException}
import com.sun.jersey.api.client.UniformInterfaceException
import net.lshift.diffa.agent.itest.IsolatedDomainTest
import org.apache.commons.lang3.RandomStringUtils
import net.lshift.diffa.kernel.frontend.{PolicyMember, UserDef, DomainDef}
import net.lshift.diffa.agent.client.{PoliciesRestClient, ConfigurationRestClient, SecurityRestClient, SystemConfigRestClient}
import net.lshift.diffa.agent.auth.{Privilege, Privileges}
import net.lshift.diffa.kernel.config.system.PolicyStatement

/**
 * Tests whether domain membership admin is accessible via the REST API
 */
@Ignore
class MembershipTest extends IsolatedDomainTest {

  val username = RandomStringUtils.randomAlphabetic(10)
  val email = username + "@test.diffa.io"
  val password = "foo"

  val systemConfigClient = new SystemConfigRestClient(agentURL)
  val securityClient = new SecurityRestClient(agentURL)
  val configClient = new ConfigurationRestClient(agentURL, isolatedDomain)
  val policiesClient = new PoliciesRestClient(agentURL, isolatedDomain)
  val rootPoliciesClient = new PoliciesRestClient(agentURL, defaultDomain)
  val userConfigClient = new ConfigurationRestClient(agentURL, isolatedDomain, RestClientParams(username = Some(username), password = Some(password)))

  @Test
  def shouldBeAbleToManageDomainMembership = {

    def assertIsDomainMember(username:String, policy:String, expectation:Boolean) = {
      val members = configClient.listDomainMembers
      val isMember = members.toSeq.find(m => m == PolicyMember(username, policy)).isDefined
      assertEquals(expectation, isMember)
    }

    securityClient.declareUser(UserDef(username,email,false,password))

    configClient.makeDomainMember(username, "User")

    assertIsDomainMember(username, "User", true)

    configClient.removeDomainMembership(username, "User")
    assertIsDomainMember(username, "User", false)
  }

  @Test
  def shouldBeAbleToListDomainsUserIsAMemberOf() = {

    securityClient.declareUser(UserDef(username, email, false, password))
    configClient.makeDomainMember(username, "User")

    assertEquals(List(isolatedDomain), securityClient.getMembershipDomains(username).map(d => d.name).toList)

    configClient.removeDomainMembership(username, "User")

    assertEquals(List(), securityClient.getMembershipDomains(username).toList)
  }

  @Test(expected = classOf[AccessDeniedException])
  def shouldNotBeAbleToAccessDomainConfigurationWhenNotADomainMember() {

    securityClient.declareUser(UserDef(username,email,false,password))
    configClient.removeDomainMembership(username, "User")   // Ensure the user isn't a domain member
    userConfigClient.listDomainMembers
  }

  @Test
  def shouldBeAbleToAccessDomainConfigurationWhenDomainMember() {

    securityClient.declareUser(UserDef(username,email,false,password))
    configClient.makeDomainMember(username, "Admin")
    userConfigClient.listDomainMembers
  }

  @Test
  def shouldBeAbleToCreateExternalUser() {
    securityClient.declareUser(UserDef(name = username, email = email, external = true))
  }

  @Test
  def shouldNotBeAbleToAuthenticateWithExternalUser() {
    securityClient.declareUser(UserDef(name = username, email = email, external = true))

    val noPasswordConfigClient = new ConfigurationRestClient(agentURL, isolatedDomain, RestClientParams(username = Some(username), password = Some("")))
    try {
      noPasswordConfigClient.listDomainMembers
      fail("Should have thrown 401")
    } catch {
      case ex:UniformInterfaceException => assertEquals(401, ex.getResponse.getStatus)
    }

    val dummyPasswordConfigClient = new ConfigurationRestClient(agentURL, isolatedDomain, RestClientParams(username = Some(username), password = Some("abcdef")))
    try {
      dummyPasswordConfigClient.listDomainMembers
      fail("Should have thrown 401")
    } catch {
      case ex:UniformInterfaceException => assertEquals(401, ex.getResponse.getStatus)
    }
  }

  // This test ensures that all privileges defined in the Privileges object are actually assignable
  @Test
  def shouldBeAbleToCreateRoleWithAllKnownPermissions() {
    val stmts = allAssignablePrivileges.map(p => PolicyStatement(privilege = p, target = "*"))

    policiesClient.storePolicy("NewPol", stmts)

    val stored = policiesClient.retrievePolicy("NewPol")
    assertEquals(stmts.toSet, stored.toSet)
  }

  @Test
  def shouldHaveAllPrivilegesAssignedToAdminRole() {
    val stmts = allAssignablePrivileges.map(p => PolicyStatement(privilege = p, target = "*"))

    val stored = rootPoliciesClient.retrievePolicy("Admin")
    assertEquals(stmts.toSet, stored.toSet)
  }

  private lazy val allAssignablePrivileges = {
    val privs = Privileges.getClass.getDeclaredMethods.
      filter(m => classOf[Privilege].isAssignableFrom(m.getReturnType)).
      map(m => m.invoke(Privileges).asInstanceOf[Privilege].name).toSeq

    // user-preferences is a snowflake we should ignore
    privs.filter(_ != "user-preferences")
  }
}