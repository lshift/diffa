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
import java.util.concurrent.TimeUnit
import com.hazelcast.core.{MapEntry, IMap, Hazelcast}
import com.hazelcast.query.{PredicateBuilder, Predicate}

class HazelcastCacheProvider extends CacheProvider {

  def getCachedMap[K,V](name: String) = {
    val underlying = Hazelcast.getMap[K,V](name)
    new HazelcastBackedMap[K,V](underlying)
  }
}

class HazelcastBackedMap[K,V](underlying:IMap[K,V]) extends CachedMap[K,V] {

  def size = underlying.size

  def evictAll = underlying.clear()

  def keySubset(keyPredicate:KeyPredicate[K]) = {
    val predicate = new HazelcastKeyPredicate(keyPredicate)
    val projectedKeys = underlying.keySet(predicate)
    new CachedSubset[K,V] {
      def evictAll = projectedKeys.foreach(underlying.remove(_))
    }
  }

  def valueSubset(attribute:String, value:String) = {
    val entryObject = new PredicateBuilder().getEntryObject
    val predicate = entryObject.get(attribute).equal(value)
    val v = underlying.values(predicate)
    v.toList
  }

  def put(key:K, value:V) = underlying.put(key, value)

  def get(key:K) = underlying.get(key)

  def evict(key:K) = underlying.evict(key)

}

class HazelcastKeyPredicate[K,V](keyPredicate:KeyPredicate[K]) extends Predicate[K,V] {
  def apply(mapEntry: MapEntry[K,V]) = {
    keyPredicate.constrain(mapEntry.getKey)
  }
}


