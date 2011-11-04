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

class DifferencesResource(val differencesManager: DifferencesManager,
                          val domainSequenceCache:DomainSequenceCache,
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
      val domainVsn = new EntityTag(getSequenceNumber(domain))

      request.evaluatePreconditions(domainVsn) match {
        case null => // We'll continue with the request
        case r => throw new WebApplicationException(r.build)
      }

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
  @Path("/tiles/{zoomLevel}")
  @Produces(Array("application/json"))
  @MandatoryParams(Array(
      new MandatoryParam(name = "range-start", datatype = "date", description = "The starting time for any differences"),
      new MandatoryParam(name = "range-end", datatype = "date", description = "The ending time for any differences"),
      new MandatoryParam(name = "zoom-level", datatype = "int", description = "The level of zoom for the requested tiles")))
  @Description("Returns a zoomed view of the data within a specific time range")
  def getZoomedTiles(@QueryParam("range-start") rangeStart: String,
                     @QueryParam("range-end") rangeEnd:String,
                     @PathParam("zoomLevel") zoomLevel:Int,
                     @Context request: Request): Response = {

    // Evaluate whether the version of the domain has changed
    val domainVsn = new EntityTag(getSequenceNumber(domain) + "@" + zoomLevel)
    request.evaluatePreconditions(domainVsn) match {
      case null => // We'll continue with the request
      case r => throw new WebApplicationException(r.build)
    }

    val rangeStartDate = isoDateTime.parseDateTime(rangeStart)
    val rangeEndDate = isoDateTime.parseDateTime(rangeEnd)

    if (rangeStartDate == null || rangeEndDate == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Invalid start or end date").build
    }

    // TODO Consider limiting the zoom range to prevent people requesting ranges going back to the start of time in one go
    // This should probably be relative to the zoom level and is effectively geared towards delivering enough
    // data to populate the heatmap

    val tileSet = differencesManager.retrieveEventTiles(domain, zoomLevel, new Interval(rangeStartDate, rangeEndDate))
    val respObj = mapAsJavaMap(tileSet.keys.map(pair => pair -> tileSet(pair).toArray).toMap[String, Array[Int]])
    Response.ok(respObj).tag(domainVsn).build()
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

  def defaultDateTime(input:String, default:DateTime) = input match {
    case "" | null => default
    case x         => isoDateTime.parseDateTime(x)
  }

  def defaultInt(input:String, default:Int) = input match {
    case "" | null => default
    case x         => x.toInt
  }

  private def getSequenceNumber(domain:String) = domainSequenceCache.readThrough( domain, () => differencesManager.retrieveDomainSequenceNum(domain) )
}
