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
import net.lshift.diffa.participant.common.JSONHelper
import org.apache.commons.io.IOUtils
import scala.collection.JavaConversions._
import net.lshift.diffa.participant.scanning._
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.util.AlertCodes._
import java.net.ConnectException
import net.lshift.diffa.kernel.differencing.ScanFailedException
import com.sun.jersey.api.client.{ClientHandlerException, ClientResponse}

/**
 * JSON/REST scanning participant client.
 */

class ScanningParticipantRestClient(scanUrl:String, params: RestClientParams = RestClientParams.default)
    extends AbstractRestClient(scanUrl, "", params)
    with ScanningParticipantRef {

  val logger = LoggerFactory.getLogger(getClass)

  def scan(constraints: Seq[ScanConstraint], aggregations: Seq[CategoryFunction]) = {

    val params = new MultivaluedMapImpl()
    RequestBuildingHelper.constraintsToQueryArguments(params, constraints)
    RequestBuildingHelper.aggregationsToQueryArguments(params, aggregations)

    val query = resource.queryParams(params)
    logger.debug("%s Querying participant: %s".format(SCAN_QUERY_EVENT, query))

    val jsonEndpoint = query.`type`(MediaType.APPLICATION_JSON_TYPE)


    val response = try {

      jsonEndpoint.get(classOf[ClientResponse])

    } catch {
      case e:Exception =>

        // If Jersey encounters a ConnectException, it wraps it in a ClientHandlerException, which
        // doesn't seem to add much value. So if we can detected this scenario, we'll apply the
        // appropriate logging and signalling, otherwise it will just be up to higher level code
        // to sort this out.

        if (e.getCause.isInstanceOf[ConnectException]) {
          logger.error("%s Connection to %s refused".format(SCAN_CONNECTION_REFUSED, scanUrl))
          throw new ScanFailedException("Could not connect to " + scanUrl)
        }
        else {
          throw e
        }

    }

    response.getStatus match {
      case 200 => JSONHelper.readQueryResult(response.getEntityInputStream)
      case _   =>
        logger.error("%s External scan error, response code: %s".format(EXTERNAL_SCAN_ERROR, response.getStatus))
        throw new Exception("Participant scan failed: " + response.getStatus + "\n" + IOUtils.toString(response.getEntityInputStream, "UTF-8"))
    }
  }
}