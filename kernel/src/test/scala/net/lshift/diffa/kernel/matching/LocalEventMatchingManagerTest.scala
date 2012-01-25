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

/**
 * Test cases for the LocalEventMatchingManager.
 */
class LocalEventMatchingManagerTest {

  val domainName = "domain"
  val domain = Domain(name=domainName)

  val pair1 = new DiffaPair(key = "pair1", domain = domain, matchingTimeout = 10)
  val pair2 = new DiffaPair(key = "pair2", domain = domain, matchingTimeout = 5)
  val pair3 = new DiffaPair(key = "pair3", domain = domain, matchingTimeout = 2)

  val invalid = new DiffaPair(key = "invalid", domain = domain, matchingTimeout = 5)

  val systemConfigStore = createStrictMock(classOf[SystemConfigStore])
  expect(systemConfigStore.getPair(domainName, "pair1")).andStubReturn(pair1)
  expect(systemConfigStore.getPair(domainName, "pair2")).andStubReturn(pair2)
  expect(systemConfigStore.listPairs).andStubReturn(Seq(pair1,pair2))
  replay(systemConfigStore)

  val matchingManager = new LocalEventMatchingManager(systemConfigStore)

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
    reset(systemConfigStore)
    expect(systemConfigStore.getPair(domainName, "pair3")).andStubReturn(pair3)
    replay(systemConfigStore)

    matchingManager.onUpdatePair(pair3)
    assertFalse(None.equals(matchingManager.getMatcher(pair3.asRef)))
  }

  @Test
  def shouldNotReturnAMatcherForARemovedPair {
    reset(systemConfigStore)
    expect(systemConfigStore.getPair(domainName, "pair3")).andStubReturn(pair3)
    replay(systemConfigStore)

    matchingManager.onUpdatePair(pair3)
    matchingManager.onDeletePair(pair3)

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
    reset(systemConfigStore)
    expect(systemConfigStore.getPair(domainName, "pair3")).andStubReturn(pair3)
    replay(systemConfigStore)

    val l1 = createStrictMock(classOf[MatchingStatusListener])
    matchingManager.addListener(l1)

    matchingManager.onUpdatePair(pair3)
    assertTrue(matchingManager.getMatcher(pair3.asRef).get.listeners.contains(l1))
  }
}