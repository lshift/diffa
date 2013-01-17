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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import net.lshift.diffa.agent.rest.ResponseUtils._
import javax.ws.rs._
import core._
import org.springframework.security.access.prepost.PreAuthorize
import net.lshift.diffa.kernel.util.MissingObjectException
import com.sun.jersey.api.representation.Form
import scala.collection.JavaConversions._
import javax.servlet.http.HttpServletRequest
import net.lshift.diffa.kernel.frontend.{SystemConfiguration, DomainDef}
import net.lshift.diffa.adapter.scanning._
import net.lshift.diffa.kernel.config.ValidationUtil
import net.lshift.diffa.config.ConfigValidationException

@Path("/root")
@Component
@PreAuthorize("hasRole('root')")
class SystemConfigResource {

  @Autowired var systemConfig:SystemConfiguration = null
  @Context var uriInfo:UriInfo = null

  @POST
  @Path("/spaces")
  @Consumes(Array("application/json"))
  def createSpace(domain:DomainDef) = {
    val path = domain.name
    path.split("/").foreach(ValidationUtil.ensurePathSegmentFormat("space",_))
    systemConfig.createOrUpdateSpace(path)
    resourceCreated(domain.name, uriInfo)
  }

  @POST
  @Path("/domains")
  @Consumes(Array("application/json"))
  @Deprecated
  def createBackwardsCompatibleDomain(domain:DomainDef) = createSpace(domain)

  @DELETE
  @Path("/spaces/{name:.+}")
  def deleteSpace(@PathParam("name") name:String) = systemConfig.deleteSpace(name)

  @DELETE
  @Path("/domains/{name}")
  @Deprecated
  def deleteBackwardsCompatibleDomain(@PathParam("name") name:String) = deleteSpace(name)

  @POST
  @Path("/system/config")
  @Consumes(Array(MediaType.APPLICATION_FORM_URLENCODED))
  def setSystemConfigOption(params: Form) = {
    val update = params.keySet().map(k => {
      k -> params.getFirst(k)
    }).toMap

    systemConfig.setSystemConfigOptions(update)

    Response.noContent().build()
  }

  @PUT
  @Path("/system/config/{key}")
  @Consumes(Array("text/plain"))
  def setSystemConfigOption(@PathParam("key") key:String,
                            value:String) = {
    if (value == null) {
      throw new ConfigValidationException(key, "Config value must not be null")
    }

    val builder = systemConfig.getSystemConfigOption(key) match {
      case Some(x) if x == value => Response.notModified()
      case _                     =>
        systemConfig.setSystemConfigOption(key, value)
        Response.noContent()
    }

    builder.build()
  }

  @DELETE
  @Path("/system/config/{key}")
  def clearSystemConfigOption(@PathParam("key") key:String) = systemConfig.clearSystemConfigOption(key)
  
  @GET
  @Path("/system/config/{key}")
  @Produces(Array("text/plain"))
  def getSystemConfigOption(@PathParam("key") key:String) = {    
    systemConfig.getSystemConfigOption(key) match {
      case Some(value) => value
      case None        => throw new MissingObjectException(key)
    }
  }

  @GET
  @Path("/spaces/scan")
  @Produces(Array("application/json"))
  def scanPairs(@Context request:HttpServletRequest) = {
    def generateVersion(domain:String) = ScannableUtils.generateDigest(domain)

    val constraintsBuilder = new ConstraintsBuilder(request)
    constraintsBuilder.maybeAddStringPrefixConstraint("name")
    val constraints = constraintsBuilder.toList

    val aggregationsBuilder = new AggregationBuilder(request)
    aggregationsBuilder.maybeAddStringPrefixAggregation("name")
    val aggregations = aggregationsBuilder.toList

    val domains = ScannableUtils.filterByKey[String](systemConfig.listDomains, constraints, x => x)
    val scanResults = domains.map { d => new ScanResultEntry(d, generateVersion(d), null, Map("name" -> d)) }
    val aggregated = ScannableUtils.maybeAggregate(scanResults, aggregations, systemConfig)

    Response.ok(aggregated).build()
  }

  @GET
  @Path("/domains/scan")
  @Produces(Array("application/json"))
  @Deprecated
  def scanBackwardsCompatiblePairs(@Context request:HttpServletRequest) = scanPairs(request)

  @GET
  @Path("/system/limits/{name}")
  @Produces(Array("text/plain"))
  def effectiveServiceLimit(@PathParam("name") name:String) : String = {
    systemConfig.getEffectiveSystemLimit(name).toString
  }

  @PUT
  @Path("/system/limits/{name}/hard")
  @Produces(Array("application/json"))
  def setHardLimit(@PathParam("name") name:String,
                   value:String) = {
    systemConfig.setHardSystemLimit(name, value.toInt)
  }

  @PUT
  @Path("/system/limits/{name}/default")
  @Produces(Array("application/json"))
  def setDefaultLimit(@PathParam("name") name:String,
                      value:String) = {
    systemConfig.setDefaultSystemLimit(name, value.toInt)
  }
}