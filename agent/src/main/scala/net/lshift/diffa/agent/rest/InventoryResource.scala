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
import net.lshift.diffa.participant.scanning.ConstraintsBuilder
import net.lshift.diffa.kernel.config.DomainConfigStore
import scala.collection.JavaConversions._

/**
 * Resource allowing participants to provide bulk details of their current status.
 */
class InventoryResource(changes:Changes, configStore:DomainConfigStore, domain:String) {
  @POST
  @Path("/{endpoint}")
  @Consumes(Array("text/csv"))
  @Description("Submits an inventory for the given endpoint within a domain")
  @MandatoryParams(Array(new MandatoryParam(name="endpoint", datatype="string", description="Endpoint Identifier")))
  def submitInventory(@PathParam("endpoint") endpoint: String, @Context request:HttpServletRequest, content:ScanResultList) = {
    val constraintsBuilder = new ConstraintsBuilder(request)

    val ep = configStore.getEndpoint(domain, endpoint)
    ep.buildConstraints(constraintsBuilder)

    changes.submitInventory(domain, endpoint, constraintsBuilder.toList.toSeq, content.results)
    
    Response.status(Response.Status.ACCEPTED).`type`("text/plain").build()
  }
}