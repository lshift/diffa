package net.lshift.diffa.agent.client

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

import org.joda.time.DateTime
import com.sun.jersey.core.util.MultivaluedMapImpl
import com.sun.jersey.api.client.{WebResource, ClientResponse}
import javax.ws.rs.core.{Response, MediaType}
import scala.collection.JavaConversions._
import org.joda.time.format.ISODateTimeFormat
import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.node.ObjectNode
import java.util.ArrayList
import net.lshift.diffa.client.{RestClientParams, NotFoundException}
import net.lshift.diffa.agent.rest.AggregateRequest
import net.lshift.diffa.kernel.differencing._

/**
 * A RESTful client to poll for difference events on a domain.
 */
class DifferencesRestClient(serverRootUrl:String, domain:String, params: RestClientParams = RestClientParams.default)
    extends DomainAwareRestClient(serverRootUrl, domain, "domains/{domain}/diffs", params) {

  val supportsStreaming = false
  val supportsPolling = true

  val noMillisFormatter = ISODateTimeFormat.basicDateTimeNoMillis
  val formatter = ISODateTimeFormat.dateTime()

  def retrieveAggregates(requests:Map[String, AggregateRequest]) : Map[String, Map[String,List[Int]]]  = {
    val path = resource.path("aggregates")
                       .queryParams(aggregateRequestsToParams(requests))
    val media = path.accept(MediaType.APPLICATION_JSON_TYPE)
    val response = media.get(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 200    =>
        val result = response.getEntity(classOf[java.util.Map[String, java.util.Map[String, ArrayList[Int]]]])
        result.map {
          case (k,v) => k -> v.map {
            case (k1, v1) => k1 -> v1.toList
          }.toMap[String, List[Int]]
        }.toMap[String,Map[String, List[Int]]]
      case x:Int  => handleHTTPError(x, path, status)
    }
  }

  def retrieveAggregates(pair:String, requests:Map[String, AggregateRequest]) : Map[String,List[Int]]  = {
    val path = resource.path("aggregates/" + pair)
                       .queryParams(aggregateRequestsToParams(requests))
    val media = path.accept(MediaType.APPLICATION_JSON_TYPE)
    val response = media.get(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 200    =>
        val result = response.getEntity(classOf[java.util.Map[String,ArrayList[Int]]])
        result.map { case (k,v) => k -> v.toList }.toMap[String,List[Int]]
      case 400    =>
        throw new InvalidAggregateRequestException(response.getEntity(classOf[String]))
      case x:Int  => handleHTTPError(x, path, status)
    }

  }

  def getEvents(pairKey:String, from:DateTime, until:DateTime, offset:Int, length:Int) = {
    val path = resource.queryParam("pairKey", pairKey)
                       .queryParam("range-start", noMillisFormatter.print(from))
                       .queryParam("range-end", noMillisFormatter.print(until))
                       .queryParam("offset", offset.toString)
                       .queryParam("length", length.toString)
    val media = path.accept(MediaType.APPLICATION_JSON_TYPE)
    val response = media.get(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 200 => {
        val responseMap = response.getEntity(classOf[ObjectNode])
        val diffs = responseMap.get("diffs")
        val objMapper = new ObjectMapper()

        objMapper.readValue(diffs, classOf[Array[ExternalDifferenceEvent]])
      }
      case x:Int   => throw new RuntimeException("HTTP " + x + " : " + status.getReasonPhrase)
    }
  }

  /*
  def eventDetail(evtSeqId:String, t:ParticipantType.ParticipantType) : String = {
    val path = resource.path("events/" + evtSeqId + "/" + t.toString )
    val media = path.accept(MediaType.TEXT_PLAIN_TYPE)
    val response = media.get(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 200    => response.getEntity(classOf[String])
      case 400    => throw new InvalidSequenceNumberException(evtSeqId)
      case 404    => throw new NotFoundException(domain)
      case x:Int  => handleHTTPError(x, path, status)
    }
  }
  */

  def ignore(seqId: String) = {
    val response = delete("events/" + seqId)

    response.getEntity(classOf[ExternalDifferenceEvent])
  }

  def unignore(seqId: String) = {
    val path = resource.path("events/" + seqId)
    val media = path.accept(MediaType.APPLICATION_JSON_TYPE)
    val response = media.put(classOf[ClientResponse])
    val status = response.getClientResponseStatus
    status.getStatusCode match {
      case 200    => response.getEntity(classOf[ExternalDifferenceEvent])
      case 400    => throw new InvalidSequenceNumberException(seqId)
      case 404    => throw new NotFoundException(domain)
      case x:Int  => handleHTTPError(x, path, status)
    }
  }

  def f (d:DateTime) = {
    d match {
      case null => ""
      case _    => d.toString()
    }    
  }

  private def aggregateRequestsToParams(requests:Map[String, AggregateRequest]) = {
    val params = new MultivaluedMapImpl()

    requests.foreach { case (name, req) => params.putSingle("agg-" + name, req.toRequestString) }

    params
  }
}