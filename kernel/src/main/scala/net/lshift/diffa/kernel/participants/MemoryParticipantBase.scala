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

package net.lshift.diffa.kernel.participants

import org.joda.time.DateTime
import collection.mutable.HashMap
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.differencing.AttributesUtil
import javax.servlet.http.HttpServletRequest
import scala.collection.JavaConversions._
import java.util.ArrayList
import net.lshift.diffa.adapter.scanning._
import net.lshift.diffa.adapter.content.ContentParticipantHandler
import net.lshift.diffa.adapter.correlation.VersioningParticipantHandler

/**
 * Base class for test participants.
 */
class MemoryParticipantBase(nativeVsnGen: String => String)
    extends ScanningParticipantRequestHandler
    with ContentParticipantHandler {

  val log = LoggerFactory.getLogger(getClass)

  protected val entities = new HashMap[String, TestEntity]

  def entityIds: Seq[String] = entities.keys.toList

  /**
   * Scans this adapter with the given constraints and aggregations.
   */
  def scan(constraints:Seq[ScanConstraint], aggregations:Seq[CategoryFunction]): Seq[ScanResultEntry] =
    doQuery(constraints, aggregations).toSeq

  def retrieveContent(identifier: String) = entities.get(identifier) match {
    case Some(entity) => entity.body
    case None => null
  }

  def addEntity(id: String, datetime:DateTime, someString:String, lastUpdated:DateTime, body: String): Unit = {
    entities += ((id, TestEntity(id, datetime, someString, lastUpdated, body)))
  }

  def removeEntity(id:String) {
    entities.remove(id)
  }

  def clearEntities {
    entities.clear
  }

  def close() = entities.clear

  protected override def determineAggregations(req: HttpServletRequest) = {
    val builder = new AggregationBuilder(req)
    builder.maybeAddDateAggregation("someDate")
    builder.maybeAddByNameAggregation("someString")
    builder.toList
  }

  protected override def determineConstraints(req: HttpServletRequest) = {
    val builder = new ConstraintsBuilder(req)
    builder.maybeAddTimeRangeConstraint("someDate")
    builder.maybeAddSetConstraint("someString")
    builder.toList
  }

  protected def doQuery(constraints: java.util.List[ScanConstraint], aggregations: java.util.List[ScanAggregation]):java.util.List[ScanResultEntry] = {
    val entitiesInRange = entities.values.filter(e => {
      constraints.forall {
        case drc:net.lshift.diffa.adapter.scanning.TimeRangeConstraint =>
          (drc.getStart == null || !e.someDate.isBefore(drc.getStart)) &&
              (drc.getEnd == null || !e.someDate.isAfter(drc.getEnd))
        case sc:net.lshift.diffa.adapter.scanning.SetConstraint =>
          sc.getValues.contains(e.someString)
        case _ =>
          false   // We can't satisfy other constraints
      }
    }).toList
    val asScanResults = entitiesInRange.sortBy(_.id).map { e => new ScanResultEntry(e.id, nativeVsnGen(e.body), e.lastUpdated, e.toAttributes) }

    if (aggregations.length > 0) {
      val digester = new DigestBuilder(aggregations)
      asScanResults.foreach(digester.add(_))
      digester.toDigests
    } else {
      asScanResults
    }
  }
}

case class TestEntity(id: String, someDate:DateTime, someString:String, lastUpdated:DateTime, body: String) {
  def toAttributes:Map[String, String] = Map("someDate" -> someDate.toString(), "someString" -> someString)
}