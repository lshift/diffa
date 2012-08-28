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
package net.lshift.diffa.agent.client

import net.lshift.diffa.client.{RestClientParams, AccessDeniedException, NotFoundException, ExternalRestClient}
import net.lshift.diffa.kernel.preferences.FilteredItemType
import javax.ws.rs.core.MediaType
import com.sun.jersey.api.client.ClientResponse
import net.lshift.diffa.kernel.config.PairRef
import scala.collection.JavaConversions._

class UsersRestClient(rootUrl:String, username:String, params: RestClientParams = RestClientParams.default)
  extends ExternalRestClient(rootUrl, "/users/" + username, params) {

  def removeFilter(spacePath:String, pair:String, itemType:FilteredItemType)
    = delete("/" + spacePath + "/" + pair + "/filter/"  + itemType.toString)

  def createFilter(spacePath:String, pair:String, itemType:FilteredItemType) = {
    val path = resource.path("/" + spacePath + "/" + pair + "/filter/"  + itemType.toString)
    val media = path.accept(MediaType.TEXT_PLAIN)
    val response = media.put(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 204   => ()
      case 403   => throw new AccessDeniedException(path.toString)
      case 404   => throw new NotFoundException(path.toString)
      case x:Int => handleHTTPError(x, path, status)
    }
  }

  def getFilteredItems(domain:String, itemType:FilteredItemType) : Seq[String] = {
    val path = resource.path("/" + domain + "/filter/"  + itemType.toString)
    val media = path.accept(MediaType.APPLICATION_JSON)
    val response = media.get(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 200   => response.getEntity(classOf[java.util.List[String]]).toList
      case 404   => throw new NotFoundException(path.toString)
      case x:Int => handleHTTPError(x, path, status)
    }
  }
}
