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
import net.lshift.diffa.kernel.config.User
import javax.ws.rs._
import core.{Response, Context, UriInfo}
import net.lshift.diffa.agent.rest.ResponseUtils._
import net.lshift.diffa.kernel.frontend.FrontendConversions._
import net.lshift.diffa.kernel.frontend.{SystemConfiguration, UserDef, DomainDef}
import org.springframework.security.access.prepost.PreAuthorize
import javax.servlet.http.HttpServletRequest
import net.lshift.diffa.adapter.scanning._
import scala.collection.JavaConversions._

/**
 * This handles all of the user specific admin
 */
@Path("/security")
@Component
@PreAuthorize("hasRole('root')")
class SecurityResource {

  @Autowired var systemConfig:SystemConfiguration = null
  @Context var uriInfo:UriInfo = null

  @GET
  @Path("/users")
  @Produces(Array("application/json"))
  def listUsers() = systemConfig.listUsers.toArray

  @GET
  @Produces(Array("application/json"))
  @Path("/users/{name}")
  def getUser(@PathParam("name") name:String) = systemConfig.getUser(name)

  @POST
  @Path("/users")
  @Consumes(Array("application/json"))
  def createUser(user:UserDef) = {
    systemConfig.createOrUpdateUser(user)
    resourceCreated(user.name, uriInfo)
  }

  @PUT
  @Consumes(Array("application/json"))
  @Produces(Array("application/json"))
  @Path("/users/{name}")
  def updateUser(@PathParam("name") name:String, user:UserDef) = systemConfig.createOrUpdateUser(user)
  // TODO This PUT is buggy

  @GET
  @Produces(Array("text/plain"))
  @Path("/users/{name}/token")
  def getUserToken(@PathParam("name") name:String) = systemConfig.getUserToken(name)

  @DELETE
  @Path("/users/{name}/token")
  def clearUserToken(@PathParam("name") name:String) = {
    systemConfig.clearUserToken(name)

    Response.noContent().build()
  }

  @DELETE
  @Path("/users/{name}")
  def deleteUser(@PathParam("name") name:String) = systemConfig.deleteUser(name)
  
  @GET
  @Produces(Array("application/json"))
  @Path("/users/{name}/memberships")
  def listUserDomains(@PathParam("name") name: String) : Array[String] =
    systemConfig.listDomainMemberships(name).map(m => m.domain).toArray

  @GET
  @Produces(Array("application/json"))
  @Path("/scan")
  def scan(@Context request:HttpServletRequest) = {
    def generateVersion(user:User) = ScannableUtils.generateDigest(user.name, user.token)

    val constraintsBuilder = new ConstraintsBuilder(request)
    constraintsBuilder.maybeAddStringPrefixConstraint("name")
    val constraints = constraintsBuilder.toList

    val aggregationsBuilder = new AggregationBuilder(request)
    aggregationsBuilder.maybeAddStringPrefixAggregation("name")
    val aggregations = aggregationsBuilder.toList

    val aggregated = new UserScanningParticipant(systemConfig).perform(constraints, aggregations).toArray.sortBy(_.getId)

    Response.ok(aggregated).build()
  }
}