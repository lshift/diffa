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

  def defineLimit(limitName: String, description: String) = underlying.defineLimit(limitName, description)

  def deleteDomainLimits(domainName: String) = {
    underlying.deleteDomainLimits(domainName)
    evictPairLimitCacheByDomain(domainName)
    evictDomainLimitCachesByDomain(domainName)
  }

  def deletePairLimitsByDomain(domainName: String) = {
    underlying.deletePairLimitsByDomain(domainName)
    evictPairLimitCacheByDomain(domainName)
  }

  def setSystemHardLimit(limitName: String, limitValue: Int) = {
    underlying.setSystemHardLimit(limitName, limitValue)
    systemHardLimits.put(limitName, Some(limitValue))
  }

  def setSystemDefaultLimit(limitName: String, limitValue: Int) = {
    underlying.setSystemDefaultLimit(limitName, limitValue)
    systemDefaults.put(limitName, Some(limitValue))
  }

  def setDomainHardLimit(domainName: String, limitName: String, limitValue: Int) = {
    underlying.setDomainHardLimit(domainName, limitName, limitValue)
    domainHardLimits.put(DomainLimitKey(domainName, limitName), Some(limitValue))
  }

  def setDomainDefaultLimit(domainName: String, limitName: String, limitValue: Int) = {
    underlying.setDomainDefaultLimit(domainName, limitName, limitValue)
    domainDefaults.put(DomainLimitKey(domainName, limitName), Some(limitValue))
  }

  def setPairLimit(domainName: String, pairKey: String, limitName: String, limitValue: Int) = {
    underlying.setPairLimit(domainName, pairKey, limitName, limitValue)
    pairLimits.put(PairLimitKey(domainName, pairKey, limitName), Some(limitValue))
  }

  def getSystemHardLimitForName(limitName: String) =
    systemHardLimits.readThrough(limitName,
      () => underlying.getSystemHardLimitForName(limitName))

  def getSystemDefaultLimitForName(limitName: String) =
    systemDefaults.readThrough(limitName,
      () => underlying.getSystemDefaultLimitForName(limitName))

  def getDomainHardLimitForDomainAndName(domainName: String, limitName: String) =
    domainHardLimits.readThrough(DomainLimitKey(domainName, limitName),
      () => underlying.getDomainHardLimitForDomainAndName(domainName, limitName))

  def getDomainDefaultLimitForDomainAndName(domainName: String, limitName: String) =
    domainDefaults.readThrough(DomainLimitKey(domainName, limitName),
      () => underlying.getDomainDefaultLimitForDomainAndName(domainName, limitName))

  def getPairLimitForPairAndName(domainName: String, pairKey: String, limitName: String) =
    pairLimits.readThrough(PairLimitKey(domainName, pairKey, limitName),
      () => underlying.getPairLimitForPairAndName(domainName, pairKey, limitName))

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



