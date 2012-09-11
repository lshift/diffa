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
package net.lshift.diffa.agent.rest

import java.lang.{String, Class}
import javax.ws.rs.core.{MultivaluedMap, MediaType}
import java.lang.annotation.Annotation
import java.lang.reflect.Type
import javax.ws.rs.ext.{MessageBodyWriter, MessageBodyReader, Provider}
import javax.ws.rs.{Consumes, Produces}
import org.springframework.core.io.ClassPathResource
import org.springframework.oxm.castor.CastorMarshaller
import javax.xml.transform.Result
import java.io.{StringWriter, OutputStream, InputStream}
import javax.xml.transform.stream.{StreamSource, StreamResult}
import scala.collection.JavaConversions._
import reflect.BeanProperty
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.kernel.differencing.PairScanState
import net.lshift.diffa.kernel.frontend._

/**
 * Provider for encoding and decoding diffa configuration blocks.
 */
@Provider
@Produces(Array("application/xml"))
@Consumes(Array("application/xml"))
class DiffaConfigReaderWriter
    extends MessageBodyReader[DiffaConfig]
    with MessageBodyWriter[DiffaConfig] {

  val marshaller = new CastorMarshaller();
  marshaller.setMappingLocation(new ClassPathResource("/net/lshift/diffa/agent/rest/castorMapping.xml"));
  marshaller.afterPropertiesSet();

  def isReadable(propType : Class[_], genericType: Type, annotations: Array[Annotation], mediaType: MediaType) =
    classOf[DiffaConfig].isAssignableFrom(propType)
  def isWriteable(propType : Class[_], genericType: Type, annotations: Array[Annotation], mediaType: MediaType) =
    classOf[DiffaConfig].isAssignableFrom(propType)

  def readFrom(propType: Class[DiffaConfig], genericType: Type, annotations: Array[Annotation], mediaType: MediaType, httpHeaders: MultivaluedMap[String, String], entityStream: InputStream) =
    marshaller.unmarshal(new StreamSource(entityStream)).asInstanceOf[DiffaCastorSerializableConfig].toDiffaConfig

  def writeTo(t: DiffaConfig, `type` : Class[_], genericType: Type, annotations: Array[Annotation], mediaType: MediaType, httpHeaders: MultivaluedMap[String, AnyRef], entityStream: OutputStream) = {
    var r = new StreamResult(entityStream)
    marshaller.marshal((new DiffaCastorSerializableConfig).fromDiffaConfig(t), r)
  }

  def getSize(t: DiffaConfig, `type` : Class[_], genericType: Type, annotations: Array[Annotation], mediaType: MediaType) = {
    var sw = new StringWriter
    var r = new StreamResult(sw)
    marshaller.marshal((new DiffaCastorSerializableConfig).fromDiffaConfig(t), r)

    sw.toString.length
  }
}

//
// The below types are essential private the DiffaConfigReaderWriter, and are used simply to allow for clean xml
// to be mapped to/from by Castor. Our internal types simply don't serialize to XML cleanly, and we don't want to live
// with the mess they'd be in if we adjusted them to be so.
//


/**
 * Describes a complete diffa configuration.
 */
class DiffaCastorSerializableConfig {
  @BeanProperty var members:java.util.List[PolicyMember] = new java.util.ArrayList[PolicyMember]
  @BeanProperty var properties:java.util.List[DiffaProperty] = new java.util.ArrayList[DiffaProperty]
  @BeanProperty var endpoints:java.util.List[CastorSerializableEndpoint] = new java.util.ArrayList[CastorSerializableEndpoint]
  @BeanProperty var pairs:java.util.List[CastorSerializablePair] = new java.util.ArrayList[CastorSerializablePair]

  def fromDiffaConfig(c:DiffaConfig) = {
    this.members = c.members.toList
    this.properties = c.properties.map { case (k, v) => new DiffaProperty(k, v) }.toList
    this.endpoints = c.endpoints.map { e => (new CastorSerializableEndpoint).fromDiffaEndpoint(e) }.toList
    this.pairs = c.pairs.map(p => {
      CastorSerializablePair.fromPairDef(p)
    }).toList
    this
  }

  def toDiffaConfig:DiffaConfig =
    DiffaConfig(
      members = members.toSet,
      properties = properties.map(p => p.key -> p.value).toMap,
      endpoints = endpoints.map(_.toDiffaEndpoint).toSet,
      pairs = (for (p <- pairs) yield p.toPairDef).toSet
    )

}

class DiffaProperty(@BeanProperty var key:String, @BeanProperty var value:String) {
  def this() = this(null, null)
}

trait Categorized {
  @BeanProperty var rangeCategories: java.util.List[CastorSerializableRangeCategoryDescriptor] = new java.util.ArrayList[CastorSerializableRangeCategoryDescriptor]
  @BeanProperty var prefixCategories: java.util.List[CastorSerializablePrefixCategoryDescriptor] = new java.util.ArrayList[CastorSerializablePrefixCategoryDescriptor]
  @BeanProperty var setCategories: java.util.List[CastorSerializableSetCategoryDescriptor] = new java.util.ArrayList[CastorSerializableSetCategoryDescriptor]

  protected def fromDiffaCategories(categories:java.util.Map[String, CategoryDescriptor]) {
    this.rangeCategories = categories.filter { case (key, cat) => cat.isInstanceOf[RangeCategoryDescriptor] }.
      map { case (key, cat) => new CastorSerializableRangeCategoryDescriptor(key, cat.asInstanceOf[RangeCategoryDescriptor]) }.toList
    this.prefixCategories = categories.filter { case (key, cat) => cat.isInstanceOf[PrefixCategoryDescriptor] }.
      map { case (key, cat) => new CastorSerializablePrefixCategoryDescriptor(key, cat.asInstanceOf[PrefixCategoryDescriptor]) }.toList
    this.setCategories = categories.filter { case (key, cat) => cat.isInstanceOf[SetCategoryDescriptor] }.
      map { case (key, cat) => new CastorSerializableSetCategoryDescriptor(key, cat.asInstanceOf[SetCategoryDescriptor]) }.toList
  }

  protected def toDiffaCategories:java.util.Map[String, CategoryDescriptor] = {
    rangeCategories.map(c => c.name -> c.toRangeCategoryDescriptor).toMap[String, CategoryDescriptor] ++
        prefixCategories.map(c => c.name -> c.toPrefixCategoryDescriptor).toMap[String, CategoryDescriptor] ++
        setCategories.map(c => c.name -> c.toSetCategoryDescriptor).toMap[String, CategoryDescriptor]
  }
}

class CastorSerializableEndpoint extends Categorized {
  @BeanProperty var name: String = null
  @BeanProperty var scanUrl: String = null
  @BeanProperty var contentRetrievalUrl: String = null
  @BeanProperty var versionGenerationUrl: String = null
  @BeanProperty var inboundUrl: String = null
  @BeanProperty var views: java.util.List[CastorSerializableEndpointView] = new java.util.ArrayList[CastorSerializableEndpointView]
  @BeanProperty var collation: String = null

  def fromDiffaEndpoint(e:EndpointDef) = {
    this.name = e.name
    this.scanUrl = e.scanUrl
    this.contentRetrievalUrl = e.contentRetrievalUrl
    this.versionGenerationUrl = e.versionGenerationUrl
    this.inboundUrl = e.inboundUrl
    this.fromDiffaCategories(e.categories)
    this.views = e.views.map(v => new CastorSerializableEndpointView().fromDiffaEndpointView(v));
    this.collation = e.collation

    this
  }

  def toDiffaEndpoint =
    EndpointDef(
      name = name, inboundUrl = inboundUrl,
      scanUrl = scanUrl, contentRetrievalUrl = contentRetrievalUrl, versionGenerationUrl = versionGenerationUrl,
      categories = toDiffaCategories,
      views = views.map(v => v.toDiffaEndpointView),
      collation = collation
    )
}

class CastorSerializableEndpointView extends Categorized {
  @BeanProperty var name: String = null

  def fromDiffaEndpointView(e:EndpointViewDef) = {
    this.name = e.name
    this.fromDiffaCategories(e.categories)

    this
  }

  def toDiffaEndpointView =
    EndpointViewDef(
      name = name,
      categories = toDiffaCategories
    )
}

class CastorSerializableRangeCategoryDescriptor(
    @BeanProperty var name:String,
    @BeanProperty var dataType:String,
    @BeanProperty var lower:String,
    @BeanProperty var upper:String,
    @BeanProperty var maxGranularity:String) {

  def this() = this(null,null,null,null,null)
  def this(name:String, rcd:RangeCategoryDescriptor) = this(name, rcd.dataType, rcd.lower, rcd.upper, rcd.maxGranularity)

  def toRangeCategoryDescriptor = new RangeCategoryDescriptor(dataType, lower, upper, maxGranularity)
}
class CastorSerializablePrefixCategoryDescriptor(@BeanProperty var name:String, @BeanProperty var prefixLength:Int,
                                           @BeanProperty var maxLength:Int, @BeanProperty var step:Int) {

  def this() = this(null, 1, 1, 1)
  def this(name:String, pcd:PrefixCategoryDescriptor) = this(name, pcd.prefixLength, pcd.maxLength, pcd.step)

  def toPrefixCategoryDescriptor = new PrefixCategoryDescriptor(prefixLength, maxLength, step)
}
class CastorSerializableSetCategoryDescriptor(@BeanProperty var name:String, @BeanProperty var values:java.util.Set[SetValue]) {
  def this() = this(null, new java.util.HashSet[SetValue])
  def this(name:String, scd:SetCategoryDescriptor) = this(name, scd.values.map(v => new SetValue(v)).toSet)

  def toSetCategoryDescriptor = new SetCategoryDescriptor(new java.util.HashSet(values.map(v => v.value).toList))
}
class SetValue(@BeanProperty var value:String) {
  def this() = this(null)
}

class CastorSerializablePair(
  @BeanProperty var key: String = null,
  @BeanProperty var upstream: String = null,
  @BeanProperty var downstream: String = null,
  @BeanProperty var versionPolicy: String = null,
  @BeanProperty var matchingTimeout: Int = 0,
  @BeanProperty var repairActions: java.util.List[RepairActionDef] = new java.util.ArrayList[RepairActionDef],
  @BeanProperty var escalations: java.util.List[EscalationDef] = new java.util.ArrayList[EscalationDef],
  @BeanProperty var reports: java.util.List[PairReportDef] = new java.util.ArrayList[PairReportDef],
  @BeanProperty var scanCronSpec: String = null,
  @BeanProperty var scanCronEnabled: java.lang.Boolean = null,
  @BeanProperty var allowManualScans: java.lang.Boolean = null,
  @BeanProperty var views: java.util.List[CastorSerializablePairView] = new java.util.ArrayList[CastorSerializablePairView],
  @BeanProperty var eventsToLog: Int = 0,
  @BeanProperty var maxExplainFiles: Int = 0
) {
  def this() = this(key = null)

  def toPairDef = PairDef(key, versionPolicy, matchingTimeout, upstream, downstream, scanCronSpec,
                         (scanCronEnabled == null || scanCronEnabled), allowManualScans, views.map(_.toPairViewDef),
                         new java.util.HashSet(repairActions), new java.util.HashSet(reports), new java.util.HashSet(escalations))
}

class CastorSerializablePairView(
  @BeanProperty var name:String = null,
  @BeanProperty var scanCronSpec:String = null,
  @BeanProperty var scanCronEnabled:java.lang.Boolean = null
) {
  def this() = this(name = null)

  def toPairViewDef = PairViewDef(name, scanCronSpec, scanCronEnabled == null || scanCronEnabled)
}

object CastorSerializablePair {
  def fromPairDef(p: PairDef): CastorSerializablePair =
    new CastorSerializablePair(p.key, p.upstreamName, p.downstreamName, p.versionPolicyName, p.matchingTimeout,
                               p.repairActions.toList, p.escalations.toList, p.reports.toList, p.scanCronSpec, (if (p.scanCronEnabled) null else false),
                               p.allowManualScans, p.views.map(fromPairViewDef))

  def fromPairViewDef(p: PairViewDef): CastorSerializablePairView =
    new CastorSerializablePairView(p.name, p.scanCronSpec, (if (p.scanCronEnabled) null else false))
}
