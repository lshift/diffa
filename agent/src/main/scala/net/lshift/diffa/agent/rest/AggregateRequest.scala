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

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

/**
 * Describes an individual request for an aggregate.
 */
case class AggregateRequest(start:DateTime, end:DateTime, aggregation:Option[Int]) {
  import AggregateRequest.isoParser

  def toRequestString = {
    (start match {
      case null => ""
      case s    => isoParser.print(s)
    }) +
    "-" +
    (end match {
      case null => ""
      case e    => isoParser.print(e)
    }) +
    (aggregation match {
      case None    => ""
      case Some(a) => "@" + a
    })
  }
}
object AggregateRequest {
  val requestFormat = "(\\d{8}T\\d{6}Z)?-(\\d{8}T\\d{6}Z)?(@([\\d]+))?".r
  val isoParser = ISODateTimeFormat.basicDateTimeNoMillis.withZoneUTC()

  def apply(start:String, end:String, aggregation:Option[Int] = None):AggregateRequest = {
    def maybeDate(d:String):DateTime = if (d != null) isoParser.parseDateTime(d) else null

    AggregateRequest(maybeDate(start), maybeDate(end), aggregation)
  }

  def parse(s:String):AggregateRequest = {
    s match {
      case requestFormat(start, end, _, null) =>
        AggregateRequest(start, end)
      case requestFormat(start, end, _, aggregation) =>
        AggregateRequest(start, end, Some(Integer.parseInt(aggregation)))
    }
  }
}