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

package net.lshift.diffa.kernel.config

import reflect.BeanProperty
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.differencing.AttributesUtil
import scala.Option._
import net.lshift.diffa.kernel.frontend._
import net.lshift.diffa.kernel.util.{EndpointSide, UpstreamEndpoint, DownstreamEndpoint, CategoryUtil}
import net.lshift.diffa.participant.scanning.{AggregationBuilder, ConstraintsBuilder, SetConstraint, ScanConstraint}
import java.util.HashMap
import net.lshift.diffa.kernel.participants._

/**
 * Provides general configuration options within the scope of a particular domain.
 */

trait DomainConfigStore {

  def createOrUpdateEndpoint(domain:String, endpoint: EndpointDef) : DomainEndpointDef
  def deleteEndpoint(domain:String, name: String) : Unit
  def listEndpoints(domain:String) : Seq[EndpointDef]

  def createOrUpdatePair(domain:String, pairDef: PairDef) : Unit
  def deletePair(domain:String, key: String)
  def deletePair(ref:DiffaPairRef) : Unit = deletePair(ref.domain, ref.key)
  def listPairs(domain:String) : Seq[DomainPairDef]
  def listPairsForEndpoint(domain:String, endpoint:String) : Seq[DomainPairDef]

  def getPairDef(domain:String, key: String) : DomainPairDef
  def getPairDef(ref:DiffaPairRef) : DomainPairDef = getPairDef(ref.domain, ref.key)

  def createOrUpdateRepairAction(domain:String, action: RepairActionDef)
  def deleteRepairAction(domain:String, name: String, pairKey: String)

  def listRepairActions(domain:String) : Seq[RepairActionDef]
  def listRepairActionsForPair(domain:String, key: String) : Seq[RepairActionDef]

  def listEscalations(domain:String) : Seq[EscalationDef]
  def deleteEscalation(domain:String, s: String, s1: String)
  def createOrUpdateEscalation(domain:String, escalation : EscalationDef)
  def listEscalationsForPair(domain:String, key: String) : Seq[EscalationDef]

  def listReports(domain:String) : Seq[PairReportDef]
  def deleteReport(domain:String, name: String, pairKey: String)
  def createOrUpdateReport(domain:String, report: PairReportDef)
  def listReportsForPair(domain:String, key: String) : Seq[PairReportDef]

  def getEndpointDef(domain:String, name: String) : EndpointDef
  @Deprecated def getEndpoint(domain:String, name: String) : Endpoint

  def getRepairActionDef(domain:String, name: String, pairKey: String): RepairActionDef
  def getPairReportDef(domain:String, name:String, pairKey:String):PairReportDef

  def getConfigVersion(domain:String) : Int

  /**
   * Retrieves all (domain-specific, non-internal) agent configuration options.
   */
  def allConfigOptions(domain:String) : Map[String, String]

  /**
   * Retrieves an agent configuration option, returning the None if it is unset.
   */
  def maybeConfigOption(domain:String, key:String) : Option[String]

  /**
   * Retrieves an agent configuration option, returning the provided default value if it is unset.
   */
  def configOptionOrDefault(domain:String, key:String, defaultVal:String) : String

  /**
   * Sets the given configuration option to the given value.
   *   properties to be prevented from being shown in the user-visible system configuration views.
   */
  def setConfigOption(domain:String, key:String, value:String)

  /**
   * Removes the setting for the given configuration option.
   */
  def clearConfigOption(domain:String, key:String)


  /**
   * Make the given user a member of this domain.
   */
  def makeDomainMember(domain:String, userName:String) : Member

  /**
   * Remove the given user a from this domain.
   */
  def removeDomainMembership(domain:String, userName:String) : Unit

  /**
   * Lists all of the members of the given domain
   */
  def listDomainMembers(domain:String) : Seq[Member]
}

case class Endpoint(
  @BeanProperty var name: String = null,
  @BeanProperty var domain: Domain = null,
  @BeanProperty var scanUrl: String = null,
  @BeanProperty var contentRetrievalUrl: String = null,
  @BeanProperty var versionGenerationUrl: String = null,
  @BeanProperty var inboundUrl: String = null,
  @BeanProperty var categories: java.util.Map[String,CategoryDescriptor] = new HashMap[String, CategoryDescriptor],
  @BeanProperty var collation: String = AsciiCollationOrdering.name) {

  // Don't include this in the header definition, since it is a lazy collection
  @BeanProperty var views: java.util.Set[EndpointView] = new java.util.HashSet[EndpointView]

  def this() = this(name = null)

  def defaultView = views.find(v => v.name == "default").get

  /**
   * Fuses a list of runtime attributes together with their
   * static schema bound keys because the static attributes
   * are not transmitted over the wire.
   */
  def schematize(runtimeValues:Map[String, String]) = AttributesUtil.toTypedMap(categories.toMap, runtimeValues)

  def initialBucketing(view:Option[String]) =
    CategoryUtil.initialBucketingFor(CategoryUtil.fuseViewCategories(categories.toMap, views, view))

  /**
   * Returns a structured group of constraints for the current endpoint that is appropriate for transmission
   * over the wire.
   */
  def groupedConstraints(view:Option[String]) =
    CategoryUtil.groupConstraints(CategoryUtil.fuseViewCategories(categories.toMap, views, view))

  /**
   * Returns a set of the coarsest unbound query constraints for
   * each of the category types that has been configured for this pair.
   */
  def initialConstraints(view:Option[String]) =
    CategoryUtil.initialConstraintsFor(CategoryUtil.fuseViewCategories(categories.toMap, views, view))

  /**
   * Allows constraints relevant to this endpoint to be built by instructing a constraints builder of the category
   * types supported by this endpoint.
   */
  def buildConstraints(builder:ConstraintsBuilder) {
    CategoryUtil.buildConstraints(builder, categories.toMap)
  }

  /**
   * Allows aggregations relevant to this endpoint to be built by instructing an aggregations builder of the category
   * types supported by this endpoint.
   */
  def buildAggregations(builder:AggregationBuilder) {
    CategoryUtil.buildAggregations(builder, categories.toMap)
  }

 def lookupCollation () = collation match {
    case UnicodeCollationOrdering.name => UnicodeCollationOrdering
    case AsciiCollationOrdering.name => AsciiCollationOrdering
  }

  /**
   * Inidication of whether scanning is supported by the given endpoint.
   */
  def supportsScanning = scanUrl != null && scanUrl.length() > 0
}
case class EndpointView(
  @BeanProperty var name:String = null,
  @BeanProperty var endpoint:Endpoint = null,
  @BeanProperty var categories: java.util.Map[String,CategoryDescriptor] = new HashMap[String, CategoryDescriptor]
) {

  def this() = this(name = null)

  override def equals(that:Any) = that match {
    case v:EndpointView => v.name == name && v.categories == categories
    case _              => false
  }

  override def hashCode = 31 * (31 + name.hashCode) + categories.hashCode
}

case class DiffaPair(
  @BeanProperty var key: String = null,
  @BeanProperty var domain: Domain = null,
  @BeanProperty var upstream: String = null,
  @BeanProperty var downstream: String = null,
  @BeanProperty var versionPolicyName: String = null,
  @BeanProperty var matchingTimeout: Int = DiffaPair.NO_MATCHING,
  @BeanProperty var scanCronSpec: String = null,
  @BeanProperty var scanCronEnabled: Boolean = true,
  @BeanProperty var allowManualScans: java.lang.Boolean = null,
  @BeanProperty var views:java.util.Set[PairView] = new java.util.HashSet[PairView]) {

  def this() = this(key = null)

  def identifier = asRef.identifier

  def asRef = DiffaPairRef(key, domain.name)

  override def equals(that:Any) = that match {
    case p:DiffaPair => p.key == key && p.domain == domain
    case _           => false
  }

  // TODO This looks a bit strange
  override def hashCode = 31 * (31 + key.hashCode) + domain.hashCode

  def whichSide(endpoint:EndpointDef):EndpointSide = {
    if (upstream == endpoint.name) {
      UpstreamEndpoint
    } else if (downstream == endpoint.name) {
      DownstreamEndpoint
    } else {
      throw new IllegalArgumentException(endpoint.name + " is not a member of pair " + this.asRef)
    }
  }
}

case class PairView(
  @BeanProperty var name:String = null,
  @BeanProperty var scanCronSpec:String = null,
  @BeanProperty var scanCronEnabled:Boolean = true
) {
  // Not wanted in equals, hashCode or toString
  @BeanProperty var pair:DiffaPair = null

  def this() = this(name = null)

  override def equals(that:Any) = that match {
    case p:PairView => p.name == name && p.pair.key == pair.key && p.pair.domain.name == pair.domain.name
    case _          => false
  }

  // TODO This looks a bit strange
  override def hashCode = 31 * (31 * (31 + pair.key.hashCode) + name.hashCode) + pair.domain.name.hashCode
}

case class PairReport(
  @BeanProperty var name:String = null,
  @BeanProperty var pair: DiffaPair = null,
  @BeanProperty var reportType:String = null,
  @BeanProperty var target:String = null
) {
  def this() = this(name = null)
}

/**
 * Enumeration of valid types of reports that can be run.
 */
object PairReportType {
  val DIFFERENCES = "differences"
}

/**
 * This is a light weight pointer to a pair in Diffa.
 */
case class DiffaPairRef(@BeanProperty var key: String = null,
                        @BeanProperty var domain: String = null) {
  def this() = this(key = null)

  def identifier = "%s/%s".format(domain,key)

  def toInternalFormat = DiffaPair(key = key, domain = Domain(name = domain))

  override def equals(that:Any) = that match {
    case p:DiffaPairRef => p.key == key && p.domain == domain
    case _              => false
  }

  // TODO This looks a bit strange
  override def hashCode = 31 * (31 + key.hashCode) + domain.hashCode
}

object DiffaPair {
  val NO_MATCHING = null.asInstanceOf[Int]
  def fromIdentifier(id:String) = {
    val Array(domain,key) = id.split("/")
    (domain,key)
  }
}

case class Domain (
  @BeanProperty var name: String = null,
  @BeanProperty var configVersion: java.lang.Integer = 0
) {
  def this() = this(name = null)

  override def equals(that:Any) = that match {
    case d:Domain => d.name == name
    case _        => false
  }

  override def hashCode = name.hashCode
  override def toString = name
}

object Domain {
  val DEFAULT_DOMAIN = Domain(name = "diffa")
}

case class RepairAction(
  @BeanProperty var name: String = null,
  @BeanProperty var url: String = null,
  @BeanProperty var scope: String = null,
  @BeanProperty var pair: DiffaPair = null
) {
  import RepairAction._

  def this() = this(name = null)

  def validate(path:String = null) {
    val actionPath = ValidationUtil.buildPath(
      ValidationUtil.buildPath(path, "pair", Map("key" -> pair.key)),
      "repair-action", Map("name" -> name))

    // Ensure that the scope is supported
    this.scope = scope match {
      case ENTITY_SCOPE | PAIR_SCOPE => scope
      case _ => throw new ConfigValidationException(actionPath, "Invalid action scope: "+scope)
    }
  }
}

object RepairAction {
  val ENTITY_SCOPE = "entity"
  val PAIR_SCOPE = "pair"
}
/**
 * Defines a step for escalating a detected difference.
 */
case class Escalation (
  @BeanProperty var name: String = null,
  @BeanProperty var pair: DiffaPair = null,
  @BeanProperty var action: String = null,
  @BeanProperty var actionType: String = null,
  @BeanProperty var event: String = null,
  @BeanProperty var origin: String = null
) {

  def this() = this(name = null)
}

/**
 * Enumeration of valid types that an escalating difference should trigger.
 */
object EscalationEvent {
  val UPSTREAM_MISSING = "upstream-missing"
  val DOWNSTREAM_MISSING = "downstream-missing"
  val MISMATCH = "mismatch"
  val SCAN_FAILED = "scan-failed"
  val SCAN_COMPLETED = "scan-completed"
}

/**
 * Enumeration of valid origins for escalating a difference.
 */
object EscalationOrigin {
  val SCAN = "scan"
}

/**
 * Enumeration of valid action types for escalating a difference.
 */
object EscalationActionType {
  val REPAIR = "repair"
  val REPORT = "report"
}

case class User(@BeanProperty var name: String = null,
                @BeanProperty var email: String = null,
                @BeanProperty var passwordEnc: String = null,
                @BeanProperty var superuser: Boolean = false,
                @BeanProperty var token: String = null) {
  def this() = this(name = null)

  override def equals(that:Any) = that match {
    case u:User => u.name == name
    case _      => false
  }

  override def hashCode = name.hashCode
  override def toString = name
}

case class ExternalHttpCredentials(
  domain: String,
  url: String,
  key: String,
  value: String,
  credentialType: String
) {

  override def equals(that:Any) = that match {
    case e:ExternalHttpCredentials =>
      e.domain == domain && e.url == url && e.credentialType == credentialType
    case _                         => false
  }

  override def hashCode = 31 * (31 * (31 + domain.hashCode) + url.hashCode) + credentialType.hashCode
}

object ExternalHttpCredentials {
  val BASIC_AUTH = "basic_auth"
  val QUERY_PARAMETER = "query_parameter"
}

/**
 * Defines a user's membership to a domain
 */
case class Member(@BeanProperty var user:String = null,
                  @BeanProperty var domain:String = null) {

  def this() = this(user = null)

}

case class ConfigOption(@BeanProperty var key:String = null,
                        @BeanProperty var value:String = null,
                        @BeanProperty var domain:Domain = null) {
  def this() = this(key = null)
}

case class SystemConfigOption(@BeanProperty var key:String = null,
                              @BeanProperty var value:String = null) {
  def this() = this(key = null)
}

/**
 * Convenience wrapper for a compound primary key
 */
case class DomainScopedKey(@BeanProperty var key:String = null,
                           @BeanProperty var domain:Domain = null) extends java.io.Serializable
{
  def this() = this(key = null)
}

/**
 * Provides a Domain Scoped name for an entity.
 */
case class DomainScopedName(@BeanProperty var name:String = null,
                            @BeanProperty var domain:Domain = null) extends java.io.Serializable
{
  def this() = this(name = null)
}

/**
 * Provides an Endpoint Scoped name for an entity.
 */
case class EndpointScopedName(@BeanProperty var name:String = null,
                            @BeanProperty var endpoint:Endpoint = null) extends java.io.Serializable
{
  def this() = this(name = null)
}

/**
 * Provides a Pair Scoped name for an entity.
 */
case class PairScopedName(@BeanProperty var name:String = null,
                          @BeanProperty var pair:DiffaPair = null) extends java.io.Serializable
{
  def this() = this(name = null)
}

object ConfigOption {
  @Deprecated val eventExplanationLimitKey = "maxEventsToExplainPerPair"
  @Deprecated val explainFilesLimitKey = "maxExplainFilesPerPair"
}
