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

package net.lshift.diffa.agent.client

import net.lshift.diffa.kernel.frontend.DomainDef
import javax.ws.rs.core.MediaType
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.core.util.MultivaluedMapImpl
import net.lshift.diffa.client.{NotFoundException, ExternalRestClient}
import net.lshift.diffa.client.{RestClientParams, ExternalRestClient}
import collection.immutable.Map
import java.lang.String
import com.sun.jersey.api.representation.Form


class SystemConfigRestClient(rootUrl:String, params: RestClientParams = RestClientParams.default)
    extends ExternalRestClient(rootUrl, "root/", params) {

  @Deprecated
  def declareDomain(domain:DomainDef) = create("spaces", domain)

  def removeDomain(name: String) = delete("/spaces/" + name)
  
  def setConfigOption(key:String, value:String) = {    
    val path = resource.path("/system/config/" + key)
    val response = path.`type`(MediaType.TEXT_PLAIN).put(classOf[ClientResponse], value)
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 204 | 304    => ()
      case x:Int => handleHTTPError(x, path, status)
    }
  }

  def setConfigOptions(options: Map[String, String]) {
    val form = new Form()
    options.foreach { case (k, v) => form.add(k, v)}

    val path = resource.path("/system/config")
    val response = path.`type`(MediaType.APPLICATION_FORM_URLENCODED).post(classOf[ClientResponse], form)
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 204 | 304    => ()
      case x:Int => handleHTTPError(x, path, status)
    }
  }


  def deleteConfigOption(key:String) = {
    val path = resource.path("/system/config/" + key)
    val response = path.delete(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 204   => ()
      case x:Int => handleHTTPError(x, path, status)
    }
  }

  def setHardSystemLimit(name:String, value:Int) = setValue("/system/limits/" + name + "/hard", value)
  def setDefaultSystemLimit(name:String, value:Int) = setValue("/system/limits/" + name + "/default", value)

  def getEffectiveSystemLimit(name:String) : Int = {
    val path = resource.path("/system/limits/" + name)
    val media = path.accept(MediaType.TEXT_PLAIN)
    val response = media.get(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 200   => response.getEntity(classOf[String]).toInt
      case 404   => throw new NotFoundException(path.toString)
      case x:Int => handleHTTPError(x, path, status)
    }
  }

  def getConfigOption(key:String) = {
    val path = resource.path("/system/config/" + key)
    val media = path.accept(MediaType.TEXT_PLAIN)
    val response = media.get(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 200   => response.getEntity(classOf[String])
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