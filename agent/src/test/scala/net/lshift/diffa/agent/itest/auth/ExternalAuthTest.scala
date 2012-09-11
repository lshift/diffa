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
import org.springframework.security.ldap.server.ApacheDSContainer
import net.lshift.diffa.agent.itest.support.TestConstants._
import net.lshift.diffa.client.RestClientParams
import net.lshift.diffa.kernel.frontend.{DomainDef, UserDef}
import java.io.File
import org.apache.commons.io.FileUtils
import net.lshift.diffa.agent.client.{ScanningRestClient, ConfigurationRestClient, SecurityRestClient, SystemConfigRestClient}
import org.apache.commons.lang.RandomStringUtils

/**
 * Test cases for Diffa external authentication support.
 */
class ExternalAuthTest {
  val cleanDomain = RandomStringUtils.randomAlphabetic(10)

  val configClient = new SystemConfigRestClient(agentURL)
  val securityClient = new SecurityRestClient(agentURL)
  val externalAdminUsersClient = new SecurityRestClient(agentURL, RestClientParams(username = Some("External Admin"), password = Some("admin123")))
  val externalAdminDomainConfigClient = new ConfigurationRestClient(agentURL, cleanDomain, RestClientParams(username = Some("External Admin"), password = Some("admin123")))
  val externalUserScanningClient = new ScanningRestClient(agentURL, cleanDomain, RestClientParams(username = Some("External User"), password = Some("user123")))

  // Not yet working, though there are some useful pieces in here.
  // The key thing that needs to be solved to make this work is to get the schema modifications adding the
  // sAMAccountName to actually load in successfully, since this is the attribute that Active Directory finds
  // users by. See also the file diffa-ad-test.ldif, which contains what should be appropriate definitions
  // to create these types and users that work with them.

  /*@Test
  def shouldSupportADAuthentication() {
    val suffix = "dc=diffa,dc=io"
    var url = "ldap://127.0.0.1:53389/" + suffix

    // Remove the working directory for the DS
    FileUtils.deleteDirectory(new File(System.getProperty("java.io.tmpdir") + File.separator + "apacheds-spring-security"))

    // Create an LDAP/AD server
    val ldapServer = new ApacheDSContainer(suffix, "classpath*:diffa-ad-test.ldif")
    ldapServer.getService.getInterceptors.add(new SchemaInterceptor())
    val registries = ldapServer.getService.getRegistries
    val bootstrapper = new BootstrapSchemaLoader
    bootstrapper.loadWithDependencies(new SambaSchema, registries)
    ldapServer.afterPropertiesSet()

    try {
      // Create an external user
      usersClient.declareUser(UserDef(name = "external", email = "external@diffa.io", superuser = true, external = true))

      // Configure Diffa to use it
      configClient.setConfigOptions(Map("activedirectory.server" -> "ldap://127.0.0.1:33389", "activedirectory.domain" -> "diffa.io"))

      // Attempt authentication against it
      val adConfigClient = new SystemConfigRestClient(agentURL, RestClientParams(username = Some("external"), password = Some("password123")))
      adConfigClient.getConfigOption("activedirectory.server")    // Try to make a call using credentials stored in the server
    } finally {
      ldapServer.destroy()
    }
  }*/

  @Test
  def shouldSupportLDAPAuthentication() {
    val suffix = "dc=diffa,dc=io"
    val url = "ldap://127.0.0.1:53389/" + suffix

    // Remove the working directory for the DS
    FileUtils.deleteDirectory(new File(System.getProperty("java.io.tmpdir") + File.separator + "apacheds-spring-security"))

    val ldapServer = new ApacheDSContainer(suffix, "classpath*:diffa-ldap-test.ldif")
    ldapServer.afterPropertiesSet()

    try {
      // Configure Diffa to use the LDAP server
      configClient.setConfigOptions(Map("ldap.url" -> url, "ldap.userdn.pattern" -> "cn={0}"))
      configClient.declareDomain(DomainDef(cleanDomain))

      // Create an external admin
      securityClient.declareUser(UserDef(name = "External Admin", email = "external-admin@diffa.io", superuser = true, external = true))

      // Create an internal user, and make it a member of a domain. We'll use our new external superuser to do it.
      externalAdminUsersClient.declareUser(UserDef(name = "External User", email = "external-user@diffa.io", superuser = false, external = true))
      externalAdminDomainConfigClient.makeDomainMember("External User", "Admin")

      // Try to make a call within the domain for the external user
      assertEquals(0, externalUserScanningClient.getScanStatus.size)
    } finally {
      configClient.removeDomain(cleanDomain)
      ldapServer.destroy()
    }
  }
}