package net.lshift.diffa.kernel.config

import scala.beans.BeanProperty

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
