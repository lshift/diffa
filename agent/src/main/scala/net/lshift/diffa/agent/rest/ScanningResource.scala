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
import net.lshift.diffa.docgen.annotations.{MandatoryParams, Description}
import net.lshift.diffa.docgen.annotations.MandatoryParams.MandatoryParam
import javax.ws.rs.core.Response
import net.lshift.diffa.kernel.frontend.Configuration
import javax.ws.rs._
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.config.{DiffaPairRef, DomainConfigStore}
import org.slf4j.{LoggerFactory, Logger}
import net.lshift.diffa.docgen.annotations.OptionalParams.OptionalParam

class ScanningResource(val pairPolicyClient:PairPolicyClient,
                       val config:Configuration,
                       val domainConfigStore:DomainConfigStore,
                       val diagnostics:DiagnosticsManager,
                       val domain:String) {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  @GET
  @Path("/states")
  @Description("Lists the scanning state for every configured pair within this domain.")
  def getAllPairStates = {
    val states = diagnostics.retrievePairScanStatesForDomain(domain)
    Response.ok(scala.collection.JavaConversions.mapAsJavaMap(states)).build
  }

  @POST
  @Path("/pairs/{pairKey}/scan")
  @Description("Starts a scan for the given pair.")
  @MandatoryParams(Array(new MandatoryParam(name="pairKey", datatype="string", description="Pair Key")))
  @OptionalParam(name = "view", datatype="string", description="Child View to Scan")
  def startScan(@PathParam("pairKey") pairKey:String, @FormParam("view") view:String) = {
    pairPolicyClient.scanPair(DiffaPairRef(pairKey, domain), if (view != null) Some(view) else None)
    Response.status(Response.Status.ACCEPTED).build
  }

  @POST
  @Path("/scan_all")
  @Description("Forces Diffa to execute a scan operation for every configured pair within this domain.")
  def scanAllPairings = {
    log.info("Initiating scan of all known pairs")
    domainConfigStore.listPairs(domain).foreach(p => pairPolicyClient.scanPair(DiffaPairRef(p.key, domain), None))

    Response.status(Response.Status.ACCEPTED).build
  }

  @DELETE
  @Path("/pairs/{pairKey}/scan")
  @Description("Cancels any current and/or pending scans for the given pair.")
  @MandatoryParams(Array(new MandatoryParam(name="pairKey", datatype="string", description="Pair Key")))
  def cancelScanning(@PathParam("pairKey") pairKey:String) = {
    pairPolicyClient.cancelScans(DiffaPairRef(pairKey, domain))
    Response.status(Response.Status.OK).build
  }

}