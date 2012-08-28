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
import javax.ws.rs._
import net.lshift.diffa.participant.changes.ChangeEvent
import net.lshift.diffa.schema.servicelimits.ChangeEventRate
import net.lshift.diffa.kernel.limiting.{DomainRateLimiterFactory, ServiceLimiterKey, ServiceLimiterRegistry}
import net.lshift.diffa.participant.common.{InvalidEntityException, ScanEntityValidator}
import net.lshift.diffa.kernel.differencing.EntityValidator

/**
 * Resource allowing participants to provide details of changes that have occurred.
 */
class ChangesResource(changes:Changes, space:Long, rateLimiterFactory: DomainRateLimiterFactory,
                      validator: ScanEntityValidator) {

  def this(changes:Changes, space:Long, rateLimiterFactory: DomainRateLimiterFactory) =
    this(changes, space, rateLimiterFactory, EntityValidator)
  @POST
  @Path("/{endpoint}")
  @Consumes(Array("application/json"))
  def submitChange(@PathParam("endpoint") endpoint: String, e:ChangeEvent) = {
    val limiter = ServiceLimiterRegistry.get(
      ServiceLimiterKey(ChangeEventRate, Some(space), None),
      () => rateLimiterFactory.createRateLimiter(space))

    val responseBuilder = if (limiter.accept()) {
      try {
        validator.process(e)
        changes.onChange(space, endpoint, e)
        Response.status(Response.Status.ACCEPTED)
      } catch {
        case e: InvalidEntityException => Response.status(400).entity(e.getMessage + "\n")
      }
    } else {
      Response.status(420)
    }
    responseBuilder.`type`("text/plain").build()
  }
}


