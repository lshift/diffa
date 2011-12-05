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

package net.lshift.diffa.client

import com.sun.jersey.core.util.MultivaluedMapImpl
import net.lshift.diffa.kernel.participants._
import javax.ws.rs.core.MediaType
import com.sun.jersey.api.client.ClientResponse
import net.lshift.diffa.participant.common.JSONHelper
import org.apache.commons.io.IOUtils
import scala.collection.JavaConversions._
import net.lshift.diffa.participant.scanning._

/**
 * JSON/REST scanning participant client.
 */

class ScanningParticipantRestClient(scanUrl:String, params: RestClientParams = RestClientParams.default)
    extends AbstractRestClient(scanUrl, "", params)
    with ScanningParticipantRef {

  def scan(constraints: Seq[ScanConstraint], aggregations: Seq[CategoryFunction]) = {
  	log.debug("Querying constraints %s, aggregated by %s".format(constraints, aggregations))
    val params = new MultivaluedMapImpl()
    constraints.foreach {
      case sqc:SetConstraint   =>
        sqc.getValues.foreach(v => params.add(sqc.getAttributeName, v))
      case rc:RangeConstraint =>
        if (rc.hasLowerBound) {
          params.add(rc.getAttributeName + "-start", rc.getStartText)  
        }
        if (rc.hasUpperBound) {
          params.add(rc.getAttributeName + "-end", rc.getEndText)
        }
      case pc:StringPrefixConstraint =>
        params.add(pc.getAttributeName + "-prefix", pc.getPrefix)
    }
    aggregations.foreach {
      case spf:StringPrefixCategoryFunction =>
        params.add(spf.getAttributeName + "-length", spf.prefixLength.toString)
      case f =>
        params.add(f.getAttributeName + "-granularity", f.name)
    }

    val jsonEndpoint = resource.queryParams(params).`type`(MediaType.APPLICATION_JSON_TYPE)
    val response = jsonEndpoint.get(classOf[ClientResponse])
    response.getStatus match {
      case 200 => JSONHelper.readQueryResult(response.getEntityInputStream)
      case _   =>
        log.error(response.getStatus + "")
        throw new Exception("Participant scan failed: " + response.getStatus + "\n" + IOUtils.toString(response.getEntityInputStream, "UTF-8"))
    }
  }
}