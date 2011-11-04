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
import net.lshift.diffa.kernel.differencing.PairScanState
import scala.collection.JavaConversions._
import com.sun.jersey.core.util.MultivaluedMapImpl

/**
 * A RESTful client to manage participant scanning.
 */
class ScanningRestClient(serverRootUrl:String, domain:String, username:String = "guest", password:String = "guest")
    extends DomainAwareRestClient(serverRootUrl, domain, "rest/{domain}/scanning/", username, password) {

  def startScan(pairKey: String, view:Option[String] = None) = {
    val p = resource.path("pairs").path(pairKey).path("scan")
    val postData = new MultivaluedMapImpl()
    view match {
      case None     =>
      case Some(v)  => postData.add("view", v)
    }
    val response = p.accept(MediaType.APPLICATION_JSON_TYPE).
      `type`("application/x-www-form-urlencoded").post(classOf[ClientResponse], postData)
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 202     => // Successfully submitted (202 is "Accepted")
      case x:Int   => throw new RuntimeException("HTTP " + x + " : " + status.getReasonPhrase)
    }
    true
  }

  def getScanStatus = {
    val path = resource.path("states")
    val media = path.accept(MediaType.APPLICATION_JSON_TYPE)
    val response = media.get(classOf[ClientResponse])

    val status = response.getClientResponseStatus

    status.getStatusCode match {
      case 200 => {
        val responseData = response.getEntity(classOf[java.util.Map[String, String]])
        responseData.map {case (k, v) => k -> PairScanState.valueOf(v) }.toMap
      }
      case x:Int   => handleHTTPError(x, path, status)
    }
  }

  def cancelScanning(pairKey: String) = {
    delete("/pairs/" + pairKey + "/scan")
    true
  }
}