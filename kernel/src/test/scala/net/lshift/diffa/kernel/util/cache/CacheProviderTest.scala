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
package net.lshift.diffa.kernel.util.cache

import org.junit.Assert._
import org.junit.runner.RunWith
import org.junit.experimental.theories.{DataPoint, Theories, Theory}

@RunWith(classOf[Theories])
class CacheProviderTest {

  @Theory
  def shouldProvideReadThroughCaching(provider:CacheProvider) {
    val cache = provider.getCachedMap[String,String]("some-cache")

    reset(cache)

    val underlying = new UnderlyingDataSource("some-value")
    val unCachedResponse = cache.readThrough("some_key", underlying.getData)
    assertEquals("some-value", unCachedResponse)
    val cachedResponse = cache.readThrough("some_key", underlying.getData)
    assertEquals("some-value", cachedResponse)
    assertEquals(1, underlying.invocations)
  }

  @Theory
  def shouldSupportRemovalBasedOnKeyQuery(provider:CacheProvider) {
    val cache = provider.getCachedMap[NonProductionCacheKey,String]("another-cache")

    reset(cache)

    val fooKey = NonProductionCacheKey("foo", 1)
    val barKey = NonProductionCacheKey("bar", 2)

    cache.put(fooKey, "first-value")
    cache.put(barKey, "second-value")

    assertEquals(2, cache.size())
    assertEquals("first-value", cache.get(fooKey))
    assertEquals("second-value", cache.get(barKey))

    cache.subset(NonProductionKeyPredicate("foo")).evictAll

    assertEquals(1, cache.size())
    assertNull(cache.get(fooKey))
    assertEquals("second-value", cache.get(barKey))
  }

  @Theory
  def shouldSupportRemovalBasedOnExplicitKey(provider:CacheProvider) {
    val cache = provider.getCachedMap[NonProductionCacheKey,String]("new-cache")

    reset(cache)

    val key = new NonProductionCacheKey("baz", 888)

    cache.put(key, "baz-value")
    assertEquals(1, cache.size())
    assertEquals("baz-value", cache.get(key))

    cache.evict(key)
    assertEquals(0, cache.size())
    assertNull(cache.get(key))

  }

  private def reset(cache:CachedMap[_,_]) = {
    cache.evictAll
    assertEquals(0, cache.size)
  }

}

object CacheProviderTest {
  @DataPoint def hazelcast = new HazelcastCacheProvider
}

class UnderlyingDataSource(responseValue:String) {

  var invocations = 0

  def getData() = {
    invocations += 1
    responseValue
  }
}
