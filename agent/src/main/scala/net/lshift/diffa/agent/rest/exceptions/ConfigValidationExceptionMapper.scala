package net.lshift.diffa.agent.rest.exceptions

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

import javax.ws.rs.ext.{Provider, ExceptionMapper}
import javax.ws.rs.core.Response
import org.slf4j.LoggerFactory
import net.lshift.diffa.config.ConfigValidationException

/**
 * This logs all ConfigValidationException that occur in the application and returns an HTTP 400 to the requester.
 */
@Provider
class ConfigValidationExceptionMapper extends ExceptionMapper[ConfigValidationException] {

  val log = LoggerFactory.getLogger(getClass)

  def toResponse(x: ConfigValidationException) = {
    log.debug("Attempt to upload an invalid configuration:" + x.getMessage)
    Response.status(Response.Status.BAD_REQUEST).entity(x.getMessage).`type`("text/plain").build()
  }
}