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
import net.lshift.diffa.kernel.diag.{DiagnosticLevel, DiagnosticsManager}
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.config.{DiffaPairRef, RepairAction, DiffaPair, DomainConfigStore}
import net.lshift.diffa.kernel.util.AlertCodes._
import org.apache.http.util.EntityUtils
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.{HttpConnectionParams, BasicHttpParams}
import org.slf4j.LoggerFactory
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.HttpClient
import com.sun.xml.internal.ws.Closeable

/**
 * This is a conduit to the actions that are provided by participants
 */
class ActionsProxy(val config:DomainConfigStore,
                   val systemConfig:SystemConfigStore,
                   val factory:ParticipantFactory, val diagnostics:DiagnosticsManager)
    extends ActionsClient {

  val log = LoggerFactory.getLogger(getClass)

  // use arbitrary connection and socket timeouts of five minutes
  // (this is not necessarily a sensible default, but if these parameters are not set the timeout becomes infinite)
  val fiveMinutesinMillis = 5 * 60 * 1000
  val connectionTimeoutMillis = fiveMinutesinMillis
  val socketTimeoutMillis = fiveMinutesinMillis

  def listActions(pair:DiffaPairRef): Seq[Actionable] =
    withValidPair(pair) { p =>
      config.listRepairActionsForPair(pair.domain, pair.key).map(Actionable.fromRepairAction(pair.domain,_))
    }

  def listEntityScopedActions(pair:DiffaPairRef) = listActions(pair).filter(_.scope == RepairAction.ENTITY_SCOPE)

  def listPairScopedActions(pair:DiffaPairRef) = listActions(pair).filter(_.scope == RepairAction.PAIR_SCOPE)

  def invoke(request: ActionableRequest): InvocationResult =
    withValidPair(request.domain, request.pairKey) { pair =>
      val pairRef = pair.asRef
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


      val httpClient = createHttpClient()
      try {
        val httpResponse = httpClient.execute(new HttpPost(url))
        val httpCode = httpResponse.getStatusLine.getStatusCode
        val httpEntity = EntityUtils.toString(httpResponse.getEntity)

        if (httpCode >= 200 && httpCode < 300) {
          diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, "Action " + actionDescription + " succeeded: " + httpEntity)
        } else {
          diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Action " + actionDescription + " failed: " + httpEntity)
        }
        InvocationResult.received(httpCode, httpEntity)
      } catch {
        case e: Exception =>
          diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Action " + actionDescription + " failed: " + e.getMessage)
          InvocationResult.failure(e)
      } finally {
        immediatelyShutDownClient(httpClient, pairRef)
      }
    }

  def withValidPair[T](pair:DiffaPairRef)(f: DiffaPair => T) : T = withValidPair(pair.domain, pair.key)(f)

  def withValidPair[T](domain:String,pairKey:String)(f: DiffaPair => T) : T = {
    val p = systemConfig.getPair(domain, pairKey)
    f(p)
  }

  private def createHttpClient() = {
    val params = new BasicHttpParams
    HttpConnectionParams.setConnectionTimeout(params, connectionTimeoutMillis)
    HttpConnectionParams.setSoTimeout(params, socketTimeoutMillis)
    new DefaultHttpClient(params)
  }

  private def immediatelyShutDownClient(client: HttpClient, pair: DiffaPairRef) =
    try {
      client.getConnectionManager.shutdown()
    } catch {
      case e: Exception =>
        log.warn("{} Could not shut down HTTP client: {} {}",
          Array[Object](formatAlertCode(pair, ACTION_HTTP_CLEANUP_FAILURE), e.getClass, e.getMessage))
    }

}
