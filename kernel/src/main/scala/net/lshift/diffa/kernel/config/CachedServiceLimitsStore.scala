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

import reflect.BeanProperty
import net.lshift.diffa.kernel.util.cache.{KeyPredicate, CacheProvider}

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

  def deleteDomainLimits(domainName: String) = {
    underlying.deleteDomainLimits(domainName)
    evictPairLimitCacheByDomain(domainName)
    evictDomainLimitCachesByDomain(domainName)
  }

  def deletePairLimitsByDomain(domainName: String) = {
    underlying.deletePairLimitsByDomain(domainName)
    evictPairLimitCacheByDomain(domainName)
  }

  def setSystemHardLimit(limit: ServiceLimit, limitValue: Int) = {
    underlying.setSystemHardLimit(limit, limitValue)
    systemHardLimits.put(limit.key, Some(limitValue))
  }

  def setSystemDefaultLimit(limit: ServiceLimit, limitValue: Int) = {
    underlying.setSystemDefaultLimit(limit, limitValue)
    systemDefaults.put(limit.key, Some(limitValue))
  }

  def setDomainHardLimit(domainName: String, limit: ServiceLimit, limitValue: Int) = {
    underlying.setDomainHardLimit(domainName, limit, limitValue)
    domainHardLimits.put(DomainLimitKey(domainName, limit.key), Some(limitValue))
  }

  def setDomainDefaultLimit(domainName: String, limit: ServiceLimit, limitValue: Int) = {
    underlying.setDomainDefaultLimit(domainName, limit, limitValue)
    domainDefaults.put(DomainLimitKey(domainName, limit.key), Some(limitValue))
  }

  def setPairLimit(domainName: String, pairKey: String, limit: ServiceLimit, limitValue: Int) = {
    underlying.setPairLimit(domainName, pairKey, limit, limitValue)
    pairLimits.put(PairLimitKey(domainName, pairKey, limit.key), Some(limitValue))
  }

  def getSystemHardLimitForName(limit: ServiceLimit) =
    systemHardLimits.readThrough(limit.key,
      () => underlying.getSystemHardLimitForName(limit))

  def getSystemDefaultLimitForName(limit: ServiceLimit) =
    systemDefaults.readThrough(limit.key,
      () => underlying.getSystemDefaultLimitForName(limit))

  def getDomainHardLimitForDomainAndName(domainName: String, limit: ServiceLimit) =
    domainHardLimits.readThrough(DomainLimitKey(domainName, limit.key),
      () => underlying.getDomainHardLimitForDomainAndName(domainName, limit))

  def getDomainDefaultLimitForDomainAndName(domainName: String, limit: ServiceLimit) =
    domainDefaults.readThrough(DomainLimitKey(domainName, limit.key),
      () => underlying.getDomainDefaultLimitForDomainAndName(domainName, limit))

  def getPairLimitForPairAndName(domainName: String, pairKey: String, limit: ServiceLimit) =
    pairLimits.readThrough(PairLimitKey(domainName, pairKey, limit.key),
      () => underlying.getPairLimitForPairAndName(domainName, pairKey, limit))

  private def evictPairLimitCacheByDomain(domainName:String) = {
    pairLimits.subset(PairLimitByDomainPredicate(domainName)).evictAll
  }

  private def evictDomainLimitCachesByDomain(domainName:String) = {
    domainHardLimits.subset(DomainLimitByDomainPredicate(domainName)).evictAll
    domainDefaults.subset(DomainLimitByDomainPredicate(domainName)).evictAll
  }

}

// All of these beans need to be serializable

case class PairLimitKey(
  @BeanProperty var domainName: String = null,
  @BeanProperty var pairKey: String = null,
  @BeanProperty var limitName: String = null) {

  def this() = this(domainName = null)

}

case class DomainLimitKey(
  @BeanProperty var domainName: String = null,
  @BeanProperty var limitName: String = null) {

  def this() = this(domainName = null)
}

/**
 * Allows the key set to queried by the domain field.
 */
case class PairLimitByDomainPredicate(@BeanProperty domainName:String) extends KeyPredicate[PairLimitKey] {
  def this() = this(domainName = null)
  def constrain(key: PairLimitKey) = key.domainName == domainName
}

case class DomainLimitByDomainPredicate(@BeanProperty domainName:String) extends KeyPredicate[DomainLimitKey] {
  def this() = this(domainName = null)
  def constrain(key: DomainLimitKey) = key.domainName == domainName
}



