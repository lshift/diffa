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

import net.lshift.diffa.kernel.actors.PairPolicyClient
import javax.ws.rs.core.Response
import net.lshift.diffa.kernel.frontend.Configuration
import javax.ws.rs._
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.config.{DiffaPairRef, DomainConfigStore}
import org.slf4j.{LoggerFactory, Logger}
import net.lshift.diffa.kernel.util.AlertCodes._

class ScanningResource(val pairPolicyClient:PairPolicyClient,
                       val config:Configuration,
                       val domainConfigStore:DomainConfigStore,
                       val diagnostics:DiagnosticsManager,
                       val domain:String,
                       val currentUser:String) {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  @GET
  @Path("/states")
  def getAllPairStates = {
    val states = diagnostics.retrievePairScanStatesForDomain(domain)
    Response.ok(scala.collection.JavaConversions.mapAsJavaMap(states)).build
  }

  @POST
  @Path("/pairs/{pairKey}/scan")
  def startScan(@PathParam("pairKey") pairKey:String, @FormParam("view") view:String) = {

    val infoString = formatAlertCode(domain, pairKey, API_SCAN_STARTED) + " scan initiated by " + currentUser
    val message = if (view != null) {
      infoString + " for " + view + " view"
    } else {
      infoString
    }

    log.info(message)

    pairPolicyClient.scanPair(DiffaPairRef(pairKey, domain), Option(view), Some(currentUser))
    Response.status(Response.Status.ACCEPTED).build
  }

  @DELETE
  @Path("/pairs/{pairKey}/scan")
  def cancelScanning(@PathParam("pairKey") pairKey:String) = {
    pairPolicyClient.cancelScans(DiffaPairRef(pairKey, domain))
    Response.status(Response.Status.OK).build
  }

}