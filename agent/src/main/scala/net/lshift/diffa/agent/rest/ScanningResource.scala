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
import net.lshift.diffa.kernel.config.{PairRef, DomainConfigStore}
import org.slf4j.{LoggerFactory, Logger}
import net.lshift.diffa.kernel.util.AlertCodes._

class ScanningResource(val pairPolicyClient:PairPolicyClient,
                       val config:Configuration,
                       val domainConfigStore:DomainConfigStore,
                       val diagnostics:DiagnosticsManager,
                       val space:Long,
                       val currentUser:String) {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  @GET
  @Path("/states")
  def getAllPairStates = {
    val states = diagnostics.retrievePairScanStatesForDomain(space)
    Response.ok(scala.collection.JavaConversions.mapAsJavaMap(states)).build
  }

  @POST
  @Path("/pairs/{pairKey}/scan")
  def startScan(@PathParam("pairKey") pairKey:String, @FormParam("view") view:String) = {

    val ref = PairRef(pairKey, space)

    val pair = domainConfigStore.getPairDef(ref)
    val up = domainConfigStore.getEndpointDef(space, pair.upstreamName)
    val down = domainConfigStore.getEndpointDef(space, pair.downstreamName)

    if (!up.supportsScanning && !down.supportsScanning) {

      val msg = "Neither %s nor %s support scanning".format(pair.upstreamName, pair.downstreamName)
      Response.status(Response.Status.BAD_REQUEST).entity(msg).`type`("text/plain").build()

    }
    else {

      val infoString = formatAlertCode(space, pairKey, API_SCAN_STARTED) + " scan initiated by " + currentUser
      val message = if (view != null) {
        infoString + " for " + view + " view"
      } else {
        infoString
      }

      log.info(message)

      pairPolicyClient.scanPair(ref, Option(view), Some(currentUser))
      Response.status(Response.Status.ACCEPTED).build

    }
  }

  @DELETE
  @Path("/pairs/{pairKey}/scan")
  def cancelScanning(@PathParam("pairKey") pairKey:String) = {
    pairPolicyClient.cancelScans(PairRef(pairKey, space))
    Response.status(Response.Status.OK).build
  }

}