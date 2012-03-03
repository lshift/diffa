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

package net.lshift.diffa.kernel.util

import java.io.Closeable
import net.sf.ehcache.{Element, CacheManager}
import scala.collection.JavaConversions._
import org.slf4j.LoggerFactory

/**
 * Simple wrapper around an underlying EhCache to make its usage less verbose.
 */
class CacheWrapper[A, B](cacheName:String, manager:CacheManager) extends Closeable {

  val log = LoggerFactory.getLogger(getClass)

  if (manager.cacheExists(cacheName)) {
    manager.removeCache(cacheName)
  }

  manager.addCache(cacheName)

  val cache = manager.getEhcache(cacheName)

  /**
   * Notifies the cache manager that this cache can be removed
   */
  def close() = manager.removeCache(cacheName)

  /**
   * Removes all values from this cache
   */
  def clear() = cache.removeAll()


  /**
   * Removes this value from the cache
   */
  def remove(key:A) = cache.remove(key)

  /**
   * Indicates whether this key exists
   */
  def contains(key:A) = cache.isKeyInCache(key)

  /**
   * Retrieve a value from the value by its key
   */
  def get(key:A) : Option[B] =
    Option(cache.get(key)).map(_.getValue.asInstanceOf[B])
  
  /**
   * Add an value to the cache
   */
  def put(key:A, value:B) = cache.put(new Element(key,value))

  /**
   * Returns a list of keys associated with this cache
   */
  def keys : Seq[A] = cache.getKeys.toSeq.map(_.asInstanceOf[A])

  /**
   * Retrieves a value by key from the cache. If that key does not exist in the cache,
   * the value is sourced from the underlying store, added to the cache and then returned to the caller.
   */
  def readThrough(key:A, f:() => B ) : B = {
    val cached = cache.get(key)
    if (null == cached) {
      val value = f()
      put(key,value)
      value
    }
    else {
      cached.getValue.asInstanceOf[B]
    }
  }

}