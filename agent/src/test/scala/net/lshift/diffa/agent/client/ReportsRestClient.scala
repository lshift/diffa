package net.lshift.diffa.agent.client

/**
 * Copyright (C) 2011 LShift Ltd.
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

import net.lshift.diffa.kernel.config.DiffaPairRef
import net.lshift.diffa.kernel.frontend.PairReportDef
import com.sun.jersey.api.client.ClientResponse
import net.lshift.diffa.client.{RestClientParams, BadRequestException}

class ReportsRestClient(serverRootUrl:String, domain:String, params: RestClientParams = RestClientParams.default)
  extends DomainAwareRestClient(serverRootUrl, domain, "rest/domains/{domain}/actions/", params) {

  def listReports(pair:DiffaPairRef): Seq[PairReportDef] = {
    val t = classOf[Array[PairReportDef]]
    rpc(pair.key, t)
  }
  
  def executeReport(pair:DiffaPairRef,  name:String) {
    val p = resource.path(name)
    val response = p.post(classOf[ClientResponse])

    def logError(status:Int) = {
      log.error("HTTP %s: Could not execute report %s at %s".format(status, name, p.getURI))
    }

    response.getStatus match {
      case 200 => ()
      case 400 => {
        logError(response.getStatus)
        throw new BadRequestException(response.getStatus + "")
      }
      case _   => {
        logError(response.getStatus)
        throw new RuntimeException(response.getStatus + "")
      }
    }
  }

}
