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
trait ServiceLimitsStore extends PairServiceLimitsView {
  def defineLimit(limitName: String, description: String): Unit
  def deleteDomainLimits(domainName: String): Unit
  def deletePairLimitsByDomain(domainName: String): Unit

  def setSystemHardLimit(limitName: String, limitValue: Int): Unit
  def setSystemDefaultLimit(limitName: String, limitValue: Int): Unit
  def setDomainHardLimit(domainName: String, limitName: String, limitValue: Int): Unit
  def setDomainDefaultLimit(domainName: String, limitName: String, limitValue: Int): Unit
  def setPairLimit(domainName: String, pairKey: String, limitName: String, limitValue: Int): Unit
  
  def getSystemHardLimitForName(limitName: String): Option[Int]
  def getSystemDefaultLimitForName(limitName: String): Option[Int]
  def getDomainHardLimitForDomainAndName(domainName: String, limitName: String): Option[Int]
  def getDomainDefaultLimitForDomainAndName(domainName: String, limitName: String): Option[Int]
  def getPairLimitForPairAndName(domainName: String, pairKey: String, limitName: String): Option[Int]

  def getEffectiveLimitByName(limitName: String): Int
  def getEffectiveLimitByNameForDomain(limitName: String, domainName: String): Int
}

trait PairServiceLimitsView {
  def getEffectiveLimitByNameForPair(limitName: String, domainName: String, pairKey: String): Int
}

/**
 * This should only be used carefully, typically for tests that don't want limits to interfere
 * with results.
 */
object UnlimitedPairServiceLimitsView extends PairServiceLimitsView {
  def getEffectiveLimitByNameForPair(limitName: String, domainName: String, pairKey: String): Int =
    ServiceLimit.UNLIMITED
}
