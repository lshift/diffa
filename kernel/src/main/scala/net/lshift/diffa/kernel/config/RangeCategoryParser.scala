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

package net.lshift.diffa.kernel.config

import org.joda.time.format.{DateTimeFormatterBuilder, ISODateTimeFormat}
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.differencing.{DateAttribute, StringAttribute, DateTimeAttribute, IntegerAttribute}
import net.lshift.diffa.participant.scanning.{IntegerRangeConstraint, TimeRangeConstraint, DateRangeConstraint}
import org.joda.time.DateTimeZone

/**
 * This is a simple registry that can hydrate category values based on a descriptor
 */
object RangeCategoryParser {

  val log = LoggerFactory.getLogger(getClass)

  val dateParsers = Array(ISODateTimeFormat.date().getParser)
  protected val dateFormatter = new DateTimeFormatterBuilder().append( null, dateParsers ).toFormatter.withZone(DateTimeZone.UTC)

  def parseDateTime(s:String) = ISODateTimeFormat.dateTimeParser.withZone(DateTimeZone.UTC).parseDateTime(s)
  def parseDate(s:String) = parseDateTime(s).toLocalDate
  def parseInt(s:String) = Integer.valueOf(s).intValue

  def typedAttribute(descriptor:RangeCategoryDescriptor, value:String) = descriptor.dataType match {
    case "int"      => IntegerAttribute(parseInt(value))
    case "date"     => DateAttribute(parseDate(value))
    case "datetime" => DateTimeAttribute(parseDateTime(value))
    case t          =>
      log.warn("Casting value %s (which has the configured type %s) to a string".format(value,t))
      StringAttribute(value)
  }
}