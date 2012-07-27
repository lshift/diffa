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
import core.{Response, UriInfo}
import net.lshift.diffa.kernel.reporting.ReportManager
import net.lshift.diffa.kernel.config.{DomainConfigStore, DiffaPairRef}
import net.lshift.diffa.kernel.frontend.PairReportDef
import scala.collection.JavaConversions._

class ReportsResource(val config:DomainConfigStore,
                      val reports:ReportManager,
                      val domain:String,
                      val uriInfo:UriInfo) {

  @GET
  @Path("/{pairId}")
  @Produces(Array("application/json"))
  def listReports(@PathParam("pairId") pairId: String,
                  @QueryParam("scope") scope: String): Array[PairReportDef] =
      config.getPairDef(domain, pairId).reports.toSeq.toArray[PairReportDef]

  @POST
  @Path("/{pairId}/{reportId}")
  @Produces(Array("application/json"))
  def executeReport(@PathParam("pairId") pairId:String,
                   @PathParam("reportId") reportId:String) = {
    reports.executeReport(DiffaPairRef(key = pairId, domain = domain), reportId)
    Response.status(Response.Status.OK).build
  }

}