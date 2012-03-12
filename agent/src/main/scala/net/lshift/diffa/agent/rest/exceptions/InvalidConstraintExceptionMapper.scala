package net.lshift.diffa.agent.rest.exceptions

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

import javax.ws.rs.ext.{Provider, ExceptionMapper}
import javax.ws.rs.core.Response
import net.lshift.diffa.kernel.util.InvalidConstraintException

/**
 * This transform all InvalidInventoryExceptions that occur in the application and returns an HTTP 400 to the requester.
 */
@Provider
class InvalidConstraintExceptionMapper extends ExceptionMapper[InvalidConstraintException] {

  def toResponse(x: InvalidConstraintException) = {
    Response.status(Response.Status.BAD_REQUEST).entity("Constraint was invalid: " + x.getMessage).`type`("text/plain").build()
  }
}