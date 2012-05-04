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
import core._
import org.slf4j.{Logger, LoggerFactory}
import net.lshift.diffa.docgen.annotations.{OptionalParams, MandatoryParams, Description}
import net.lshift.diffa.docgen.annotations.MandatoryParams.MandatoryParam
import net.lshift.diffa.kernel.participants.ParticipantType
import scala.collection.JavaConversions._
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import org.joda.time.{DateTime, Interval}
import net.lshift.diffa.docgen.annotations.OptionalParams.OptionalParam
import net.lshift.diffa.kernel.differencing.{EventOptions, DifferencesManager}
import javax.servlet.http.HttpServletRequest
import net.lshift.diffa.kernel.config.{DomainConfigStore, DiffaPairRef}

class DifferencesResource(val differencesManager: DifferencesManager,
                          val domainSequenceCache:DomainSequenceCache,
                          val domainConfigStore:DomainConfigStore,
                          val domain:String,
                          val uriInfo:UriInfo) {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  val parser = DateTimeFormat.forPattern("dd/MMM/yyyy:HH:mm:ss Z");
  val isoDateTime = ISODateTimeFormat.basicDateTimeNoMillis.withZoneUTC()

  @GET
  @Produces(Array("application/json"))
  @Description("Returns a list of outstanding differences for the domain in a paged format.")
  @MandatoryParams(Array(
    new MandatoryParam(name = "pairKey", datatype = "string", description = "Pair Key")))
  @OptionalParams(Array(
    new OptionalParam(name = "range-start", datatype = "date", description = "The lower bound of the items to be paged."),
    new OptionalParam(name = "range-end", datatype = "date", description = "The upper bound of the items to be paged."),
    new OptionalParam(name = "offset", datatype = "int", description = "The offset to base the page on."),
    new OptionalParam(name = "length", datatype = "int", description = "The number of items to return in the page."),
    new OptionalParam(name = "include-ignored", datatype = "bool", description = "Whether to include ignored differences (defaults to false).")))
  def getDifferenceEvents(@QueryParam("pairKey") pairKey:String,
                          @QueryParam("range-start") from_param:String,
                          @QueryParam("range-end") until_param:String,
                          @QueryParam("offset") offset_param:String,
                          @QueryParam("length") length_param:String,
                          @QueryParam("include-ignored") includeIgnored:java.lang.Boolean,
                          @Context request: Request) = {

    try {
      val domainVsn = validateETag(request)

      val now = new DateTime()
      val from = defaultDateTime(from_param, now.minusDays(1))
      val until = defaultDateTime(until_param, now)
      val offset = defaultInt(offset_param, 0)
      val length = defaultInt(length_param, 100)
      val reallyIncludeIgnored = if (includeIgnored != null) { includeIgnored.booleanValue() } else { false }

      val interval = new Interval(from,until)
      val diffs = differencesManager.retrievePagedEvents(domain, pairKey, interval, offset, length,
        EventOptions(includeIgnored = reallyIncludeIgnored))

      val responseObj = Map(
        "seqId" -> domainVsn.getValue,
        "diffs" -> diffs.toArray,
        "total" -> differencesManager.countEvents(domain, pairKey, interval)
      )
      Response.ok(mapAsJavaMap(responseObj)).tag(domainVsn).build
    }
    catch {
      case e:NoSuchElementException =>
        log.error("Unsucessful query on domain = " + domain, e)
        throw new WebApplicationException(404)
    }
  }
  
  @GET
  @Path("/aggregates")
  @Produces(Array("application/json"))
  @Description("Returns an aggregate view for all pairs in a domain")
  def getAggregates(@Context request: Request, @Context servletRequest:HttpServletRequest): Response = {
    val domainVsn = validateETag(request)

    val requestedAggregates = parseAggregates(servletRequest)
    val aggregates = domainConfigStore.listPairs(domain).map(p => {
      p.key -> mapAsJavaMap(processAggregates(DiffaPairRef(domain = domain, key = p.key), requestedAggregates))
    }).toMap

    Response.ok(mapAsJavaMap(aggregates)).tag(domainVsn).build()
  }

  @GET
  @Path("/aggregates/{pair}")
  @Produces(Array("application/json"))
  @Description("Returns an aggregate view for a given pair")
  def getAggregates(@PathParam("pair") pair:String, @Context request: Request, @Context servletRequest:HttpServletRequest): Response = {
    val domainVsn = validateETag(request)

    val requestedAggregates = parseAggregates(servletRequest)
    val aggregates = processAggregates(DiffaPairRef(domain = domain, key = pair), requestedAggregates)

    Response.ok(mapAsJavaMap(aggregates)).tag(domainVsn).build()
  }

  @GET
  @Path("/events/{evtSeqId}/{participant}")
  @Produces(Array("text/plain"))
  @Description("Returns the verbatim detail from each participant for the event that corresponds to the sequence id.")
  @MandatoryParams(Array(
    new MandatoryParam(name = "evtSeqId", datatype = "string", description = "Event Sequence ID"),
    new MandatoryParam(name = "participant", datatype = "string", description = "Denotes whether the upstream or downstream participant is intended. Legal values are {upstream,downstream}.")
  ))
  def getDetail(@PathParam("evtSeqId") evtSeqId:String,
                @PathParam("participant") participant:String) : String =
    differencesManager.retrieveEventDetail(domain, evtSeqId, ParticipantType.withName(participant))

  @DELETE
  @Path("/events/{evtSeqId}")
  @Produces(Array("application/json"))
  @Description("Ignores the difference with the given sequence id.")
  @MandatoryParams(Array(
    new MandatoryParam(name = "evtSeqId", datatype = "string", description = "Event Sequence ID")
  ))
  def ignoreDifference(@PathParam("evtSeqId") evtSeqId:String):Response = {
    val ignored = differencesManager.ignoreDifference(domain, evtSeqId)

    Response.ok(ignored).build
  }

  @PUT
  @Path("/events/{evtSeqId}")
  @Produces(Array("application/json"))
  @Description("Unignores a difference with the given sequence id.")
  @MandatoryParams(Array(
    new MandatoryParam(name = "evtSeqId", datatype = "string", description = "Event Sequence ID")
  ))
  def unignoreDifference(@PathParam("evtSeqId") evtSeqId:String):Response = {
    val restored = differencesManager.unignoreDifference(domain, evtSeqId)

    Response.ok(restored).build
  }

  def validateETag(request:Request) = {
    val domainConfigVersion = domainConfigStore.getConfigVersion(domain)
    val eventSequenceNumber = getSequenceNumber(domain)

    val domainVsn = new EntityTag(eventSequenceNumber + "@" + domainConfigVersion)

    request.evaluatePreconditions(domainVsn) match {
      case null => // We'll continue with the request
      case r => throw new WebApplicationException(r.build)
    }

    domainVsn
  }

  def defaultDateTime(input:String, default:DateTime) = input match {
    case "" | null => default
    case x         => isoDateTime.parseDateTime(x)
  }

  def defaultInt(input:String, default:Int) = input match {
    case "" | null => default
    case x         => x.toInt
  }

  private def getSequenceNumber(domain:String) = domainSequenceCache.readThrough( domain, () => differencesManager.retrieveDomainSequenceNum(domain) )

  val aggregateParamPrefix = "agg-"

  def parseAggregates(request:HttpServletRequest):Map[String, AggregateRequest] = {
    request.getParameterMap.flatMap { case (key:String, values:Array[String]) =>
      if (key.startsWith(aggregateParamPrefix)) {
        Some(key.substring(aggregateParamPrefix.length) -> AggregateRequest.parse(values(0)))
      } else {
        None
      }
    }.toMap
  }

  def processAggregates(pairRef:DiffaPairRef, requests:Map[String, AggregateRequest]) =
    requests.map { case (name, details) =>
      val tiles = differencesManager.retrieveAggregates(pairRef, details.start, details.end, details.aggregation)

      name -> tiles.map(_.count).toArray
    }.toMap
}
