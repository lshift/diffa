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
import net.lshift.diffa.kernel.participants.ParticipantType
import scala.collection.JavaConversions._
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import org.joda.time.{DateTime, Interval}
import net.lshift.diffa.kernel.differencing.{EventOptions, DifferencesManager}
import javax.servlet.http.HttpServletRequest
import net.lshift.diffa.kernel.config.{DomainConfigStore, PairRef}
import org.springframework.security.access.PermissionEvaluator
import net.lshift.diffa.agent.rest.PermissionUtils._
import net.lshift.diffa.agent.auth.{DiffTarget, PairTarget, Privileges}

class DifferencesResource(val differencesManager: DifferencesManager,
                          val domainConfigStore:DomainConfigStore,
                          val space:Long,
                          val uriInfo:UriInfo,
                          val permissionEvaluator:PermissionEvaluator)
    extends IndividuallySecuredResource {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  val parser = DateTimeFormat.forPattern("dd/MMM/yyyy:HH:mm:ss Z");
  val isoDateTime = ISODateTimeFormat.basicDateTimeNoMillis.withZoneUTC()

  @GET
  @Produces(Array("application/json"))
  def getDifferenceEvents(@QueryParam("pairKey") pairKey:String,
                          @QueryParam("range-start") from_param:String,
                          @QueryParam("range-end") until_param:String,
                          @QueryParam("offset") offset_param:String,
                          @QueryParam("length") length_param:String,
                          @QueryParam("include-ignored") includeIgnored:java.lang.Boolean,
                          @Context request: Request) = {

    ensurePrivilege(permissionEvaluator, Privileges.READ_DIFFS, new PairTarget(space, pairKey))

    try {
      val domainVsn = validateETag(request)

      val now = new DateTime()
      val from = defaultDateTime(from_param, now.minusDays(1))
      val until = defaultDateTime(until_param, now)
      val offset = defaultInt(offset_param, 0)
      val length = defaultInt(length_param, 100)
      val reallyIncludeIgnored = if (includeIgnored != null) { includeIgnored.booleanValue() } else { false }

      val interval = new Interval(from,until)
      val diffs = differencesManager.retrievePagedEvents(space, pairKey, interval, offset, length,
        EventOptions(includeIgnored = reallyIncludeIgnored)).map(_.toExternalFormat)

      val responseObj = Map(
        "seqId" -> domainVsn.getValue,
        "diffs" -> diffs.toArray,
        "total" -> differencesManager.countEvents(space, pairKey, interval)
      )
      Response.ok(mapAsJavaMap(responseObj)).tag(domainVsn).build
    }
    catch {
      case e:NoSuchElementException =>
        log.error("Unsucessful query on space = " + space, e)
        throw new WebApplicationException(404)
    }
  }
  
  @GET
  @Path("/aggregates")
  @Produces(Array("application/json"))
  def getAggregates(@Context request: Request, @Context servletRequest:HttpServletRequest): Response = {
    val domainVsn = validateETag(request)

    val requestedAggregates = parseAggregates(servletRequest)
    val aggregates = domainConfigStore.listPairs(space).
      filter(p => {
        hasPrivilege(permissionEvaluator, Privileges.READ_DIFFS, new PairTarget(space, p.key))
      }).map(p => {
        p.key -> mapAsJavaMap(processAggregates(PairRef(space = space, name = p.key), requestedAggregates))
      }).toMap

    Response.ok(mapAsJavaMap(aggregates)).tag(domainVsn).build()
  }

  @GET
  @Path("/aggregates/{pair}")
  @Produces(Array("application/json"))
  def getAggregates(@PathParam("pair") pair:String, @Context request: Request, @Context servletRequest:HttpServletRequest): Response = {
    ensurePrivilege(permissionEvaluator, Privileges.READ_DIFFS, new PairTarget(space, pair))

    val domainVsn = validateETag(request)

    val requestedAggregates = parseAggregates(servletRequest)
    val aggregates = processAggregates(PairRef(space = space, name = pair), requestedAggregates)

    Response.ok(mapAsJavaMap(aggregates)).tag(domainVsn).build()
  }

  @GET
  @Path("/events/{evtSeqId}/{participant}")
  @Produces(Array("text/plain"))
  def getDetail(@PathParam("evtSeqId") evtSeqId:String,
                @PathParam("participant") participant:String) : String = {
    ensurePrivilege(permissionEvaluator, Privileges.READ_EVENT_DETAILS, new DiffTarget(space, evtSeqId))

    differencesManager.retrieveEventDetail(space, evtSeqId, ParticipantType.withName(participant))
  }

  @DELETE
  @Path("/events/{evtSeqId}")
  @Produces(Array("application/json"))
  def ignoreDifference(@PathParam("evtSeqId") evtSeqId:String):Response = {
    ensurePrivilege(permissionEvaluator, Privileges.IGNORE_DIFFS, new DiffTarget(space, evtSeqId))

    val ignored = differencesManager.ignoreDifference(space, evtSeqId).toExternalFormat

    Response.ok(ignored).build
  }

  @PUT
  @Path("/events/{evtSeqId}")
  @Produces(Array("application/json"))
  def unignoreDifference(@PathParam("evtSeqId") evtSeqId:String):Response = {
    ensurePrivilege(permissionEvaluator, Privileges.IGNORE_DIFFS, new DiffTarget(space, evtSeqId))

    val restored = differencesManager.unignoreDifference(space, evtSeqId).toExternalFormat

    Response.ok(restored).build
  }

  def validateETag(request:Request) = {
    val domainConfigVersion = domainConfigStore.getConfigVersion(space)
    val eventSequenceNumber = differencesManager.retrieveDomainSequenceNum(space)

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

  def processAggregates(pairRef:PairRef, requests:Map[String, AggregateRequest]) =
    requests.map { case (name, details) =>
      val tiles = differencesManager.retrieveAggregates(pairRef, details.start, details.end, details.aggregation)

      name -> tiles.map(_.count).toArray
    }.toMap
}
