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

/**
 * Interface to the administration of Service Limits.
 *
 * Limits may be applied to any operation supported by the real-time event,
 * participant scanning or inventory submission services.  The meaning of any
 * limit is tied to the limiter that uses it, which is outside the
 * responsibility of the ServiceLimitsStore.
 * The responsibilities of a ServiceLimitsStore are to: provide mechanisms to
 * define new limits, set limits at each scope (see below), cascade hard limit
 * changes down through the chain, and report the effective limit value -
 * typically with respect to a pair associated with the report from the
 * representative of the client application (e.g. scan participant).
 *
 * There are three scopes for limits: System, Domain and Pair.
 *
 * <h3>Configuration</h3>
 * A System Hard Limit constrains all more specific limits of the same name both
 * initially (at the time the other limits are set) and retrospectively
 * (whenever the System Hard Limit is changed).  The limits it constrains are:
 * SystemDefaultLimit, DomainHardLimit, DomainDefaultLimit and PairLimit.
 *
 * Similarly, a Domain Hard Limit constrains the value of the following limits:
 * DomainDefaultLimit and PairLimit.
 *
 * <h3>Effective Limit</h3>
 * In determining an effective limit for a pair, the following strategy should
 * apply:
 <ol>
   <li>If there is a corresponding PairLimit defined, then the value of that
   limit is the effective limit;</li>
   <li>Otherwise, if there is a DomainDefaultLimit corresponding to the domain
   of the pair, then the value of that limit is the effective limit;</li>
   <li>Otherwise, the value of the relevant SystemDefaultLimit is the effective
   limit.</li>
 </ol>
 */

import net.lshift.diffa.schema.servicelimits._

trait ServiceLimitsStore extends ServiceLimitsView {
  def defineLimit(limit: ServiceLimit): Unit
  def deleteDomainLimits(space:Long): Unit
  def deletePairLimitsByDomain(space:Long): Unit

  def setSystemHardLimit(limit: ServiceLimit, limitValue: Int): Unit
  def setSystemDefaultLimit(limit: ServiceLimit, limitValue: Int): Unit
  def setDomainHardLimit(space:Long, limit: ServiceLimit, limitValue: Int): Unit
  def setDomainDefaultLimit(space:Long, limit: ServiceLimit, limitValue: Int): Unit
  def setPairLimit(space:Long, pairKey: String, limit: ServiceLimit, limitValue: Int): Unit

  def getSystemHardLimitForName(limit: ServiceLimit): Option[Int]
  def getSystemDefaultLimitForName(limit: ServiceLimit): Option[Int]
  def getDomainHardLimitForDomainAndName(space:Long, limit: ServiceLimit): Option[Int]
  def getDomainDefaultLimitForDomainAndName(space:Long, limit: ServiceLimit): Option[Int]
  def getPairLimitForPairAndName(space:Long, pairKey: String, limit: ServiceLimit): Option[Int]

  def getEffectiveLimitByName(limit: ServiceLimit) : Int = getSystemDefaultLimitForName(limit).getOrElse(Unlimited.value)

  def getEffectiveLimitByNameForDomain(space:Long, limit: ServiceLimit) : Int =
    getDomainDefaultLimitForDomainAndName(space, limit).getOrElse(
      getEffectiveLimitByName(limit))

  def getEffectiveLimitByNameForPair(space:Long, pairKey: String, limit: ServiceLimit) : Int =
    getPairLimitForPairAndName(space, pairKey, limit).getOrElse(
      getEffectiveLimitByNameForDomain(space, limit))
}

trait ServiceLimitsView extends SystemServiceLimitsView with DomainServiceLimitsView with PairServiceLimitsView

trait SystemServiceLimitsView {
  def getEffectiveLimitByName(limit: ServiceLimit) : Int
}

trait DomainServiceLimitsView {
  def getEffectiveLimitByNameForDomain(space:Long, limit: ServiceLimit) : Int
}

trait PairServiceLimitsView {
  def getEffectiveLimitByNameForPair(space:Long, pairKey: String, limit:ServiceLimit) : Int
}
