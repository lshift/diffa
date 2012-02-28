/**
 * Copyright (C) 2012 LShift Ltd.
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

import javax.ws.rs.core.Response
import net.lshift.diffa.docgen.annotations.Description
import javax.ws.rs.{Path, GET}
import org.springframework.stereotype.Component

/**
 * Resource providing overall status information on the agent. It is intended for use as an endpoint that load balancers
 * and monitoring systems can watch to determine whether the agent is in a healthy state.
 */
@Path("/status")
@Component
class StatusResource {
  @GET
  @Description("Retrieves a basic yes/no status of the agent for use with monitoring and balancing systems")
  def checkStatus = {
    Response.ok.build
  }
}