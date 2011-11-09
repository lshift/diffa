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

package net.lshift.diffa.kernel.frontend.wire

import net.lshift.diffa.kernel.participants.ParticipantFactory
import net.lshift.diffa.kernel.client.{Actionable, ActionableRequest, ActionsClient}
import InvocationResult._
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpPost
import io.Source
import net.lshift.diffa.kernel.diag.{DiagnosticLevel, DiagnosticsManager}
import net.lshift.diffa.kernel.frontend.PairDef
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.config.{DiffaPairRef, RepairAction, DiffaPair, DomainConfigStore}

/**
 * This is a conduit to the actions that are provided by participants
 */
class ActionsProxy(val config:DomainConfigStore,
                   val systemConfig:SystemConfigStore,
                   val factory:ParticipantFactory, val diagnostics:DiagnosticsManager)
    extends ActionsClient {

  def listActions(pair:DiffaPairRef): Seq[Actionable] =
    withValidPair(pair) { p =>
      config.listRepairActionsForPair(pair.domain, pair.key).map(Actionable.fromRepairAction(pair.domain,_))
    }

  def listEntityScopedActions(pair:DiffaPairRef) = listActions(pair).filter(_.scope == RepairAction.ENTITY_SCOPE)

  def listPairScopedActions(pair:DiffaPairRef) = listActions(pair).filter(_.scope == RepairAction.PAIR_SCOPE)

  def invoke(request: ActionableRequest): InvocationResult =
    withValidPair(request.domain, request.pairKey) { pair =>
      val pairRef = pair.asRef
      val client = new DefaultHttpClient
      val repairAction = config.getRepairActionDef(request.domain, request.actionId, request.pairKey)
      val url = repairAction.scope match {
        case RepairAction.ENTITY_SCOPE => repairAction.url.replace("{id}", request.entityId)
        case RepairAction.PAIR_SCOPE => repairAction.url
      }
      val actionDescription = "\"" + repairAction.name + "\" on " + (repairAction.scope match {
        case RepairAction.ENTITY_SCOPE => "entity " + request.entityId + " of pair " + request.pairKey
        case RepairAction.PAIR_SCOPE => "pair " + request.pairKey
      })
      diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, "Initiating action " + actionDescription)

      try {
        val httpResponse = client.execute(new HttpPost(url))
        val httpCode = httpResponse.getStatusLine.getStatusCode
        val httpEntity = Source.fromInputStream(httpResponse.getEntity.getContent).mkString

        if (httpCode >= 200 && httpCode < 300) {
          diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, "Action " + actionDescription + " succeeded: " + httpEntity)
        } else {
          diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Action " + actionDescription + " failed: " + httpEntity)
        }
        InvocationResult.received(httpCode, httpEntity)
      }
      catch {
        case e =>
          diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Action " + actionDescription + " failed: " + e.getMessage)
          InvocationResult.failure(e)
      }
    }

  def withValidPair[T](pair:DiffaPairRef)(f: DiffaPair => T) : T = withValidPair(pair.domain, pair.key)(f)

  def withValidPair[T](domain:String,pairKey:String)(f: DiffaPair => T) : T = {
    val p = systemConfig.getPair(domain, pairKey)
    f(p)
  }

}
