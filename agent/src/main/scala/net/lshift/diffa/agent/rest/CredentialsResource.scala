/**
 * Copyright (C) 2010-2012 LShift Ltd.
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

import net.lshift.diffa.kernel.config.DomainCredentialsManager
import net.lshift.diffa.kernel.frontend.InboundExternalHttpCredentialsDef
import javax.ws.rs._
import core.{Response, UriInfo}
import net.lshift.diffa.agent.rest.ResponseUtils._


class CredentialsResource(val credentialsManager:DomainCredentialsManager,
                          val space:Long,
                          val uri:UriInfo) {

  @GET
  @Path("/")
  @Produces(Array("application/json"))
  def listCredentials = credentialsManager.listCredentials(space).toArray

  @POST
  @Path("/")
  @Consumes(Array("application/json"))
  def addCredentials(creds:InboundExternalHttpCredentialsDef) = {
    credentialsManager.addExternalHttpCredentials(space, creds)
    resourceCreated(creds.url + "/" + creds.`type`, uri)
  }

  @DELETE
  @Path("/{url}")
  def deleteCredentials(@PathParam("url") url:String) = {
    credentialsManager.deleteExternalHttpCredentials(space, url)
    Response.noContent().build()
  }

}
