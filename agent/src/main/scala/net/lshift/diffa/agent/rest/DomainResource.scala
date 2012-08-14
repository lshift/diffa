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

import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Autowired
import javax.ws.rs.core.{UriInfo, Context}
import javax.ws.rs.{PathParam, Path}
import net.lshift.diffa.kernel.client.ActionsClient
import net.lshift.diffa.kernel.differencing.DifferencesManager
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.actors.PairPolicyClient
import net.lshift.diffa.kernel.frontend.{Changes, Configuration}
import org.springframework.security.access.prepost.PreAuthorize
import net.lshift.diffa.kernel.reporting.ReportManager
import com.sun.jersey.api.NotFoundException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.util.AlertCodes._
import net.lshift.diffa.kernel.config.system.CachedSystemConfigStore
import net.lshift.diffa.kernel.limiting.DomainRateLimiterFactory
import net.lshift.diffa.kernel.config.{BreakerHelper, DomainCredentialsManager, User, DomainConfigStore}

/**
 * The policy is that we will publish spaces as the replacement term for domains
 * but to avoid having to refactor a bunch of of code straight away, we'll just change
 * the path specification from /domains to /spaces and implement a redirect.
 */
@Path("/spaces/{domain}")
@Component
@PreAuthorize("hasPermission(#domain, 'domain-user')")
class DomainResource {

  val log = LoggerFactory.getLogger(getClass)

  @Context var uriInfo:UriInfo = null

  @Autowired var config:Configuration = null
  @Autowired var credentialsManager:DomainCredentialsManager = null
  @Autowired var actionsClient:ActionsClient = null
  @Autowired var differencesManager:DifferencesManager = null
  @Autowired var diagnosticsManager:DiagnosticsManager = null
  @Autowired var pairPolicyClient:PairPolicyClient = null
  @Autowired var domainConfigStore:DomainConfigStore = null
  @Autowired var systemConfigStore:CachedSystemConfigStore = null
  @Autowired var changes:Changes = null
  @Autowired var changeEventRateLimiterFactory: DomainRateLimiterFactory = null
  @Autowired var reports:ReportManager = null
  @Autowired var breakers:BreakerHelper = null

  private def getCurrentUser(domain:String) : String = SecurityContextHolder.getContext.getAuthentication.getPrincipal match {
    case user:UserDetails => user.getUsername
    case token:String     => {
      systemConfigStore.getUserByToken(token) match {
        case user:User => user.getName
        case _         =>
          log.warn(formatAlertCode(domain, SPURIOUS_AUTH_TOKEN) + " " + token)
          null
      }
    }
    case _                => null
  }

  private def withValidDomain[T](domain: String, resource: T) =
    if (config.doesDomainExist(domain))
      resource
    else
      throw new NotFoundException("Invalid domain")

  @Path("/config")
  def getConfigResource(@Context uri:UriInfo,
                        @PathParam("domain") domain:String) =
    withValidDomain(domain, new ConfigurationResource(config, breakers, domain, getCurrentUser(domain), uri))

  @Path("/credentials")
  def getCredentialsResource(@Context uri:UriInfo,
                             @PathParam("domain") domain:String) =
    withValidDomain(domain, new CredentialsResource(credentialsManager, domain, uri))

  @Path("/diffs")
  def getDifferencesResource(@Context uri:UriInfo,
                             @PathParam("domain") domain:String) =
    withValidDomain(domain, new DifferencesResource(differencesManager, domainConfigStore, domain, uri))

  @Path("/escalations")
  def getEscalationsResource(@PathParam("domain") domain:String) =
    withValidDomain(domain, new EscalationsResource(config, domain))

  @Path("/actions")
  def getActionsResource(@Context uri:UriInfo,
                         @PathParam("domain") domain:String) =
    withValidDomain(domain, new ActionsResource(actionsClient, domain, uri))

  @Path("/reports")
  def getReportsResource(@Context uri:UriInfo,
                         @PathParam("domain") domain:String) =
    withValidDomain(domain, new ReportsResource(domainConfigStore, reports, domain, uri))

  @Path("/diagnostics")
  def getDiagnosticsResource(@PathParam("domain") domain:String) =
    withValidDomain(domain, new DiagnosticsResource(diagnosticsManager, config, domain))

  @Path("/scanning")
  def getScanningResource(@PathParam("domain") domain:String) =
    withValidDomain(domain, new ScanningResource(pairPolicyClient, config, domainConfigStore, diagnosticsManager, domain, getCurrentUser(domain)))

  @Path("/changes")
  def getChangesResource(@PathParam("domain") domain:String) = {
    withValidDomain(domain, new ChangesResource(changes, domain, changeEventRateLimiterFactory))
  }

  @Path("/inventory")
  def getInventoryResource(@PathParam("domain") domain:String) =
    withValidDomain(domain, new InventoryResource(changes, domainConfigStore, domain))

  @Path("/limits")
  def getLimitsResource(@PathParam("domain") domain:String) =
    withValidDomain(domain, new DomainServiceLimitsResource(config, domain))
}