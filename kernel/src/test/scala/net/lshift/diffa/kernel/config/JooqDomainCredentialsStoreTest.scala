/**
 * Copyright (C) 2010-2012 LShift Ltd.
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
package net.lshift.diffa.kernel.config

import net.lshift.diffa.kernel.frontend.{OutboundExternalHttpCredentialsDef, InboundExternalHttpCredentialsDef}
import org.junit.Assert._
import net.lshift.diffa.kernel.StoreReferenceContainer
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import org.junit.{Before, AfterClass, Test}

class JooqDomainCredentialsStoreTest {

  private val storeReferences = JooqDomainCredentialsStoreTest.storeReferences
  private val systemConfigStore = storeReferences.systemConfigStore
  private val domainCredentialsStore = storeReferences.domainCredentialsStore

  val domainName = "domain-with-http-creds"

  @Before
  def setUp = storeReferences.clearConfiguration(domainName)

  @Test
  def externalHttpCredentialsShouldBeWriteOnly() {

    systemConfigStore.createOrUpdateDomain(domainName)

    val basicAuthIn = InboundExternalHttpCredentialsDef(url = "https://acme.com/foo", key = "scott", value = "tiger", `type` = "basic_auth")
    val queryParamIn = InboundExternalHttpCredentialsDef(url = "https://acme.com/bar", key = "authToken", value = "a987bg6", `type` = "query_parameter")

    val basicAuthOut = OutboundExternalHttpCredentialsDef(url = "https://acme.com/foo", key = "scott", `type` = "basic_auth")
    val queryParamOut = OutboundExternalHttpCredentialsDef(url = "https://acme.com/bar", key = "authToken", `type` = "query_parameter")

    domainCredentialsStore.addExternalHttpCredentials(domainName, basicAuthIn)
    domainCredentialsStore.addExternalHttpCredentials(domainName, queryParamIn)

    val creds1 = domainCredentialsStore.listCredentials(domainName)

    assertEquals(2, creds1.length)
    assertTrue(creds1.contains(basicAuthOut))
    assertTrue(creds1.contains(queryParamOut))

    domainCredentialsStore.deleteExternalHttpCredentials(domainName, "https://acme.com/foo")

    val creds2 = domainCredentialsStore.listCredentials(domainName)

    assertEquals(1, creds2.length)
    assertFalse(creds2.contains(basicAuthOut))
    assertTrue(creds2.contains(queryParamOut))

    domainCredentialsStore.deleteExternalHttpCredentials(domainName, "https://acme.com/bar")

    val creds3 = domainCredentialsStore.listCredentials(domainName)

    assertTrue(creds3.isEmpty)

  }

  @Test
  def shouldReturnMostSpecificCredentials() {

    systemConfigStore.createOrUpdateDomain(domainName)

    Seq(
      InboundExternalHttpCredentialsDef(url = "https://acme.com:8081/foo/bar", key = "wendy", value = "shell", `type` = "basic_auth"),
      InboundExternalHttpCredentialsDef(url = "http://acme.com:8080/foo/bar",  key = "jolly", value = "river", `type` = "basic_auth"),
      InboundExternalHttpCredentialsDef(url = "https://acme.com:8080/foo/bar", key = "scott", value = "tiger", `type` = "basic_auth"),
      InboundExternalHttpCredentialsDef(url = "https://acme.com:8080",         key = "alice", value = "seven", `type` = "basic_auth"),
      InboundExternalHttpCredentialsDef(url = "https://acme.com:8080/foo",     key = "shane", value = "yetti", `type` = "basic_auth"),
      InboundExternalHttpCredentialsDef(url = "https://acme.com:8080/foo/",    key = "tango", value = "split", `type` = "basic_auth")
      ).foreach(domainCredentialsStore.addExternalHttpCredentials(domainName, _))

    val creds = domainCredentialsStore.credentialsForUrl(domainName, "https://acme.com:8080/foo/bar/baz")

    assertEquals(Some(BasicAuthCredentials(username = "scott", password = "tiger")), creds)

  }
}

object JooqDomainCredentialsStoreTest {
  private[JooqDomainCredentialsStoreTest] val env =
    TestDatabaseEnvironments.uniqueEnvironment("target/domainCredentialsStore")

  private[JooqDomainCredentialsStoreTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  @AfterClass
  def tearDown {
    storeReferences.tearDown
  }
}
