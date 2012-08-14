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

import javax.ws.rs.core.{UriInfo, Response}

/**
 * Helper class to provide commonly used HTTP responses
 */
object ResponseUtils {

  /**
   * Constructs an HTTP 201 response for the given identifier in the given context
   */
  def resourceCreated(id:String, uriInfo:UriInfo) = {
    val uri = uriInfo.getAbsolutePathBuilder().path(id).build()
    Response.created(uri).build()
  }

  /**
   * Constructs an HTTP 201 response for the given identifier in the given context
   */
  def resourceDeleted() = {
    Response.noContent().build()
  }
}