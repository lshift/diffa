package net.lshift.diffa.agent.client

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

import javax.ws.rs.core.MediaType
import com.sun.jersey.api.client.ClientResponse
import scala.collection.JavaConversions._
import net.lshift.diffa.client.{BadRequestException, RestClientParams}
import net.lshift.diffa.kernel.config.system.PolicyStatement

/**
 * A RESTful client to manage policies.
 */
class PoliciesRestClient(serverRootUrl:String, domain:String, params: RestClientParams = RestClientParams.default)
    extends DomainAwareRestClient(serverRootUrl, domain, "domains/{domain}/policies/", params) {

  def storePolicy(name: String, stmts:Seq[PolicyStatement]) = {
    val p = resource.path(name)
    val media = p.`type`(MediaType.APPLICATION_JSON_TYPE)
    val response = media.put(classOf[ClientResponse], seqAsJavaList(stmts.toList))

    def logError(status:Int) = {
      log.error("HTTP %s: Could not store policy %s at %s (%s)".format(status, stmts, p.getURI, response.getEntity(classOf[String])))
    }

    response.getStatus match {
      case 200 => ()
      case 204 => ()
      case 400 => {
        logError(response.getStatus)
        throw new BadRequestException(response.getStatus + "")
      }
      case _   => {
        logError(response.getStatus)
        throw new RuntimeException(response.getStatus + "")
      }
    }
  }

  def retrievePolicy(name:String):Seq[PolicyStatement] = {
    val path = resource.path(name)
    val media = path.accept(MediaType.APPLICATION_JSON_TYPE)
    val response = media.get(classOf[ClientResponse])

    val status = response.getClientResponseStatus

    status.getStatusCode match {
      case 200 => {
        response.getEntity(classOf[Array[PolicyStatement]]).toSeq
      }
      case x:Int   => handleHTTPError(x, path, status)
    }
  }

  def removePolicy(name: String) = {
    delete("/" + name)
    true
  }
}