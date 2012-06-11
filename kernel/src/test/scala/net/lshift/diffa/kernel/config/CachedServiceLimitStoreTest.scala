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

import net.lshift.diffa.schema.servicelimits.{ServiceLimit, Unlimited}
import org.easymock.EasyMock._
import org.junit.{Before, Test}
import org.junit.Assert._
import net.lshift.diffa.kernel.util.cache.HazelcastCacheProvider

class CachedServiceLimitStoreTest {

  val underlying = createStrictMock(classOf[ServiceLimitsStore])

  val cacheProvider = new HazelcastCacheProvider()

  val cachedServiceLimitStore = new CachedServiceLimitsStore(underlying, cacheProvider)

  val bogusLimit = new ServiceLimit {
    def key = "bogus"
    def description = "bogus"
    def defaultLimit = 123
    def hardLimit = 8823
  }

  val someLimit = new ServiceLimit {
    def key = "some-limit"
    def description = "Just a test limit"
    def defaultLimit = 12
    def hardLimit = 13
  }

  @Before
  def resetCache {
    cachedServiceLimitStore.reset
  }


  @Test
  def shouldCacheNonExistentPairScopedLimit = {

    expect(underlying.getPairLimitForPairAndName("domain", "pair-1", bogusLimit)).andReturn(None).once()
    replay(underlying)

    val limit = cachedServiceLimitStore.getPairLimitForPairAndName("domain", "pair-1", bogusLimit)
    assertEquals(None, limit)

    verify(underlying)
  }

  @Test
  def shouldCascadeNonExistentPairScopedLimit = {

    expect(underlying.getPairLimitForPairAndName("domain", "pair-1", bogusLimit)).andReturn(None).once()
    expect(underlying.getDomainDefaultLimitForDomainAndName("domain", bogusLimit)).andReturn(None).once()
    expect(underlying.getSystemDefaultLimitForName(bogusLimit)).andReturn(None).once()
    replay(underlying)

    val firstCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", bogusLimit)
    assertEquals(Unlimited.value, firstCall)

    val secondCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", bogusLimit)
    assertEquals(Unlimited.value, secondCall)

    verify(underlying)
  }

  @Test
  def shouldCachePairScopedLimitWhenDefinedExplicitly = {

    expect(underlying.setPairLimit("domain", "pair-1", someLimit, 657567)).once()
    replay(underlying)

    cachedServiceLimitStore.setPairLimit("domain", "pair-1", someLimit, 657567)

    val firstCall = cachedServiceLimitStore.getPairLimitForPairAndName("domain", "pair-1", someLimit)
    assertEquals(657567, firstCall.get)

    val secondCall = cachedServiceLimitStore.getPairLimitForPairAndName("domain", "pair-1", someLimit)
    assertEquals(657567, secondCall.get)

    val effectiveLimit = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", someLimit)
    assertEquals(657567, effectiveLimit)

    verify(underlying)
  }


  @Test
  def shouldCachePairScopedLimitWhenDefaultDefinedAtDomainLevel = {

    expect(underlying.setDomainDefaultLimit("domain", someLimit, 543)).once()
    expect(underlying.getPairLimitForPairAndName("domain", "pair-1", someLimit)).andReturn(None).once()

    replay(underlying)

    cachedServiceLimitStore.setDomainDefaultLimit("domain", someLimit, 543)

    val firstCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", someLimit)
    assertEquals(543, firstCall)

    val secondCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", someLimit)
    assertEquals(543, secondCall)

    verify(underlying)
  }

  @Test
  def shouldCachePairScopedLimitWhenDefaultDefinedAtSystemLevel = {

    expect(underlying.setSystemDefaultLimit(someLimit, 1169)).once()
    expect(underlying.getPairLimitForPairAndName("domain", "pair-1", someLimit)).andReturn(None).once()
    expect(underlying.getDomainDefaultLimitForDomainAndName("domain", someLimit)).andReturn(None).once()

    replay(underlying)

    cachedServiceLimitStore.setSystemDefaultLimit(someLimit, 1169)

    val firstCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", someLimit)
    assertEquals(1169, firstCall)

    val secondCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", someLimit)
    assertEquals(1169, secondCall)

    verify(underlying)
  }

  @Test
  def shouldRetainCachePairScopedLimitWhenOtherPairIsDeleted = {

    expect(underlying.setPairLimit("domain-1", "pair-1", someLimit, 23)).once()
    expect(underlying.setPairLimit("domain-2", "pair-2", someLimit, 24)).once()
    expect(underlying.deletePairLimitsByDomain("domain-2")).once()
    expect(underlying.getPairLimitForPairAndName("domain-2", "pair-2", someLimit)).andReturn(None).once()
    expect(underlying.getDomainDefaultLimitForDomainAndName("domain-2", someLimit)).andReturn(None).once()
    expect(underlying.getSystemDefaultLimitForName(someLimit)).andReturn(None).once()
    replay(underlying)

    cachedServiceLimitStore.setPairLimit("domain-1", "pair-1", someLimit, 23)
    cachedServiceLimitStore.setPairLimit("domain-2", "pair-2", someLimit, 24)

    val firstDomain = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-1", "pair-1", someLimit)
    assertEquals(23, firstDomain)

    val secondDomain = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-2", "pair-2", someLimit)
    assertEquals(24, secondDomain)

    cachedServiceLimitStore.deletePairLimitsByDomain("domain-2")

    val l1 = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-1", "pair-1", someLimit)
    assertEquals(23, l1)
    val l2 = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-2", "pair-2", someLimit)
    assertEquals(Unlimited.value, l2)


    verify(underlying)
  }

  @Test
  def shouldRetainCachePairScopedLimitWhenOtherDomainIsDeleted = {

    expect(underlying.setPairLimit("domain-1", "pair-1", someLimit, 3128)).once()
    expect(underlying.setPairLimit("domain-2", "pair-2", someLimit, 3129)).once()
    expect(underlying.deleteDomainLimits("domain-2")).once()
    expect(underlying.getPairLimitForPairAndName("domain-2", "pair-2", someLimit)).andReturn(None).once()
    expect(underlying.getDomainDefaultLimitForDomainAndName("domain-2", someLimit)).andReturn(None).once()
    expect(underlying.getSystemDefaultLimitForName(someLimit)).andReturn(None).once()
    replay(underlying)

    cachedServiceLimitStore.setPairLimit("domain-1", "pair-1", someLimit, 3128)
    cachedServiceLimitStore.setPairLimit("domain-2", "pair-2", someLimit, 3129)

    val firstDomain = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-1", "pair-1", someLimit)
    assertEquals(3128, firstDomain)

    val secondDomain = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-2", "pair-2", someLimit)
    assertEquals(3129, secondDomain)

    cachedServiceLimitStore.deleteDomainLimits("domain-2")

    val l1 = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-1", "pair-1", someLimit)
    assertEquals(3128, l1)
    val l2 = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-2", "pair-2", someLimit)
    assertEquals(Unlimited.value, l2)


    verify(underlying)
  }
}
