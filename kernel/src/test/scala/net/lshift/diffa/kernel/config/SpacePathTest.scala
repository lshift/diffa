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
package net.lshift.diffa.kernel.config

import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import net.lshift.diffa.kernel.StoreReferenceContainer
import org.junit.{Test, AfterClass}
import org.junit.Assert._
import org.apache.commons.lang.RandomStringUtils
import net.lshift.diffa.kernel.util.MissingObjectException

class SpacePathTest {

  private val storeReferences = SpacePathTest.storeReferences
  private val systemConfigStore = storeReferences.systemConfigStore

  @Test
  def shouldCreateNewTopLevelSpace {
    val topLevelSpace = RandomStringUtils.randomAlphanumeric(10)
    val newSpace = systemConfigStore.createOrUpdateSpace(topLevelSpace)

    assertTrue(systemConfigStore.doesDomainExist(topLevelSpace))
    systemConfigStore.listSpaces.contains(newSpace)

  }

  @Test
  def shouldNotAcceptInvalidTopLevelSpaceName {
    val bogusPortion = RandomStringUtils.random(10, "!@#$%^&*()/")
    val topLevelSpace = RandomStringUtils.randomAlphanumeric(10) + bogusPortion
    try {
      systemConfigStore.createOrUpdateSpace(topLevelSpace)
      fail("Using %s in a space path should have caused an error".format(bogusPortion))
    }
    catch {
      case x:ConfigValidationException => // Expected
    }
  }

  @Test
  def shouldNotAcceptInvalidSubSpaceName {

    val topLevelSpace = RandomStringUtils.randomAlphanumeric(10)
    systemConfigStore.createOrUpdateSpace(topLevelSpace)
    assertTrue(systemConfigStore.doesDomainExist(topLevelSpace))

    val bogusPortion = RandomStringUtils.random(10, "!@#$%^&*()/")

    val bogusChildSpace = topLevelSpace + "/" + RandomStringUtils.randomAlphanumeric(10) + bogusPortion

    try {
      systemConfigStore.createOrUpdateSpace(bogusChildSpace)
      fail("Using %s in a space path should have caused an error".format(bogusPortion))
    }
    catch {
      case x:ConfigValidationException => // Expected
    }
  }

  @Test
  def shouldCreateSubspaceForExistingLineage {

    val firstParentSpace = RandomStringUtils.randomAlphanumeric(10)
    val secondParentSpace = RandomStringUtils.randomAlphanumeric(10)
    val childSpace = firstParentSpace + "/" + RandomStringUtils.randomAlphanumeric(10)
    val grandChildSpace = childSpace + "/" + RandomStringUtils.randomAlphanumeric(10)
    val greatGrandChildSpace = grandChildSpace + "/" + RandomStringUtils.randomAlphanumeric(10)

    val firstParent = systemConfigStore.createOrUpdateSpace(firstParentSpace)
    assertTrue(systemConfigStore.doesDomainExist(firstParent.name))

    val secondParent = systemConfigStore.createOrUpdateSpace(secondParentSpace)
    assertTrue(systemConfigStore.doesDomainExist(secondParent.name))

    val child = systemConfigStore.createOrUpdateSpace(childSpace)
    assertTrue(systemConfigStore.doesDomainExist(childSpace))

    val grandChild = systemConfigStore.createOrUpdateSpace(grandChildSpace)
    assertTrue(systemConfigStore.doesDomainExist(grandChildSpace))

    val greatGrandChild = systemConfigStore.createOrUpdateSpace(greatGrandChildSpace)
    assertTrue(systemConfigStore.doesDomainExist(greatGrandChildSpace))

    // Verify the reported hierarchy

    val hierarchy = systemConfigStore.listSubspaces(firstParent.id)

    assertEquals(Seq(greatGrandChild, grandChild, child, firstParent), hierarchy)
    assertFalse(hierarchy.contains(secondParent))
  }

  @Test
  def shouldBeAbleToDeleteAncestorsRecursively {

    // Set up a lineage with 4 generations

    val parentSpace = RandomStringUtils.randomAlphanumeric(10)
    val childSpace = parentSpace + "/" + RandomStringUtils.randomAlphanumeric(10)
    val grandChildSpace = childSpace + "/" + RandomStringUtils.randomAlphanumeric(10)
    val greatGrandChildSpace = grandChildSpace + "/" + RandomStringUtils.randomAlphanumeric(10)

    Seq(parentSpace, childSpace, grandChildSpace, greatGrandChildSpace).foreach(s => {
      systemConfigStore.createOrUpdateSpace(s)
      systemConfigStore.doesDomainExist(s)
    })

    // Just delete from the third level down

    val grandChild = systemConfigStore.lookupSpaceByPath(grandChildSpace)
    systemConfigStore.deleteSpace(grandChild.id)

    // Only levels 3 and 4 should have been deleted

    Seq(grandChildSpace, greatGrandChildSpace).foreach(s => {
      assertFalse(systemConfigStore.doesDomainExist(s))
    })

    // Levels 1 and 2 should still exist

    Seq(parentSpace, childSpace).foreach(s => {
      assertTrue(systemConfigStore.doesDomainExist(s))
    })

  }

  @Test(expected = classOf[MissingObjectException])
  def shouldNotCreateSubspaceForNonExistentParent {

    val parentSpace = RandomStringUtils.randomAlphanumeric(10)
    assertFalse(systemConfigStore.doesDomainExist(parentSpace))

    val childSpace = parentSpace + "/" + RandomStringUtils.randomAlphanumeric(10)
    systemConfigStore.createOrUpdateSpace(childSpace)
  }

}

object SpacePathTest {
  private[SpacePathTest] val env =
    TestDatabaseEnvironments.uniqueEnvironment("target/domainConfigStore")

  private[SpacePathTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  @AfterClass
  def tearDown {
    storeReferences.tearDown
  }
}
