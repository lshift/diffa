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

import javax.ws.rs._
import core.UriInfo
import net.lshift.diffa.kernel.frontend.wire.InvocationResult
import net.lshift.diffa.kernel.client.{Actionable, ActionableRequest, ActionsClient}
import net.lshift.diffa.kernel.config.{PairRef, RepairAction}
import org.springframework.security.access.PermissionEvaluator
import net.lshift.diffa.agent.rest.PermissionUtils._
import net.lshift.diffa.agent.auth.{ActionTarget, PairTarget, Privileges}

class ActionsResource(val proxy:ActionsClient,
                      val space:Long,
                      val uriInfo:UriInfo,
                      val permissionEvaluator:PermissionEvaluator)
    extends IndividuallySecuredResource {

  @GET
  @Path("/{pairId}")
  @Produces(Array("application/json"))
  def listActions(@PathParam("pairId") pairId: String,
                  @QueryParam("scope") scope: String): Array[Actionable] = {
    ensurePrivilege(permissionEvaluator, Privileges.VIEW_ACTIONS, new PairTarget(space, pairId))

    (scope match {
      case RepairAction.ENTITY_SCOPE => proxy.listEntityScopedActions(PairRef(pairId, space))
      case RepairAction.PAIR_SCOPE => proxy.listPairScopedActions(PairRef(pairId, space))
      case _ => proxy.listActions(PairRef(pairId,space))
    }).toArray
  }

  @POST
  @Path("/{pairId}/{actionId}")
  @Produces(Array("application/json"))
  def invokeAction(@PathParam("pairId") pairId:String,
                   @PathParam("actionId") actionId:String) = {
    ensurePrivilege(permissionEvaluator, Privileges.INVOKE_ACTIONS, new ActionTarget(space, pairId, actionId))

    proxy.invoke(ActionableRequest(pairId, space, actionId, null))
  }

  @POST
  @Path("/{pairId}/{actionId}/{entityId}")
  @Produces(Array("application/json"))
  def invokeAction(@PathParam("pairId") pairId:String,
                   @PathParam("actionId") actionId:String,
                   @PathParam("entityId") entityId:String) : InvocationResult = {
    ensurePrivilege(permissionEvaluator, Privileges.INVOKE_ACTIONS, new ActionTarget(space, pairId, actionId))

    proxy.invoke(ActionableRequest(pairId, space, actionId, entityId))
  }

}