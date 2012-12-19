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

import javax.ws.rs.ext.{Provider, ExceptionMapper}
import org.jooq.exception.DataAccessException
import javax.ws.rs.core.Response
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.util.AlertCodes._
import java.sql.SQLIntegrityConstraintViolationException

@Provider
class DataAccessExceptionMapper extends ExceptionMapper[DataAccessException] {

  val log = LoggerFactory.getLogger(getClass)

  def toResponse(x: DataAccessException): Response = {

    if (x.getCause.isInstanceOf[SQLIntegrityConstraintViolationException]) {
      log.warn(formatAlertCode(INTEGRITY_CONSTRAINT_VIOLATED) + " " + x.getMessage)
      Response.status(Response.Status.BAD_REQUEST).entity("Inconsistent parameters").`type`("text/plain").build()
    }
    else {
      log.error(formatAlertCode(DB_ERROR) + " " + x.getMessage, x)
      Response.status(Response.Status.INTERNAL_SERVER_ERROR).build()
    }

  }
}
