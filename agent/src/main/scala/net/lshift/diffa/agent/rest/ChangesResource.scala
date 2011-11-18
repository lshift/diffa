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

import net.lshift.diffa.kernel.frontend.Changes
import javax.ws.rs.core.Response
import net.lshift.diffa.docgen.annotations.{MandatoryParams, OptionalParams, Description}
import net.lshift.diffa.docgen.annotations.MandatoryParams.MandatoryParam
import net.lshift.diffa.docgen.annotations.MandatoryParams.MandatoryParam._
import javax.ws.rs._
import net.lshift.diffa.participant.changes.ChangeEvent

/**
 * Resource allowing participants to provide details of changes that have occurred.
 */
class ChangesResource(changes:Changes, domain:String) {
  @POST
  @Path("/{endpoint}")
  @Consumes(Array("application/json"))
  @Description("Submits a change for the given endpoint within a domain")
  @MandatoryParams(Array(new MandatoryParam(name="endpoint", datatype="string", description="Endpoint Identifier")))
  def submitChange(@PathParam("endpoint") endpoint: String, e:ChangeEvent) = {
    changes.onChange(domain, endpoint, e)
    
    Response.status(Response.Status.ACCEPTED).`type`("text/plain").build()
  }
}