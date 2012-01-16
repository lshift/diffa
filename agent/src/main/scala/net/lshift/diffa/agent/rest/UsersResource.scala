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
import net.lshift.diffa.docgen.annotations.{MandatoryParams, Description}
import net.lshift.diffa.docgen.annotations.MandatoryParams.MandatoryParam
import net.lshift.diffa.kernel.config.User
import javax.ws.rs._
import core.{Response, Context, UriInfo}
import net.lshift.diffa.agent.rest.ResponseUtils._
import net.lshift.diffa.kernel.frontend.FrontendConversions._
import net.lshift.diffa.kernel.frontend.{SystemConfiguration, UserDef}
import org.springframework.security.access.prepost.PreAuthorize

/**
 * This handles all of the user specific admin
 */
@Path("/security")
@Component
@PreAuthorize("hasRole('root')")
class UsersResource {

  @Autowired var systemConfig:SystemConfiguration = null
  @Context var uriInfo:UriInfo = null

  @GET
  @Path("/users")
  @Produces(Array("application/json"))
  @Description("Returns a list of all the users registered with the agent.")
  def listUsers() = systemConfig.listUsers.toArray

  @GET
  @Produces(Array("application/json"))
  @Path("/users/{name}")
  @Description("Returns a user by its name.")
  @MandatoryParams(Array(new MandatoryParam(name="name", datatype="string", description="Username")))
  def getUser(@PathParam("name") name:String) = systemConfig.getUser(name)

  @POST
  @Path("/users")
  @Consumes(Array("application/json"))
  @Description("Registers a new user with the agent.")
  def createUser(user:UserDef) = {
    systemConfig.createOrUpdateUser(user)
    resourceCreated(user.name, uriInfo)
  }

  @PUT
  @Consumes(Array("application/json"))
  @Produces(Array("application/json"))
  @Path("/users/{name}")
  @Description("Updates the attributes of a user that is registered with the agent.")
  @MandatoryParams(Array(new MandatoryParam(name="name", datatype="string", description="Username")))
  def updateUser(@PathParam("name") name:String, user:UserDef) = systemConfig.createOrUpdateUser(user)
  // TODO This PUT is buggy

  @GET
  @Produces(Array("text/plain"))
  @Path("/users/{name}/token")
  @Description("Retrieves the token that can be used by the user for login")
  @MandatoryParams(Array(new MandatoryParam(name="name", datatype="string", description="Username")))
  def getUserToken(@PathParam("name") name:String) = systemConfig.getUserToken(name)

  @DELETE
  @Path("/users/{name}/token")
  @Description("Clears the user login token.")
  @MandatoryParams(Array(new MandatoryParam(name="name", datatype="string", description="Username")))
  def clearUserToken(@PathParam("name") name:String) = {
    systemConfig.clearUserToken(name)

    Response.noContent().build()
  }

  @DELETE
  @Path("/users/{name}")
  @Description("Removes an endpoint that is registered with the agent.")
  @MandatoryParams(Array(new MandatoryParam(name="name", datatype="string", description="Username")))
  def deleteUser(@PathParam("name") name:String) = systemConfig.deleteUser(name)
}