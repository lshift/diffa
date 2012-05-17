package net.lshift.diffa.kernel.config

import reflect.BeanProperty

/**
 * Describes a pre-defined limit. The actual run time values will depend on what is configured
 * in the ServiceLimitsStore for a given key
 */
trait ServiceLimit {
  def key:String
  def description:String
  def defaultLimit:java.lang.Integer
  def hardLimit:java.lang.Integer

  private final val SEC_PER_MIN = 60
  private final val MS_PER_S = 1000

  /// Return type is java.lang.Integer since InsertBuilder.values requires a map of AnyRef
  protected def minutesToMs(minutes: Int): java.lang.Integer = minutes * SEC_PER_MIN * MS_PER_S
  protected def secondsToMs(seconds: Int): java.lang.Integer = seconds * MS_PER_S
}

case class ServiceLimitDefinitions(@BeanProperty var limitName: String = null,
                                   @BeanProperty var limitDescription: String = null) {
  def this() = this(limitName = null)
}

case class SystemServiceLimits(@BeanProperty var limitName: String = null,
                               @BeanProperty var hardLimit: Int = 0,
                               @BeanProperty var defaultLimit: Int = 0) {
  def this() = this(limitName = null)
}

case class DomainServiceLimits(@BeanProperty var domain: Domain = null,
                               @BeanProperty var limitName: String = null,
                               @BeanProperty var hardLimit: Int = 0,
                               @BeanProperty var defaultLimit: Int = 0) {
  def this() = this(domain = null)
}

case class PairServiceLimits(@BeanProperty var domain: Domain = null,
                             @BeanProperty var pair: DiffaPair = null,
                             @BeanProperty var limitName: String = null,
                             @BeanProperty var limitValue: Int = 0) {
  def this() = this(domain = null)
}

case class DomainScopedLimit(@BeanProperty var limitName: String = null,
                             @BeanProperty var domain: Domain = null) extends java.io.Serializable {
  def this() = this(limitName = null)
}

case class PairScopedLimit(@BeanProperty var limitName: String = null,
                           @BeanProperty var pair: DiffaPair = null) extends java.io.Serializable {
  def this() = this(limitName = null)
}
