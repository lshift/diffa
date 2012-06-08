/**
 * Copyright (C) 2010 - 2012 LShift Ltd.
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
package net.lshift.diffa.agent.client

import javax.ws.rs.core.MediaType
import com.sun.jersey.api.client.ClientResponse
import net.lshift.diffa.client.{NotFoundException, RestClientParams}

class DomainLimitsRestClient (serverRootUrl:String, domain:String, params: RestClientParams = RestClientParams.default)
  extends DomainAwareRestClient(serverRootUrl, domain, "domains/{domain}/limits/", params) {

  def effectiveDomainLimit(name:String) : Int = getValue("/" + name)
  def effectivePairLimit(pair:String, name:String) : Int = getValue("/" + pair + "/" + name)
  def setDomainHardLimit(name:String, value:Int) = setValue("/" + name + "/hard", value)
  def setDomainDefaultLimit(name:String, value:Int) = setValue("/" + name + "/default", value)
  def setPairLimit(pair:String, name:String, value:Int) = setValue("/" + pair + "/" + name, value)

  def getValue(resourcePath:String) : Int = {
    val path = resource.path(resourcePath)
    val media = path.accept(MediaType.TEXT_PLAIN)
    val response = media.get(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 200   => response.getEntity(classOf[String]).toInt
      case 404   => throw new NotFoundException(path.toString)
      case x:Int => handleHTTPError(x, path, status)
    }
  }

  private def setValue(resourcePath:String, value:Int) = {
    val path = resource.path(resourcePath)
    val response = path.`type`(MediaType.TEXT_PLAIN).put(classOf[ClientResponse], value.toString)
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 204 | 304    => ()
      case 404   => throw new NotFoundException(path.toString)
      case x:Int => handleHTTPError(x, path, status)
    }
  }

}
