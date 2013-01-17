package net.lshift.diffa.agent.client

/**
 * Copyright (C) 2012 LShift Ltd.
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

import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.core.util.MultivaluedMapImpl
import net.lshift.diffa.client.{RequestBuildingHelper, RestClientParams}
import java.lang.String
import net.lshift.diffa.adapter.scanning.{ScanAggregation, ScanConstraint}

class InventoryRestClient(serverRootUrl:String, domain:String, params: RestClientParams = RestClientParams.default)
    extends DomainAwareRestClient(serverRootUrl, domain, "domains/{domain}/inventory/", params) {

  def startInventory(epName: String):Seq[String] = startInventory(epName, None)
  
  def startInventory(epName: String, view:Option[String]):Seq[String] = {
    val path = resource.path(epNameAndMaybeView(epName, view))
    val response = path.get(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 200     => response.getEntity(classOf[String]).split("\n")
      case 400     => throw new RuntimeException(response.getEntity(classOf[String]))
      case x:Int   => handleHTTPError(x,path, status)
    }
  }

  def uploadInventory(epName:String, constraints:Seq[ScanConstraint], aggregations:Seq[ScanAggregation], content:String):Seq[String] =
    uploadInventory(epName, None, constraints, aggregations, content)

  def uploadInventory(epName:String, view:Option[String], constraints:Seq[ScanConstraint], aggregations:Seq[ScanAggregation], content:String):Seq[String] = {
    val params = new MultivaluedMapImpl()
    RequestBuildingHelper.constraintsToQueryArguments(params, constraints)
    RequestBuildingHelper.aggregationsToQueryArguments(params, aggregations)

    val path = resource.path(epNameAndMaybeView(epName, view)).queryParams(params)
    val response = path.entity(content, "text/csv").post(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 202     => response.getEntity(classOf[String]).split("\n") // Successfully submitted (202 is "Accepted")
      case 400     => throw new RuntimeException(response.getEntity(classOf[String]))
      case x:Int   => handleHTTPError(x,path, status)
    }
  }

  def epNameAndMaybeView(epName:String, view:Option[String]) =
    view match { case None => epName; case Some(v) => epName + "/" + v }
}