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
package net.lshift.diffa.kernel.preferences

import org.junit.Assert._
import org.junit.{After, Before, AfterClass, Test}
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import net.lshift.diffa.kernel.StoreReferenceContainer
import net.lshift.diffa.kernel.frontend.{DomainPairDef, EndpointDef}
import net.lshift.diffa.kernel.config.{User, Domain}
import org.apache.commons.lang.RandomStringUtils
import net.lshift.diffa.kernel.config.system.PolicyKey

class JooqUserPreferencesStoreTest {
  private var storeReferences = JooqUserPreferencesStoreTest.storeReferences

  val preferencesStore = storeReferences.userPreferencesStore
  val domainConfigStore = storeReferences.domainConfigStore
  val systemConfigStore = storeReferences.systemConfigStore

  val domain = RandomStringUtils.randomAlphanumeric(10)
  val space = systemConfigStore.createOrUpdateSpace(domain)

  val upstream = EndpointDef(name = "up")
  val downstream = EndpointDef(name = "down")
  val pair1 = DomainPairDef(key = "p1", space = space.id, upstreamName = "up", downstreamName = "down")
  val pair2 = DomainPairDef(key = "p2", space = space.id, upstreamName = "up", downstreamName = "down")
  val pair3 = DomainPairDef(key = "p3", space = space.id, upstreamName = "up", downstreamName = "down")

  @Before
  def createTestData {

    preferencesStore.reset

    systemConfigStore.createOrUpdateUser(User(name = "user", email = "", passwordEnc = ""))

    domainConfigStore.makeDomainMember(space.id, "user", PolicyKey(0L, "User"))

    domainConfigStore.createOrUpdateEndpoint(space.id, upstream)
    domainConfigStore.createOrUpdateEndpoint(space.id, downstream)
    domainConfigStore.createOrUpdatePair(space.id, pair1.withoutDomain)
    domainConfigStore.createOrUpdatePair(space.id, pair2.withoutDomain)
    domainConfigStore.createOrUpdatePair(space.id, pair3.withoutDomain)

    preferencesStore.createFilteredItem(pair1.asRef, "user", FilteredItemType.SWIM_LANE)
    preferencesStore.createFilteredItem(pair2.asRef, "user", FilteredItemType.SWIM_LANE)
    preferencesStore.createFilteredItem(pair3.asRef, "user", FilteredItemType.SWIM_LANE)
  }

  @After
  def removeUserData {
    preferencesStore.removeAllFilteredItemsForUser("user")
  }

  @Test
  def shouldReturnFilteredItems {
    val filteredItems = preferencesStore.listFilteredItems(space.id, "user", FilteredItemType.SWIM_LANE)
    assertEquals(Set(pair1.key, pair2.key, pair3.key), filteredItems)
  }

  @Test
  def shouldBeAbleToRemoveSingleFilteredItem {
    preferencesStore.removeFilteredItem(pair3.asRef, "user", FilteredItemType.SWIM_LANE)
    val filteredItems = preferencesStore.listFilteredItems(space.id, "user", FilteredItemType.SWIM_LANE)
    assertEquals(Set(pair1.key, pair2.key), filteredItems)
  }

  @Test
  def shouldBeAbleToRemoveItemsInDomainForUser {
    preferencesStore.removeAllFilteredItemsForDomain(space.id, "user")
    val filteredItems = preferencesStore.listFilteredItems(space.id, "user", FilteredItemType.SWIM_LANE)
    assertTrue(filteredItems.isEmpty)
  }

  @Test
  def shouldBeAbleToRemoveItemsForUser {
    preferencesStore.removeAllFilteredItemsForUser("user")
    val filteredItems = preferencesStore.listFilteredItems(space.id, "user", FilteredItemType.SWIM_LANE)
    assertTrue(filteredItems.isEmpty)
  }
}

object JooqUserPreferencesStoreTest {
  private[JooqUserPreferencesStoreTest] val env =
    TestDatabaseEnvironments.uniqueEnvironment("target/userPreferencesStore")

  private[JooqUserPreferencesStoreTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  @AfterClass
  def tearDown {
    storeReferences.tearDown
  }
}
