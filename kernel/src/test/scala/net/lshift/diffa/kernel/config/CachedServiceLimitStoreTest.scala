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

import org.easymock.EasyMock._
import org.junit.{Before, Test}
import org.junit.Assert._
import net.lshift.diffa.kernel.util.cache.HazelcastCacheProvider

class CachedServiceLimitStoreTest {

  val underlying = createStrictMock(classOf[ServiceLimitsStore])

  val cacheProvider = new HazelcastCacheProvider()

  val cachedServiceLimitStore = new CachedServiceLimitsStore(underlying, cacheProvider)

  @Before
  def resetCache {
    cachedServiceLimitStore.reset
  }


  @Test
  def shouldCacheNonExistentPairScopedLimit = {

    expect(underlying.getPairLimitForPairAndName("domain", "pair-1", "some-bogus-limit")).andReturn(None).once()
    replay(underlying)

    val limit = cachedServiceLimitStore.getPairLimitForPairAndName("domain", "pair-1", "some-bogus-limit")
    assertEquals(None, limit)

    verify(underlying)
  }

  @Test
  def shouldCascadeNonExistentPairScopedLimit = {

    expect(underlying.getPairLimitForPairAndName("domain", "pair-1", "some-bogus-limit")).andReturn(None).once()
    expect(underlying.getDomainDefaultLimitForDomainAndName("domain", "some-bogus-limit")).andReturn(None).once()
    expect(underlying.getSystemDefaultLimitForName("some-bogus-limit")).andReturn(None).once()
    replay(underlying)

    val firstCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", "some-bogus-limit")
    assertEquals(ServiceLimit.UNLIMITED, firstCall)

    val secondCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", "some-bogus-limit")
    assertEquals(ServiceLimit.UNLIMITED, secondCall)

    verify(underlying)
  }

  @Test
  def shouldCachePairScopedLimitWhenDefinedExplicitly = {

    expect(underlying.setPairLimit("domain", "pair-1", "some-limit", 657567)).once()
    replay(underlying)

    cachedServiceLimitStore.setPairLimit("domain", "pair-1", "some-limit", 657567)

    val firstCall = cachedServiceLimitStore.getPairLimitForPairAndName("domain", "pair-1", "some-limit")
    assertEquals(657567, firstCall.get)

    val secondCall = cachedServiceLimitStore.getPairLimitForPairAndName("domain", "pair-1", "some-limit")
    assertEquals(657567, secondCall.get)

    val effectiveLimit = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", "some-limit")
    assertEquals(657567, effectiveLimit)

    verify(underlying)
  }


  @Test
  def shouldCachePairScopedLimitWhenDefaultDefinedAtDomainLevel = {

    expect(underlying.setDomainDefaultLimit("domain", "some-limit", 543)).once()
    expect(underlying.getPairLimitForPairAndName("domain", "pair-1", "some-limit")).andReturn(None).once()

    replay(underlying)

    cachedServiceLimitStore.setDomainDefaultLimit("domain", "some-limit", 543)

    val firstCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", "some-limit")
    assertEquals(543, firstCall)

    val secondCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", "some-limit")
    assertEquals(543, secondCall)

    verify(underlying)
  }

  @Test
  def shouldCachePairScopedLimitWhenDefaultDefinedAtSystemLevel = {

    expect(underlying.setSystemDefaultLimit("some-limit", 1169)).once()
    expect(underlying.getPairLimitForPairAndName("domain", "pair-1", "some-limit")).andReturn(None).once()
    expect(underlying.getDomainDefaultLimitForDomainAndName("domain", "some-limit")).andReturn(None).once()

    replay(underlying)

    cachedServiceLimitStore.setSystemDefaultLimit("some-limit", 1169)

    val firstCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", "some-limit")
    assertEquals(1169, firstCall)

    val secondCall = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain", "pair-1", "some-limit")
    assertEquals(1169, secondCall)

    verify(underlying)
  }

  @Test
  def shouldRetainCachePairScopedLimitWhenOtherPairIsDeleted = {

    expect(underlying.setPairLimit("domain-1", "pair-1", "some-limit", 23)).once()
    expect(underlying.setPairLimit("domain-2", "pair-2", "some-limit", 24)).once()
    expect(underlying.deletePairLimitsByDomain("domain-2")).once()
    expect(underlying.getPairLimitForPairAndName("domain-2", "pair-2", "some-limit")).andReturn(None).once()
    expect(underlying.getDomainDefaultLimitForDomainAndName("domain-2", "some-limit")).andReturn(None).once()
    expect(underlying.getSystemDefaultLimitForName("some-limit")).andReturn(None).once()
    replay(underlying)

    cachedServiceLimitStore.setPairLimit("domain-1", "pair-1", "some-limit", 23)
    cachedServiceLimitStore.setPairLimit("domain-2", "pair-2", "some-limit", 24)

    val firstDomain = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-1", "pair-1", "some-limit")
    assertEquals(23, firstDomain)

    val secondDomain = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-2", "pair-2", "some-limit")
    assertEquals(24, secondDomain)

    cachedServiceLimitStore.deletePairLimitsByDomain("domain-2")

    val l1 = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-1", "pair-1", "some-limit")
    assertEquals(23, l1)
    val l2 = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-2", "pair-2", "some-limit")
    assertEquals(ServiceLimit.UNLIMITED, l2)


    verify(underlying)
  }

  @Test
  def shouldRetainCachePairScopedLimitWhenOtherDomainIsDeleted = {

    expect(underlying.setPairLimit("domain-1", "pair-1", "some-limit", 3128)).once()
    expect(underlying.setPairLimit("domain-2", "pair-2", "some-limit", 3129)).once()
    expect(underlying.deleteDomainLimits("domain-2")).once()
    expect(underlying.getPairLimitForPairAndName("domain-2", "pair-2", "some-limit")).andReturn(None).once()
    expect(underlying.getDomainDefaultLimitForDomainAndName("domain-2", "some-limit")).andReturn(None).once()
    expect(underlying.getSystemDefaultLimitForName("some-limit")).andReturn(None).once()
    replay(underlying)

    cachedServiceLimitStore.setPairLimit("domain-1", "pair-1", "some-limit", 3128)
    cachedServiceLimitStore.setPairLimit("domain-2", "pair-2", "some-limit", 3129)

    val firstDomain = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-1", "pair-1", "some-limit")
    assertEquals(3128, firstDomain)

    val secondDomain = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-2", "pair-2", "some-limit")
    assertEquals(3129, secondDomain)

    cachedServiceLimitStore.deleteDomainLimits("domain-2")

    val l1 = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-1", "pair-1", "some-limit")
    assertEquals(3128, l1)
    val l2 = cachedServiceLimitStore.getEffectiveLimitByNameForPair("domain-2", "pair-2", "some-limit")
    assertEquals(ServiceLimit.UNLIMITED, l2)


    verify(underlying)
  }
}
