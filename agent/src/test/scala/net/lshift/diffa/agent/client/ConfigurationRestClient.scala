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

import scala.collection.JavaConversions._
import com.sun.jersey.api.client.ClientResponse
import net.lshift.diffa.kernel.frontend._
import javax.ws.rs.core.MediaType
import org.eclipse.jetty.io.EndPoint
import net.lshift.diffa.client.RestClientParams

class ConfigurationRestClient(serverRootUrl:String, domain:String, params: RestClientParams = RestClientParams.default)
    extends DomainAwareRestClient(serverRootUrl, domain, "rest/{domain}/config/", params) {

  def declareEndpoint(e:EndpointDef) = {
    create("endpoints", e)
    e
  }

  def declarePair(p:PairDef) = {
    create("pairs", p)
    p
  }

  def declareRepairAction(name: String, id: String, scope: String, pairKey: String) = {
    val action = new RepairActionDef(name, id, scope, pairKey)
    create("/pairs/"+pairKey+"/repair-actions", action)
    action
  }

  def removeRepairAction(name: String, pairKey: String) {
    delete("/pairs/"+pairKey+"/repair-actions/"+name)
  }

  def declareEscalation(name: String, pairKey: String, action: String, actionType: String, event: String, origin: String) = {
    val escalation = new EscalationDef(name, pairKey, action, actionType, event, origin)
    create("/pairs/"+pairKey+"/escalations", escalation)
    escalation
  }

  def removeEscalation(name: String, pairKey: String) = {
    delete("/pairs/" + pairKey + "/escalations/" + name)
  }

  def makeDomainMember(userName: String) = resource.path("members/" + userName).post()
  def removeDomainMembership(userName: String) = delete("/members/" + userName)
  def listDomainMembers = rpc("members/",classOf[Array[UserDef]])

  def deletePair(pairKey: String) = {
    val path = resource.path("pairs").path(pairKey)
    val response = path.delete(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 204     => // Successfully submitted (202 is "No Content")
      case x:Int   => handleHTTPError(x,path, status)
    }
  }

  def getEndpoint(name:String) = rpc("endpoints/" + name, classOf[EndpointDef])
}
