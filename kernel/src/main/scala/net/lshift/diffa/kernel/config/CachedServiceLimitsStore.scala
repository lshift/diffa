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

import scala.beans.BeanProperty
import net.lshift.diffa.kernel.util.cache.{KeyPredicate, CacheProvider}
import net.lshift.diffa.schema.servicelimits.ServiceLimit

class CachedServiceLimitsStore(underlying:ServiceLimitsStore,
                               cacheProvider:CacheProvider)
  extends ServiceLimitsStore {

  val pairLimits = cacheProvider.getCachedMap[PairLimitKey, Option[Int]]("domain.pair.limits")
  val domainDefaults = cacheProvider.getCachedMap[DomainLimitKey, Option[Int]]("domain.default.limits")
  val systemDefaults = cacheProvider.getCachedMap[String, Option[Int]]("system.default.limits")
  val domainHardLimits = cacheProvider.getCachedMap[DomainLimitKey, Option[Int]]("domain.hard.limits")
  val systemHardLimits = cacheProvider.getCachedMap[String, Option[Int]]("system.hard.limits")

  def reset = {
    pairLimits.evictAll
    domainDefaults.evictAll
    systemDefaults.evictAll
    domainHardLimits.evictAll
    systemHardLimits.evictAll
  }

  def defineLimit(limit: ServiceLimit) = underlying.defineLimit(limit)

  def deleteDomainLimits(space:Long) = {
    underlying.deleteDomainLimits(space)
    evictPairLimitCacheByDomain(space)
    evictDomainLimitCachesByDomain(space)
  }

  def deletePairLimitsByDomain(space:Long) = {
    underlying.deletePairLimitsByDomain(space)
    evictPairLimitCacheByDomain(space)
  }

  def setSystemHardLimit(limit: ServiceLimit, limitValue: Int) = {
    underlying.setSystemHardLimit(limit, limitValue)
    systemHardLimits.put(limit.key, Some(limitValue))
  }

  def setSystemDefaultLimit(limit: ServiceLimit, limitValue: Int) = {
    underlying.setSystemDefaultLimit(limit, limitValue)
    systemDefaults.put(limit.key, Some(limitValue))
  }

  def setDomainHardLimit(space:Long, limit: ServiceLimit, limitValue: Int) = {
    underlying.setDomainHardLimit(space, limit, limitValue)
    domainHardLimits.put(DomainLimitKey(space, limit.key), Some(limitValue))
  }

  def setDomainDefaultLimit(space:Long, limit: ServiceLimit, limitValue: Int) = {
    underlying.setDomainDefaultLimit(space, limit, limitValue)
    domainDefaults.put(DomainLimitKey(space, limit.key), Some(limitValue))
  }

  def setPairLimit(space:Long, pairKey: String, limit: ServiceLimit, limitValue: Int) = {
    underlying.setPairLimit(space, pairKey, limit, limitValue)
    pairLimits.put(PairLimitKey(space, pairKey, limit.key), Some(limitValue))
  }

  def getSystemHardLimitForName(limit: ServiceLimit) =
    systemHardLimits.readThrough(limit.key,
      () => underlying.getSystemHardLimitForName(limit))

  def getSystemDefaultLimitForName(limit: ServiceLimit) =
    systemDefaults.readThrough(limit.key,
      () => underlying.getSystemDefaultLimitForName(limit))

  def getDomainHardLimitForDomainAndName(space:Long, limit: ServiceLimit) =
    domainHardLimits.readThrough(DomainLimitKey(space, limit.key),
      () => underlying.getDomainHardLimitForDomainAndName(space, limit))

  def getDomainDefaultLimitForDomainAndName(space:Long, limit: ServiceLimit) =
    domainDefaults.readThrough(DomainLimitKey(space, limit.key),
      () => underlying.getDomainDefaultLimitForDomainAndName(space, limit))

  def getPairLimitForPairAndName(space:Long, pairKey: String, limit: ServiceLimit) =
    pairLimits.readThrough(PairLimitKey(space, pairKey, limit.key),
      () => underlying.getPairLimitForPairAndName(space, pairKey, limit))

  private def evictPairLimitCacheByDomain(space:Long) = {
    pairLimits.keySubset(PairLimitByDomainPredicate(space)).evictAll
  }

  private def evictDomainLimitCachesByDomain(space:Long) = {
    domainHardLimits.keySubset(DomainLimitByDomainPredicate(space)).evictAll
    domainDefaults.keySubset(DomainLimitByDomainPredicate(space)).evictAll
  }

}

// All of these beans need to be serializable

case class PairLimitKey(
  @BeanProperty var space: Long = -1L,
  @BeanProperty var pairKey: String = null,
  @BeanProperty var limitName: String = null) {

  def this() = this(space = -1L)

}

case class DomainLimitKey(
  @BeanProperty var space: Long = -1L,
  @BeanProperty var limitName: String = null) {

  def this() = this(space = -1L)
}

/**
 * Allows the key set to queried by the domain field.
 */
case class PairLimitByDomainPredicate(@BeanProperty var space: Long) extends KeyPredicate[PairLimitKey] {
  def this() = this(space = -1L)
  def constrain(key: PairLimitKey) = key.space == space
}

case class DomainLimitByDomainPredicate(@BeanProperty var space: Long) extends KeyPredicate[DomainLimitKey] {
  def this() = this(space = -1L)
  def constrain(key: DomainLimitKey) = key.space == space
}



