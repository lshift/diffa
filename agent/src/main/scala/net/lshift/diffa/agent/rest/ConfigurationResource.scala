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

import javax.ws.rs._
import core.{Response, UriInfo}
import scala.collection.JavaConversions._
import net.lshift.diffa.agent.rest.ResponseUtils._
import net.lshift.diffa.kernel.frontend._
import net.lshift.diffa.kernel.frontend.FrontendConversions._
import net.lshift.diffa.kernel.config.{DiffaPairRef, BreakerHelper}

/**
 * This is a REST interface to the Configuration abstraction.
 * @see Configuration
 */
class ConfigurationResource(val config:Configuration,
                            val breakers:BreakerHelper,
                            val domain:String,
                            val currentUser:String,
                            val uri:UriInfo) {

  @GET
  @Path("/xml")
  @Produces(Array("application/xml"))
  def retrieveConfiguration() =
    config.retrieveConfiguration(domain).get // existence will have been checked in DomainResource

  @POST
  @Path("/xml")
  @Consumes(Array("application/xml"))
  def applyConfiguration(newConfig:DiffaConfig) = config.applyConfiguration(domain,newConfig, Some(currentUser))

  @GET
  @Path("/endpoints")
  @Produces(Array("application/json"))
  def listEndpoints() = config.listEndpoints(domain).toArray

  @GET
  @Produces(Array("application/json"))
  @Path("/endpoints/{id}")
  def getEndpoint(@PathParam("id") id:String) = config.getEndpointDef(domain, id)

  @POST
  @Path("/endpoints")
  @Consumes(Array("application/json"))
  def createEndpoint(e:EndpointDef) = {
    config.createOrUpdateEndpoint(domain, e)
    resourceCreated(e.name, uri)
  }

  @PUT
  @Consumes(Array("application/json"))
  @Produces(Array("application/json"))
  @Path("/endpoints/{id}")
  def updateEndpoint(@PathParam("id") id:String, e:EndpointDef) = {
    config.createOrUpdateEndpoint(domain, e)
    e
  }

  @DELETE
  @Path("/endpoints/{id}")
  def deleteEndpoint(@PathParam("id") id:String) = config.deleteEndpoint(domain, id)

  @GET
  @Path("/pairs")
  @Produces(Array("application/json"))
  def listPairs() = config.listPairs(domain).toArray

  @POST
  @Path("/pairs")
  @Consumes(Array("application/json"))
  def createPair(p:PairDef) = {
    config.createOrUpdatePair(domain, p)
    resourceCreated(p.key, uri)
  }

  @PUT
  @Consumes(Array("application/json"))
  @Produces(Array("application/json"))
  @Path("/pairs/{id}")
  def updatePair(@PathParam("id") id:String, p:PairDef) = {
    config.createOrUpdatePair(domain, p)
    p
  }

  @DELETE
  @Path("/pairs/{id}")
  def deletePair(@PathParam("id") id:String) = config.deletePair(domain, id)

  @GET
  @Path("/pairs/{id}/repair-actions")
  @Produces(Array("application/json"))
  def listRepairActionsForPair(@PathParam("id") pairKey: String) = config.listRepairActionsForPair(domain, pairKey).toArray

  @POST
  @Path("/pairs/{id}/repair-actions")
  @Consumes(Array("application/json"))
  def createRepairAction(@PathParam("id") id:String, a: RepairActionDef) = {
    config.createOrUpdateRepairAction(domain, id, a)
    resourceCreated(a.name, uri)
  }

  @DELETE
  @Path("/pairs/{id}/repair-actions")
  def clearRepairActions(@PathParam("id") id:String) = {
    config.clearRepairActions(domain, id)
    resourceDeleted()
  }

  @DELETE
  @Path("/pairs/{pairKey}/repair-actions/{name}")
  def deleteRepairAction(@PathParam("name") name: String, @PathParam("pairKey") pairKey: String) {
    config.deleteRepairAction(domain, name, pairKey)
  }

  @POST
  @Path("/pairs/{id}/escalations")
  @Consumes(Array("application/json"))
  def createEscalation(@PathParam("id") id:String, e: EscalationDef) = {
    config.createOrUpdateEscalation(domain, id, e)
    resourceCreated(e.name, uri)
  }

  @DELETE
  @Path("/pairs/{id}/escalations")
  def clearEscalations(@PathParam("id") id:String) = {
    config.clearEscalations(domain, id)
    resourceDeleted()
  }

  @DELETE
  @Path("/pairs/{pairKey}/escalations/{name}")
  def deleteEscalation(@PathParam("name") name: String, @PathParam("pairKey") pairKey: String) {
    config.deleteEscalation(domain, name, pairKey)
  }

  @GET
  @Path("/pairs/{id}")
  @Produces(Array("application/json"))
  def getPair(@PathParam("id") id:String) = config.getPairDef(domain, id)

  @POST
  @Path("/members/{username}")
  def makeDomainMember(@PathParam("username") userName:String) = {
    val member = config.makeDomainMember(domain, userName)
    resourceCreated(member.user, uri)
  }

  @DELETE
  @Path("/members/{username}")
  def removeDomainMembership(@PathParam("username") userName:String) = config.removeDomainMembership(domain, userName)

  @GET
  @Path("/members")
  @Produces(Array("application/json"))
  def listDomainMembers : Array[String] = config.listDomainMembers(domain).map(m => m.user).toArray

  @PUT
  @Path("/pairs/{id}/breakers/escalations")
  def tripAllEscalations(@PathParam("id") id:String) = {
    breakers.tripAllEscalations(DiffaPairRef(domain = domain, key = id))
    resourceCreated("*", uri)
  }

  @DELETE
  @Path("/pairs/{id}/breakers/escalations")
  def resetAllEscalations(@PathParam("id") id:String) = {
    breakers.clearAllEscalations(DiffaPairRef(domain = domain, key = id))
    resourceDeleted()
  }

  @PUT
  @Path("/pairs/{id}/breakers/escalations/{name}")
  def tripAllEscalations(@PathParam("id") id:String, @PathParam("name") name:String) = {
    breakers.tripEscalation(DiffaPairRef(domain = domain, key = id), name)
    resourceCreated(name, uri)
  }

  @DELETE
  @Path("/pairs/{id}/breakers/escalations/{name}")
  def resetAllEscalations(@PathParam("id") id:String, @PathParam("name") name:String) = {
    breakers.clearEscalation(DiffaPairRef(domain = domain, key = id), name)
    resourceDeleted()
  }
}
