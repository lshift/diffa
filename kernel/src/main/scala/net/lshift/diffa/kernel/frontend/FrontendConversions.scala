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

import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.config._

/**
 * A bunch of converter functions to translate frontend objects from their internal counterparts
 */
object FrontendConversions {

  def toEndpointDef(e:Endpoint) = EndpointDef(
    name = e.name,
    scanUrl = e.scanUrl,
    contentRetrievalUrl = e.contentRetrievalUrl,
    versionGenerationUrl = e.versionGenerationUrl,
    inboundUrl = e.inboundUrl,
    categories = e.categories,
    views = new java.util.ArrayList[EndpointViewDef](e.views.map(v => toEndpointViewDef(v))),
    collation = e.collation)

  def fromEndpointDef(domain:Domain, e:EndpointDef) = Endpoint(
    name = e.name,
    domain = domain,
    scanUrl = e.scanUrl,
    contentRetrievalUrl = e.contentRetrievalUrl,
    versionGenerationUrl = e.versionGenerationUrl,
    inboundUrl = e.inboundUrl,
    categories = e.categories,
    collation = e.collation)

  def fromEndpointViewDef(endpoint:Endpoint, v:EndpointViewDef) =
    EndpointView(name = v.name, endpoint = endpoint, categories = v.categories)

  def toEndpointViewDef(v:EndpointView) = EndpointViewDef(name = v.name, categories = v.categories)

  def toPairDef(p:DomainPairDef) = PairDef(
    key = p.key,
    versionPolicyName = p.versionPolicyName,
    matchingTimeout = p.matchingTimeout,
    upstreamName = p.upstreamName,
    downstreamName = p.downstreamName,
    scanCronSpec = p.scanCronSpec,
    scanCronEnabled = p.scanCronEnabled,
    allowManualScans = p.allowManualScans,
    views = new java.util.ArrayList[PairViewDef](p.views))

  @Deprecated
  def toPairViewDef(v:PairView) = PairViewDef(name = v.name, scanCronSpec = v.scanCronSpec, scanCronEnabled = v.scanCronEnabled)

  def fromPairViewDef(p:PairRef, v:PairViewDef) = {
    val result = PairView(name = v.name, scanCronSpec = v.scanCronSpec, scanCronEnabled = v.scanCronEnabled)
    result.pair = p
    result
  }

  def toRepairActionDef(a:RepairAction) = RepairActionDef(
    name = a.name,
    url = a.url,
    scope = a.scope
  )

  def fromRepairActionDef(pair:PairRef, a:RepairActionDef) = RepairAction(
    name = a.name,
    url = a.url,
    scope = a.scope,
    pair = pair
  )

  def toPairReportDef(r:PairReport) = PairReportDef(
    name = r.name,
    reportType = r.reportType,
    target = r.target
  )

  def fromPairReportDef(pair:PairRef,r:PairReportDef) = PairReport(
    name = r.name,
    pair = pair,
    reportType = r.reportType,
    target = r.target
  )

  def toDomainDef(d: Domain) = DomainDef(name = d.name)
  
  def fromDomainDef(d:DomainDef) = Domain(name=d.name)

  def fromUserDef(u:UserDef) = User(name = u.name, email = u.email, passwordEnc = u.passwordEnc, superuser = u.superuser)

  def toUserDef(u:User) = {
    val ud = UserDef(name = u.name, email = u.email, superuser = u.superuser)
    ud.passwordEnc = u.passwordEnc    // Not an actual field, so setter needs to be invoked manually
    ud
  }
}