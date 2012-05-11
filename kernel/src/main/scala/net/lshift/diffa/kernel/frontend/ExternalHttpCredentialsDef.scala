package net.lshift.diffa.kernel.frontend

import reflect.BeanProperty
import net.lshift.diffa.kernel.config.{ConfigValidationException, ValidationUtil}
import net.lshift.diffa.kernel.config.ExternalHttpCredentials._
/**
 * Serializable representation of http credentials within the context of a domain, without any sensitive information.
 * This is used when listing credentials back to clients, so that the underlying secret credential is not leaked.
 * Generally used for outbound communication.
 */
case class OutboundExternalHttpCredentialsDef(@BeanProperty var url: String = null,
                                              @BeanProperty var key: String = null,
                                              @BeanProperty var `type`: String = null) {
  def this() = this(url = null)
}

/**
 * Serializable representation of http credentials within the context of a domain.
 * Generally used for inbound communication.
 */
case class InboundExternalHttpCredentialsDef(@BeanProperty var url: String = null,
                                             @BeanProperty var key: String = null,
                                             @BeanProperty var value: String = null,
                                             @BeanProperty var `type`: String = null) {

  def this() = this(url = null)

  def validate(path:String = null) {

    val credsPath = ValidationUtil.buildPath(path, "external-http-credentials", Map("url" -> url))

    ValidationUtil.requiredAndNotEmpty(credsPath, "url", url)
    ValidationUtil.requiredAndNotEmpty(credsPath, "key", key)
    ValidationUtil.requiredAndNotEmpty(credsPath, "value", value)
    ValidationUtil.requiredAndNotEmpty(credsPath, "type", `type`)

    ValidationUtil.ensureLengthLimit(credsPath, "url", url, DefaultLimits.URL_LENGTH_LIMIT)
    ValidationUtil.ensureLengthLimit(credsPath, "key", key, DefaultLimits.KEY_LENGTH_LIMIT)
    ValidationUtil.ensureLengthLimit(credsPath, "value", value, DefaultLimits.VALUE_LENGTH_LIMIT)

    ValidationUtil.ensureMembership(credsPath, "type", `type`, Set(BASIC_AUTH,QUERY_PARAMETER))
  }
}
