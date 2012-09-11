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

package net.lshift.diffa.kernel.config.system

import org.junit.Assert._
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.StoreReferenceContainer
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import org.junit.{Ignore, AfterClass, Before, Test}
import net.lshift.diffa.kernel.config.{User, Domain}

class JooqSystemConfigStoreTest {
  private val storeReferences = JooqSystemConfigStoreTest.storeReferences

  private val systemConfigStore = storeReferences.systemConfigStore

  val domainName = storeReferences.defaultDomain
  val domain = Domain(name=domainName)

  val TEST_USER = User(name = "foo", email = "foo@bar.com", passwordEnc = "84983c60f7daadc1cb8698621f802c0d9f9a3c3c295c810748fb048115c186ec", superuser = false)
  val TEST_SUPERUSER = User(name = "fooroot", email = "root@bar.com", passwordEnc = "fcde2b2edba56bf408601fb721fe9b5c338d10ee429ea04fae5511b68fbf8fb9", superuser = true)

  @Before
  def setup() {

    val space = systemConfigStore.createOrUpdateSpace(domainName)
    storeReferences.clearConfiguration(space.id)

    storeReferences.clearUserConfig
  }

  @Test
  def shouldBeAbleToSetSystemProperty() {
    systemConfigStore.setSystemConfigOption("foo", "bar")
    assertEquals("bar", systemConfigStore.maybeSystemConfigOption("foo").get)
  }

  @Test(expected = classOf[MissingObjectException])
  def shouldGetExceptionWhenRetrievingMissingUser() {
    systemConfigStore.getUser("unknownuser")
  }

  @Test
  def shouldBeAbleToAddUserAndSeeItByGet() {
    systemConfigStore.createOrUpdateUser(TEST_USER)
    val user = systemConfigStore.getUser(TEST_USER.name)
    assertUserEquals(TEST_USER, user)
  }

  @Test
  def shouldBeAbleToAddUserAndSeeItInList() {
    systemConfigStore.createOrUpdateUser(TEST_USER)
    val result = systemConfigStore.listUsers.filterNot(_.name == storeReferences.defaultUser)
    assertEquals(1, result.length)
    assertUserEquals(TEST_USER, result(0))
  }

  @Test(expected = classOf[MissingObjectException])
  def shouldGetExceptionWhenRetrievingDeletedUser() {
    systemConfigStore.createOrUpdateUser(TEST_USER)
    systemConfigStore.deleteUser(TEST_USER.name)
    systemConfigStore.getUser(TEST_USER.name)
  }

  @Test
  def shouldNotSeeDeletedUsersInList() {
    systemConfigStore.createOrUpdateUser(TEST_USER)
    systemConfigStore.deleteUser(TEST_USER.name)
    val result = systemConfigStore.listUsers.filterNot(_.name == storeReferences.defaultUser)
    assertEquals(0, result.length)
  }

  @Test
  def shouldBeAbleToUpdateUser() {
    val updatedUser = User(name = TEST_USER.name, email = "somethingelse@bar.com",
      passwordEnc = TEST_SUPERUSER.passwordEnc, superuser = true)

    systemConfigStore.createOrUpdateUser(TEST_USER)
    systemConfigStore.createOrUpdateUser(updatedUser)

    val user = systemConfigStore.getUser(TEST_USER.name)
    assertUserEquals(updatedUser, user)
  }

  def assertUserEquals(expected:User, actual:User) {
    assertEquals(expected.name, actual.name)
    assertEquals(expected.email, actual.email)
    assertEquals(expected.passwordEnc, actual.passwordEnc)
    assertEquals(expected.superuser, actual.superuser)
  }

  @Ignore // MySQL uses unicode by default, but we don't have a way to wire in the internal collation as a parameter to test yet.
  @Test
  def shouldListDomainsWithAsciiCollationByDefault = {
    val domainNames = Seq("bar", "Baz", "Foo", "diffa", domainName)
    domainNames.foreach { name =>
      systemConfigStore.createOrUpdateDomain(name)
    }
    val results = systemConfigStore.listDomains

    assertEquals(List("Baz", "Foo", "bar", "diffa", "domain"), results.toList)

  }

  @Test
  def shouldBeAbleToStoreRetrieveAndUpdatePolicy() {
    val space = systemConfigStore.createOrUpdateSpace(domainName)
    val key = PolicyKey(space.id, "TestPol")
    val initial = Seq(
        PolicyStatement("space-user", "*"),
        PolicyStatement("read-diffs", "*")
      )

    systemConfigStore.storePolicy(key, initial)
    assertEquals(initial.toSet, systemConfigStore.lookupPolicyStatements(key).toSet)

    val updated = Seq(
        PolicyStatement("space-user", "*"),
        PolicyStatement("read-diffs", "pair=p1"),
        PolicyStatement("read-diffs", "pair=p2"),
        PolicyStatement("initiate-scan", "pair=p2")
      )
    systemConfigStore.storePolicy(key, updated)
    assertEquals(updated.toSet, systemConfigStore.lookupPolicyStatements(key).toSet)
  }

  @Test
  def shouldBeAbleToRemovePolicy() {
    val space = systemConfigStore.createOrUpdateSpace(domainName)
    val key = PolicyKey(space.id, "TestPol")
    val initial = Seq(
        PolicyStatement("space-user", "*"),
        PolicyStatement("read-diffs", "*")
      )

    systemConfigStore.storePolicy(key, initial)
    systemConfigStore.removePolicy(key)
    assertEquals(Set[PolicyStatement](), systemConfigStore.lookupPolicyStatements(key).toSet)
  }
}

object JooqSystemConfigStoreTest {
  private[JooqSystemConfigStoreTest] val env =
    TestDatabaseEnvironments.uniqueEnvironment("target/systemConfigStore")

  private[JooqSystemConfigStoreTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  @AfterClass
  def cleanupSchema {
    storeReferences.tearDown
  }
}