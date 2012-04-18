package net.lshift.diffa.agent.rest

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
import net.lshift.diffa.docgen.annotations.{MandatoryParams, Description}
import net.lshift.diffa.docgen.annotations.MandatoryParams.MandatoryParam
import javax.ws.rs._
import core.{Context, UriInfo, Response}
import net.lshift.diffa.kernel.frontend.{Configuration, Changes}
import javax.servlet.http.HttpServletRequest
import net.lshift.diffa.kernel.config.DomainConfigStore
import scala.collection.JavaConversions._
import com.sun.jersey.core.util.MultivaluedMapImpl
import net.lshift.diffa.client.RequestBuildingHelper
import java.net.URLEncoder
import net.lshift.diffa.participant.scanning.{ScanRequest, AggregationBuilder, ConstraintsBuilder}

/**
 * Resource allowing participants to provide bulk details of their current status.
 */
class InventoryResource(changes:Changes, configStore:DomainConfigStore, domain:String) {
  @GET
  @Path("/{endpoint}")
  @Description("Retrieves a list of inventory segments that should be submitted to sync the given endpoint")
  @MandatoryParams(Array(new MandatoryParam(name="endpoint", datatype="string", description="Endpoint Identifier")))
  def startInventory(@PathParam("endpoint") endpoint: String):Response = startInventory(endpoint, null)

  @GET
  @Path("/{endpoint}/{view}")
  @Description("Retrieves a list of inventory segments that should be submitted to sync the given endpoint")
  @MandatoryParams(Array(
    new MandatoryParam(name="endpoint", datatype="string", description="Endpoint Identifier"),
    new MandatoryParam(name="view", datatype="string", description="Endpoint View")
  ))
  def startInventory(@PathParam("endpoint") endpoint: String, @PathParam("view") view:String):Response = {
    val requests = changes.startInventory(domain, endpoint, if (view != null) Some(view) else None)

    Response.status(Response.Status.OK).
      `type`("text/plain").
      entity(ScanRequestWriter.writeScanRequests(requests)).build()
  }

  @POST
  @Path("/{endpoint}")
  @Consumes(Array("text/csv"))
  @Description("Submits an inventory for the given endpoint within a domain")
  @MandatoryParams(Array(new MandatoryParam(name="endpoint", datatype="string", description="Endpoint Identifier")))
  def submitInventory(@PathParam("endpoint") endpoint: String, @Context request:HttpServletRequest, content:ScanResultList):Response =
    submitInventory(endpoint, null, request, content)

  @POST
  @Path("/{endpoint}/{view}")
  @Consumes(Array("text/csv"))
  @Description("Submits an inventory for the given endpoint within a domain")
  @MandatoryParams(Array(
    new MandatoryParam(name="endpoint", datatype="string", description="Endpoint Identifier"),
    new MandatoryParam(name="view", datatype="string", description="Endpoint View")
  ))
  def submitInventory(@PathParam("endpoint") endpoint: String, @PathParam("view") view: String, @Context request:HttpServletRequest, content:ScanResultList):Response = {
    val constraintsBuilder = new ConstraintsBuilder(request)
    val aggregationBuilder = new AggregationBuilder(request)

    val ep = configStore.getEndpoint(domain, endpoint)
    ep.buildConstraints(constraintsBuilder)
    ep.buildAggregations(aggregationBuilder)

    val nextRequests = changes.submitInventory(domain, endpoint, if (view != null) Some(view) else None,
      constraintsBuilder.toList.toSeq, aggregationBuilder.toList.toSeq, content.results)
    
    Response.status(Response.Status.ACCEPTED).
      `type`("text/plain").
      entity(ScanRequestWriter.writeScanRequests(nextRequests)).build()
  }
}
