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

import scala.collection.JavaConversions._
import java.io.Serializable

/**
 * Service definition that abstracts away concrete cache implementations from higher level application code.
 */
trait CacheProvider {

  /**
   * Returns a reference to a managed cache instance identified by the given name.
   */
  def getCachedMap[K,V](name:String) : CachedMap[K,V]

}

/**
 * This is an in-memory key-value store with a minimal set of primitives to facilitate caching.
 */
trait CachedMap[K,V] extends CachedSubset[K,V] {

  /**
   * Returns the amount of entries in the cache.
   */
  def size() : Int

  /**
   * Retrieves a value based on the supplied key from the cache.
   */
  def get(key:K) : V

  /**
   * Inserts a value based on the supplied key into the cache.
   */
  def put(key:K, value:V)

  /**
   * Removes a value based on the supplied key from the cache.
   */
  def evict(key:K)

  /**
   * Retrieves a subset of the cache based on the supplied predicate.
   */
  def subset(predicate:KeyPredicate[K]) : CachedSubset[K,V]

  /**
   * Retrieves a value based on the supplied key from the cache, if the value is already cached.
   * If the value is not already cached, the supplied function is executed to retrieve the data
   * from the underlying storage and returns this to the caller, inserting it into the cache against
   * the given key.
   */
  def readThrough(key:K, f:() => V ) : V = {
    val cached = get(key)
    if (null == cached) {
      val value = f()
      put(key,value)
      value
    }
    else {
      cached
    }
  }
}

/**
 * This is used to reference a subset of a cache, e.g. to be able to invoke operations on a portion
 * of the total values of a parent cache.
 */
trait CachedSubset[K,V] {

  /**
   *  Removes all the values from the referenced cache.
   */
  def evictAll()
}

/**
 * Provides a way to query a cache based on cache keys (as opposed to cache values).
 */
trait KeyPredicate[K] extends Serializable {

  /**
   * Returns true if the current cache key should be included in the query results.
   */
  def constrain(key:K) : Boolean
}
