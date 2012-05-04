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

package net.lshift.diffa.agent.rest.exceptions

import net.lshift.diffa.kernel.util.MissingObjectException
import javax.ws.rs.ext.{Provider, ExceptionMapper}
import javax.ws.rs.core.Response
import org.slf4j.LoggerFactory

/**
 * This logs all MissingObjectException that occur in the application and returns an HTTP 404 to the requester.
 */
@Provider
class MissingObjectExceptionMapper extends ExceptionMapper[MissingObjectException] {

  val log = LoggerFactory.getLogger(getClass)

  def toResponse(x: MissingObjectException) = {
    log.debug("Attempt to perform an operation with a non-existent key:" + x.objName)
    Response.status(Response.Status.NOT_FOUND).entity(x.objName + " not found").`type`("text/plain").build()
  }
}