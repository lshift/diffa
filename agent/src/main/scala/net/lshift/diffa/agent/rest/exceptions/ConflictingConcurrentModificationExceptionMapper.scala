/*
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

package net.lshift.diffa.agent.rest.exceptions

import javax.ws.rs.ext.{ExceptionMapper, Provider}
import org.slf4j.LoggerFactory
import javax.ws.rs.core.Response
import net.lshift.diffa.kernel.config.system.ConflictingConcurrentModificationException

/**
 * The resource being declared has been concurrently modified by another user in a way
 * that conflicts with the requested state.  HTTP response code 409 is returned (Conflict).
 */
@Provider
class ConflictingConcurrentModificationExceptionMapper extends ExceptionMapper[ConflictingConcurrentModificationException] {
  val log = LoggerFactory.getLogger(getClass)

  def toResponse(cme: ConflictingConcurrentModificationException) = {
    log.debug(cme.getMessage)
    Response.status(Response.Status.CONFLICT).entity(cme.getMessage).`type`("text/plain").build()
  }
}
