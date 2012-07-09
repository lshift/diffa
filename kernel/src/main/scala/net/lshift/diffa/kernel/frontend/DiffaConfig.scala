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
package net.lshift.diffa.kernel.frontend

import net.lshift.diffa.kernel.config._
import net.lshift.diffa.kernel.config.DiffaPair
import reflect.BeanProperty
import org.quartz.CronExpression
import java.util.HashMap
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.util.{DownstreamEndpoint, UpstreamEndpoint, EndpointSide}

/**
 * Describes a complete Diffa configuration in the context of a domain - this means that all of the objects
 * defined in a single config belong to a particular domain.
 */
case class DiffaConfig(
  members:Set[String] = Set(),
  properties:Map[String, String] = Map(),
  endpoints:Set[EndpointDef] = Set(),
  pairs:Set[PairDef] = Set(),
  repairActions:Set[RepairActionDef] = Set(),
  escalations:Set[EscalationDef] = Set(),
  reports:Set[PairReportDef] = Set()
) {

  def validate() {
    val path = "config"
    endpoints.foreach(_.validate(path))
    pairs.foreach(_.validate(path, endpoints))
    repairActions.foreach(_.validate(path))
    escalations.foreach(_.validate(path))
    reports.foreach(_.validate(path))
  }
}

object DefaultLimits {
  val KEY_LENGTH_LIMIT = 50
  val VALUE_LENGTH_LIMIT = 255
  val URL_LENGTH_LIMIT = 1024
  // This appears to be the limit of column size that MySQL will let you set a PK varchar to be
  val MYSQL_VARCHAR_PK_LIMIT = 255
}

/**
 * Serializable representation of an Endpoint within the context of a domain.
 */
case class EndpointDef (
  @BeanProperty var name: String = null,
  @BeanProperty var scanUrl: String = null,
  @BeanProperty var contentRetrievalUrl: String = null,
  @BeanProperty var versionGenerationUrl: String = null,
  @BeanProperty var inboundUrl: String = null,
  @BeanProperty var categories: java.util.Map[String,CategoryDescriptor] = new HashMap[String, CategoryDescriptor],
  @BeanProperty var views: java.util.List[EndpointViewDef] = new java.util.ArrayList[EndpointViewDef],
  @BeanProperty var collation: String = AsciiCollationOrdering.name) {

  def this() = this(name = null)

  val DEFAULT_URL_LENGTH_LIMIT = 1024

  def validate(path:String = null) {
    // Nullify any of the URLs that are blank
    scanUrl = ValidationUtil.maybeNullify(scanUrl)
    contentRetrievalUrl = ValidationUtil.maybeNullify(contentRetrievalUrl)
    versionGenerationUrl = ValidationUtil.maybeNullify(versionGenerationUrl)
    inboundUrl = ValidationUtil.maybeNullify(inboundUrl)

    val endPointPath = ValidationUtil.buildPath(path, "endpoint", Map("name" -> name))
    ValidationUtil.requiredAndNotEmpty(endPointPath, "name", this.name);
    ValidationUtil.ensureLengthLimit(endPointPath, "name", this.name, DefaultLimits.KEY_LENGTH_LIMIT)

    ValidationUtil.ensureLengthLimit(endPointPath, "scanUrl", scanUrl, DEFAULT_URL_LENGTH_LIMIT)
    ValidationUtil.ensureLengthLimit(endPointPath, "contentRetrievalUrl", contentRetrievalUrl, DEFAULT_URL_LENGTH_LIMIT)
    ValidationUtil.ensureLengthLimit(endPointPath, "versionGenerationUrl", versionGenerationUrl, DEFAULT_URL_LENGTH_LIMIT)
    ValidationUtil.ensureLengthLimit(endPointPath, "inboundUrl", inboundUrl, DEFAULT_URL_LENGTH_LIMIT)

    collation = ValidationUtil.maybeDefault(collation, AsciiCollationOrdering.name)
    ValidationUtil.ensureMembership(endPointPath, "collation", collation,
      Set(AsciiCollationOrdering.name, UnicodeCollationOrdering.name))

    Array(scanUrl,
          contentRetrievalUrl,
          versionGenerationUrl,
          inboundUrl).foreach(ValidationUtil.ensureSettingsURLFormat(endPointPath, _))

    categories.foreach { case (k, c) => {
      val categoryPath = ValidationUtil.buildPath(endPointPath, "category", Map("name" -> k))

      ValidationUtil.requiredAndNotEmpty(categoryPath, "name", k)
      c.validate(categoryPath)
    }}

    ValidationUtil.ensureUniqueChildren(endPointPath, "views", "name", views.map(v => v.name))
    views.foreach(v => v.validate(this, endPointPath))
  }
}

case class EndpointViewDef(
  @BeanProperty var name:String = null,
  @BeanProperty var categories: java.util.Map[String,CategoryDescriptor] = new HashMap[String, CategoryDescriptor]
) {
  def this() = this(name = null)

  def validate(owner:EndpointDef, path:String = null) {
    val viewPath = ValidationUtil.buildPath(path, "views", Map("name" -> this.name))

    ValidationUtil.requiredAndNotEmpty(viewPath, "name", this.name)
    ValidationUtil.ensureLengthLimit(viewPath, "name", this.name, DefaultLimits.KEY_LENGTH_LIMIT)

    categories.keySet().foreach(viewCategory => {
      val categoryPath = ValidationUtil.buildPath(viewPath, "category", Map("name" -> viewCategory))
      ValidationUtil.requiredAndNotEmpty(categoryPath, "name", viewCategory)

      if (!owner.categories.containsKey(viewCategory)) {
        // Ensure that we don't expose any categories that aren't known to the parent
        throw new ConfigValidationException(viewPath,
          "View category '%s' does not derive from an endpoint category".format(viewCategory))
      } else {
        val ourCategory = categories.get(viewCategory)
        val ownerCategory = owner.categories.get(viewCategory)

        ourCategory.validate(categoryPath)

        if (!ownerCategory.isRefinement(ourCategory)) {
          throw new ConfigValidationException(viewPath,
          "View category '%s' (%s) does not refine endpoint category (%s)".format(viewCategory, ourCategory, ownerCategory))
        }
      }
    })
  }
}

/**
 * Serializable representation of a Pair within the context of a domain.
 */
case class PairDef(
  @BeanProperty var key: String = null,
  @BeanProperty var versionPolicyName: String = null,
  @BeanProperty var matchingTimeout: Int = 0,
  @BeanProperty var upstreamName: String = null,
  @BeanProperty var downstreamName: String = null,
  @BeanProperty var scanCronSpec: String = null,
  @BeanProperty var scanCronEnabled: Boolean = true,
  @BeanProperty var allowManualScans: java.lang.Boolean = null,
  @BeanProperty var views:java.util.List[PairViewDef] = new java.util.ArrayList[PairViewDef]) {

  def this() = this(key = null)

  def asRef(domain:String) = DiffaPairRef(key, domain)

  def asDomainPairDef(domainName:String) = DomainPairDef(
    domain = domainName,
    key = this.key,
    matchingTimeout = this.matchingTimeout,
    upstreamName = this.upstreamName,
    downstreamName = this.downstreamName,
    scanCronSpec = this.scanCronSpec,
    allowManualScans = this.allowManualScans,
    views = this.views
  )

  def whichSide(endpoint:EndpointDef):EndpointSide = {
    if (upstreamName == endpoint.name) {
      UpstreamEndpoint
    } else if (downstreamName == endpoint.name) {
      DownstreamEndpoint
    } else {
      throw new IllegalArgumentException(endpoint.name + " is not a member of pair " + key)
    }
  }

  def validate(path:String = null, endpoints:Set[EndpointDef] = null) {
    val pairPath = ValidationUtil.buildPath(path, "pair", Map("key" -> key))

    ValidationUtil.requiredAndNotEmpty(pairPath, "key", key)
    ValidationUtil.ensureLengthLimit(pairPath, "key", key, DefaultLimits.KEY_LENGTH_LIMIT)

    // Nullify the cronspecs if they are blank
    scanCronSpec = ValidationUtil.maybeNullify(scanCronSpec)
    views.foreach(v => v.scanCronSpec = ValidationUtil.maybeNullify(v.scanCronSpec))

    // Default various fields if they are missing
    versionPolicyName = ValidationUtil.maybeDefault(versionPolicyName, "same")
    allowManualScans = ValidationUtil.maybeDefault(allowManualScans, false)

    // Ensure that cron specs are valid
    if (scanCronSpec != null) {
      try {
        // Will throw an exception if the expression is invalid. The exception message will also include useful
        // diagnostics of why it is wrong.
        new CronExpression(scanCronSpec)
      } catch {
        case ex =>
          throw new ConfigValidationException(pairPath, "Schedule '" + scanCronSpec + "' is not a valid: " + ex.getMessage)
      }
    }

    // Ensure that we've got endpoint names defined
    ValidationUtil.requiredAndNotEmpty(pairPath, "upstreamName", upstreamName)
    ValidationUtil.requiredAndNotEmpty(pairPath, "downstreamName", downstreamName)

    // Ensure that the upstream and downstream names are different
    if (upstreamName == downstreamName) {
      throw new ConfigValidationException(pairPath,
        "Upstream and Downstream endpoints must be different. Both are currently '%s'".format(upstreamName))
    }

    // In some cases, validate might be called without providing the list of endpoints - so we won't be able to perform
    // this validation.
    if (endpoints != null) {
      // Ensure that endpoints exist for both the specified upstream and downstream names
      val upstreamEp = endpoints.find(e => e.name == upstreamName).
        getOrElse(throw new ConfigValidationException(pairPath, "Upstream endpoint '%s' is not defined".format(upstreamName)))
      val downstreamEp = endpoints.find(e => e.name == downstreamName).
        getOrElse(throw new ConfigValidationException(pairPath, "Downstream endpoint '%s' is not defined".format(downstreamName)))

      views.foreach(v => v.validate(this, pairPath, upstreamEp, downstreamEp))
    }
  }
}

case class PairViewDef(
  @BeanProperty var name:String = null,
  @BeanProperty var scanCronSpec:String = null,
  @BeanProperty var scanCronEnabled:Boolean = true
) {
  def this() = this(name = null)

  def validate(owner:PairDef, path:String, upstreamEp:EndpointDef, downstreamEp:EndpointDef) {
    val viewPath = ValidationUtil.buildPath(path, "views", Map("name" -> this.name))

    ValidationUtil.requiredAndNotEmpty(viewPath, "name", this.name)
    ValidationUtil.ensureLengthLimit(viewPath, "name", this.name, DefaultLimits.KEY_LENGTH_LIMIT)

    // Ensure we have both upstream and downstream endpoint views corresponding to this view
    upstreamEp.views.find(v => v.name == this.name).
      getOrElse(throw new ConfigValidationException(viewPath, "The upstream endpoint does not define the view '%s'".format(name)))
    downstreamEp.views.find(v => v.name == this.name).
      getOrElse(throw new ConfigValidationException(viewPath, "The downstream endpoint does not define the view '%s'".format(name)))

    // Ensure that cron specs are valid
    if (scanCronSpec != null) {
      try {
        // Will throw an exception if the expression is invalid. The exception message will also include useful
        // diagnostics of why it is wrong.
        new CronExpression(scanCronSpec)
      } catch {
        case ex =>
          throw new ConfigValidationException(viewPath, "Schedule '" + scanCronSpec + "' is not a valid: " + ex.getMessage)
      }
    }
  }
}

/**
 * This is the next generation version of the DiffaPair, but with serialization and friendly fields.
 * This has been made java friendly to ensure it can be serialized correctly when inserted into caches.
 * When Hibernate has been removed completely, the DiffaPair object can be deleted all together and
 * get replaced with this more useful definition.
 */
case class DomainPairDef(
  @BeanProperty var domain: String = null,
  @BeanProperty var key: String = null,
  @BeanProperty var versionPolicyName: String = null,
  @BeanProperty var matchingTimeout: Int = 0,
  @BeanProperty var upstreamName: String = null,
  @BeanProperty var downstreamName: String = null,
  @BeanProperty var scanCronSpec: String = null,
  @BeanProperty var scanCronEnabled: Boolean = true,
  @BeanProperty var allowManualScans: java.lang.Boolean = null,
  @BeanProperty var views:java.util.List[PairViewDef] = new java.util.ArrayList[PairViewDef]) {

  def this() = this(domain = null)

  def asRef = DiffaPairRef(key, domain)

  def withoutDomain = PairDef(
    key = key,
    versionPolicyName = versionPolicyName,
    matchingTimeout = matchingTimeout,
    upstreamName = upstreamName,
    downstreamName = downstreamName,
    scanCronSpec = scanCronSpec,
    scanCronEnabled = scanCronEnabled,
    allowManualScans = allowManualScans,
    views = views
  )

  def identifier = asRef.identifier
}

/**
 * Serializable representation of a RepairAction within the context of a domain.
 */
case class RepairActionDef (
  @BeanProperty var name: String = null,
  @BeanProperty var url: String = null,
  @BeanProperty var scope: String = null,
  @BeanProperty var pair: String = null
) {
  import RepairAction._

  def this() = this(name = null)

  def validate(path:String = null) {
    val actionPath = ValidationUtil.buildPath(
      ValidationUtil.buildPath(path, "pair", Map("key" -> pair)),
      "repair-action", Map("name" -> name))

    ValidationUtil.ensureLengthLimit(actionPath, "name", name, DefaultLimits.KEY_LENGTH_LIMIT)
    ValidationUtil.ensureLengthLimit(actionPath, "pair", pair, DefaultLimits.KEY_LENGTH_LIMIT)

    // Ensure that the scope is supported
    this.scope = scope match {
      case ENTITY_SCOPE | PAIR_SCOPE => scope
      case _ => throw new ConfigValidationException(actionPath, "Invalid action scope: "+scope)
    }
  }

  def asRepairAction(domain:String)
    = RepairAction(name, url, scope, DiffaPair(key=pair,domain=Domain(name=domain)))
}

/**
 * Serializable representation of an Escalation within the context of a domain.
 */
case class EscalationDef (
  @BeanProperty var name: String = null,
  @BeanProperty var pair: String = null,
  @BeanProperty var action: String = null,
  @BeanProperty var actionType: String = null,
  @BeanProperty var event: String = null,
  @BeanProperty var origin: String = null
) {
  import EscalationEvent._
  import EscalationOrigin._
  import EscalationActionType._

  def this() = this(name = null)

  def validate(path:String = null) {
    val escalationPath = ValidationUtil.buildPath(
      ValidationUtil.buildPath(path, "pair", Map("key" -> pair)),
      "escalation", Map("name" -> name))

    ValidationUtil.ensureLengthLimit(escalationPath, "name", name, DefaultLimits.KEY_LENGTH_LIMIT)
    ValidationUtil.ensureLengthLimit(escalationPath, "pair", pair, DefaultLimits.KEY_LENGTH_LIMIT)
    ValidationUtil.ensureLengthLimit(escalationPath, "action", action, DefaultLimits.KEY_LENGTH_LIMIT)

    // Ensure that the action type is supported, and validate the parameters that depend on it
    actionType match {
      case REPAIR =>
        // Ensure that the origin is supported
        origin match {
          case SCAN =>
          case _    => throw new ConfigValidationException(escalationPath, "Invalid escalation origin: " + origin)
        }
        event match {
          case UPSTREAM_MISSING | DOWNSTREAM_MISSING | MISMATCH  => event
          case _ =>
            throw new ConfigValidationException(escalationPath,
              "Invalid escalation event source type %s for action type %s".format(event, actionType))
        }
      case REPORT =>
        // We don't support origins for reports
        if (origin != null)
          throw new ConfigValidationException(escalationPath, "Origin not supported on report escalations.")

        event match {
          case SCAN_FAILED | SCAN_COMPLETED => event
          case _ =>
            throw new ConfigValidationException(escalationPath,
              "Invalid escalation event source type %s for action type %s".format(event, actionType))
        }
      case _    =>
        throw new ConfigValidationException(escalationPath, "Invalid escalation action type: " + actionType)
    }
  }

  def asEscalation(domain:String)
    = Escalation(name, DiffaPair(key=pair,domain=Domain(name=domain)), action, actionType, event, origin)
}

case class PairReportDef(
  @BeanProperty var name:String = null,
  @BeanProperty var pair: String = null,
  @BeanProperty var reportType:String = null,
  @BeanProperty var target:String = null
) {
  import PairReportType._

  def this() = this(name = null)

  def validate(path:String = null) {
    val escalationPath = ValidationUtil.buildPath(
        ValidationUtil.buildPath(path, "pair", Map("key" -> pair)),
    "report", Map("name" -> name))

    ValidationUtil.ensureLengthLimit(escalationPath, "name", this.name, DefaultLimits.KEY_LENGTH_LIMIT)
    ValidationUtil.ensureLengthLimit(escalationPath, "pair", this.pair, DefaultLimits.KEY_LENGTH_LIMIT)

    reportType match {
      case DIFFERENCES  =>
      case _ => throw new ConfigValidationException(escalationPath, "Invalid report type: " + reportType)
    }

    target match {
      case null | "" => throw new ConfigValidationException(escalationPath, "Missing target")
      case url if (url.startsWith("http://") || url.startsWith("https://")) =>   // Valid
      case _ => throw new ConfigValidationException(escalationPath, "Invalid target (not a URL): " + target)
    }
  }
}

