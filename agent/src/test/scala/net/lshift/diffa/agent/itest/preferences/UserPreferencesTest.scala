/**
 * Copyright (C) 2010 - 2012 LShift Ltd.
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
package net.lshift.diffa.agent.itest.preferences

import net.lshift.diffa.kernel.preferences.FilteredItemType
import net.lshift.diffa.agent.itest.support.TestConstants._
import net.lshift.diffa.agent.client.{SecurityRestClient, SystemConfigRestClient, ConfigurationRestClient, UsersRestClient}
import org.junit.{Before, Test}
import org.junit.Assert._
import net.lshift.diffa.kernel.frontend.{UserDef, DomainDef, DomainPairDef, EndpointDef}
import com.eaio.uuid.UUID
import net.lshift.diffa.client.{RestClientParams, AccessDeniedException}

class UserPreferencesTest {

  val domain = new UUID().toString

  val rootUserPreferencesClient = new UsersRestClient(agentURL, agentUsername)
  val systemConfigClient = new SystemConfigRestClient(agentURL)
  val configClient = new ConfigurationRestClient(agentURL, domain)
  val securityClient = new SecurityRestClient(agentURL)

  val upstream = EndpointDef(name = new UUID().toString)
  val downstream = EndpointDef(name = new UUID().toString)
  val pair = DomainPairDef(key = new UUID().toString,
                           domain = domain,
                           upstreamName = upstream.name,
                           downstreamName = downstream.name)

  @Before
  def createTestData {
    systemConfigClient.declareDomain(DomainDef(name = domain))

    configClient.declareEndpoint(upstream)
    configClient.declareEndpoint(downstream)
    configClient.declarePair(pair.withoutDomain)

  }

  @Test
  def nonRootUserShouldBeAbleToModifyOwnDomainSettingsWhenMemberOfADomain {

    val nonRootUser = UserDef(name = new UUID().toString,superuser = false, external = true)
    securityClient.declareUser(nonRootUser)
    configClient.makeDomainMember(nonRootUser.name)

    val token = securityClient.getUserToken(nonRootUser.name)
    val invokingCreds = RestClientParams(token = Some(token))

    // This attempts to access a particular user's settings when authenticated as that particular user

    val nonRootUserPreferencesClient = new UsersRestClient(agentURL, nonRootUser.name, invokingCreds)
    nonRootUserPreferencesClient.createFilter(pair.asRef, FilteredItemType.SWIM_LANE)
  }

  @Test(expected = classOf[AccessDeniedException])
  def nonRootUserShouldNotBeAbleToModifyOwnUserSettingsWhenNotMemberOfADomain {

    val nonRootUser = UserDef(name = new UUID().toString,superuser = false, external = true)
    securityClient.declareUser(nonRootUser)

    val token = securityClient.getUserToken(nonRootUser.name)
    val invokingCreds = RestClientParams(token = Some(token))

    // This attempts to access a particular user's settings when authenticated as that particular user
    // but they are not a member of the target domain

    val nonRootUserPreferencesClient = new UsersRestClient(agentURL, nonRootUser.name, invokingCreds)
    nonRootUserPreferencesClient.createFilter(pair.asRef, FilteredItemType.SWIM_LANE)
  }


  @Test(expected = classOf[AccessDeniedException])
  def nonRootUserShouldNotBeAbleToModifyOtherUsersDomainSettingsWhenMemberOfSameDomain {

    val nonRootUser = UserDef(name = new UUID().toString, superuser = false, external = true)
    val otherNonRootUser = UserDef(name = new UUID().toString, superuser = false, external = true)

    securityClient.declareUser(nonRootUser)
    securityClient.declareUser(otherNonRootUser)
    configClient.makeDomainMember(nonRootUser.name)
    configClient.makeDomainMember(otherNonRootUser.name)

    val token = securityClient.getUserToken(nonRootUser.name)
    val invokingCreds = RestClientParams(token = Some(token))

    // This attempts to access a particular user's settings when authenticated as another user
    // who is a member of the same domain

    val nonRootUserPreferencesClient = new UsersRestClient(agentURL, otherNonRootUser.name, invokingCreds)
    nonRootUserPreferencesClient.createFilter(pair.asRef, FilteredItemType.SWIM_LANE)
  }

}
