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

package net.lshift.diffa.kernel.matching

import org.junit.Test
import org.junit.Assert._
import org.easymock.EasyMock.{createStrictMock, expect, replay, reset}
import net.lshift.diffa.kernel.config.{DomainConfigStore, DiffaPair}
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.config.Domain
import net.lshift.diffa.kernel.frontend.DomainPairDef

/**
 * Test cases for the LocalEventMatchingManager.
 */
class LocalEventMatchingManagerTest {

  val domainName = "domain"
  val domain = Domain(name=domainName)

  val pair1 = new DomainPairDef(key = "pair1", domain = domainName, matchingTimeout = 10)
  val pair2 = new DomainPairDef(key = "pair2", domain = domainName, matchingTimeout = 5)
  val pair3 = new DomainPairDef(key = "pair3", domain = domainName, matchingTimeout = 2)

  val invalid = new DiffaPair(key = "invalid", domain = domain, matchingTimeout = 5)

  val domainConfigStore = createStrictMock(classOf[DomainConfigStore])
  val systemConfigStore = createStrictMock(classOf[SystemConfigStore])

  expect(domainConfigStore.getPairDef(domainName, "pair1")).andStubReturn(pair1)
  expect(domainConfigStore.getPairDef(domainName, "pair2")).andStubReturn(pair2)
  expect(systemConfigStore.listPairs).andStubReturn(Seq(pair1,pair2))
  replay(systemConfigStore, domainConfigStore)

  val matchingManager = new LocalEventMatchingManager(systemConfigStore, domainConfigStore)

  @Test
  def shouldNotReturnAMatcherForAnInvalidKey {
    assertEquals(None, matchingManager.getMatcher(invalid.asRef))
  }

  @Test
  def shouldReturnAMatcherForAPairKeyThatWasKnownAtStartup {
    assertFalse(None.equals(matchingManager.getMatcher(pair1.asRef)))
  }

  @Test
  def shouldReturnAMatcherForAPairKeyLaterIntroduced {
    reset(systemConfigStore, domainConfigStore)
    expect(domainConfigStore.getPairDef(pair3.asRef)).andStubReturn(pair3)
    replay(systemConfigStore, domainConfigStore)

    matchingManager.onUpdatePair(pair3.asRef)
    assertFalse(None.equals(matchingManager.getMatcher(pair3.asRef)))
  }

  @Test
  def shouldNotReturnAMatcherForARemovedPair {
    reset(systemConfigStore, domainConfigStore)
    expect(domainConfigStore.getPairDef(pair3.asRef)).andStubReturn(pair3)
    replay(systemConfigStore, domainConfigStore)

    matchingManager.onUpdatePair(pair3.asRef)
    matchingManager.onDeletePair(pair3.asRef)

    assertEquals(None, matchingManager.getMatcher(pair3.asRef))
  }

  @Test
  def shouldApplyListenersToExistingMatchers {
    val l1 = createStrictMock(classOf[MatchingStatusListener])
    matchingManager.addListener(l1)

    assertTrue(matchingManager.getMatcher(pair1.asRef).get.listeners.contains(l1))
    assertTrue(matchingManager.getMatcher(pair2.asRef).get.listeners.contains(l1))
  }

  @Test
  def shouldApplyListenersToNewMatchers {
    reset(systemConfigStore, domainConfigStore)
    expect(domainConfigStore.getPairDef(pair3.asRef)).andStubReturn(pair3)
    replay(systemConfigStore, domainConfigStore)

    val l1 = createStrictMock(classOf[MatchingStatusListener])
    matchingManager.addListener(l1)

    matchingManager.onUpdatePair(pair3.asRef)
    assertTrue(matchingManager.getMatcher(pair3.asRef).get.listeners.contains(l1))
  }
}