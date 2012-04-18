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
package net.lshift.diffa.agent.rest

import net.lshift.diffa.participant.scanning.ScanRequest
import com.sun.jersey.core.util.MultivaluedMapImpl
import net.lshift.diffa.client.RequestBuildingHelper
import java.net.URLEncoder
import scala.collection.JavaConversions._

/**
 * Utility for transforming ScanRequest objects into an appropriate wire format.
 */
object ScanRequestWriter {
  def writeScanRequest(req:ScanRequest):String = {
    val params = new MultivaluedMapImpl()
    RequestBuildingHelper.constraintsToQueryArguments(params, req.getConstraints.toSeq)
    RequestBuildingHelper.aggregationsToQueryArguments(params, req.getAggregations.toSeq)

    val prefix = "scan" + (if (params.size() > 0) "?" else "")

    prefix +
      params.keys.flatMap(k => {
        params.get(k).map(v => URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8"))
      }).toSeq.sorted.mkString("&")
  }

  def writeScanRequests(requests:Seq[ScanRequest]):String =
    requests.map(writeScanRequest(_)).mkString("\n")
}