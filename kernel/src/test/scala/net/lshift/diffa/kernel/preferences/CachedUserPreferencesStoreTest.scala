/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.lshift.diffa.kernel.preferences

import net.lshift.diffa.kernel.util.cache.HazelcastCacheProvider

import net.lshift.diffa.schema.jooq.DatabaseFacade
import org.junit.{Before, Test}
import org.junit.Assert._
import org.easymock.EasyMock._
import org.easymock.classextension.{EasyMock => E4}
import org.jooq.impl.Factory
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.config.{Space, PairRef}

class CachedUserPreferencesStoreTest {

  val cacheProvider = new HazelcastCacheProvider
  val jooq = E4.createStrictMock(classOf[DatabaseFacade])
  val preferencesStore = new JooqUserPreferencesStore(jooq, cacheProvider)

  @Before
  def clearCache = {
    preferencesStore.reset
  }

  @Test
  def shouldReadThroughUserVisibilityItems {

    val pairs = new java.util.HashSet[String]()
    pairs.add("pair")

    expect(jooq.execute(anyObject[Function1[Factory, java.util.Set[String]]]())).andReturn(pairs).once()

    E4.replay(jooq)

    val firstCall = preferencesStore.listFilteredItems(100L, "user", FilteredItemType.SWIM_LANE)
    assertEquals(pairs.toList, firstCall.toList)

    val secondCall = preferencesStore.listFilteredItems(100L, "user", FilteredItemType.SWIM_LANE)
    assertEquals(pairs.toList, secondCall.toList)

    E4.verify(jooq)
  }

  @Test
  def shouldInvalidateUserVisibilityItemsOnUpdate {

    val firstPairSet = new java.util.HashSet[String]()
    firstPairSet.add("p1")

    val secondPairSet = new java.util.HashSet[String]()
    secondPairSet.add("p1")
    secondPairSet.add("p2")

    // First read -> returns Set(p1)
    expect(jooq.execute(anyObject[Function1[Factory, java.util.Set[String]]]())).andReturn(firstPairSet).once()
    // Update the set with p2
    expect(jooq.execute(anyObject[Function1[Factory, Unit]]())).andReturn(()).once()
    // First read -> returns Set(p1,p2)
    expect(jooq.execute(anyObject[Function1[Factory, java.util.Set[String]]]())).andReturn(secondPairSet).once()


    E4.replay(jooq)

    val firstCall = preferencesStore.listFilteredItems(100L, "user", FilteredItemType.SWIM_LANE)
    assertEquals(firstPairSet.toList, firstCall.toList)

    preferencesStore.createFilteredItem(PairRef("p2",100L), "user", FilteredItemType.SWIM_LANE)

    val secondCall = preferencesStore.listFilteredItems(100L, "user", FilteredItemType.SWIM_LANE)
    assertEquals(secondPairSet.toList, secondCall.toList)

    E4.verify(jooq)
  }
}
