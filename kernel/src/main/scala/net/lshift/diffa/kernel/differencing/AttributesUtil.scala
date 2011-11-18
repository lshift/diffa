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

package net.lshift.diffa.kernel.differencing

import org.joda.time.format.ISODateTimeFormat
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.participant.scanning._
import scala.Option._
import scala.collection.JavaConversions._

/**
 * Utility for working with attribute maps.
 */
object AttributesUtil {

  def toSeq(attrs:Map[String, String]):Seq[String] = {
    attrs.toSeq.sortBy { case (name, value) => name }.map { case (name, value) => value }
  }

  def toSeqFromTyped(attrs:Map[String, TypedAttribute]):Seq[String] = {
    attrs.toSeq.sortBy { case (name, value) => name }.map { case (name, value) => value.value }
  }

  def toMap(keys:Iterable[String], attrs:Iterable[String]):Map[String, String] = toMap(keys.toSeq, attrs.toSeq)
  def toMap(keys:Seq[String], attrs:Seq[String]):Map[String, String] = (keys.sorted, attrs).zip.toMap

  def toTypedMap(categories:Map[String, CategoryDescriptor], attrs:Map[String, String]):Map[String, TypedAttribute] = {
    categories.keys.map { name =>
      attrs.get(name) match {
        case None    => None
        case Some(a) => Some(name -> asTyped(name, a, categories))
      }
    }.flatten.toMap
  }

  def toUntypedMap(attrs:Map[String, TypedAttribute]) = {
    attrs.map { case (k, v) => k -> v.value }.toMap
  }

  def asTyped(name:String, value:String, categories:Map[String, CategoryDescriptor]) = {
    categories(name) match {
      case s:SetCategoryDescriptor => StringAttribute(value)
      case r:RangeCategoryDescriptor => RangeCategoryParser.typedAttribute(r, value)
      case p:PrefixCategoryDescriptor => StringAttribute(value)
    }
  }

  /**
   * Examines the provided attributes, and ensures that attributes exist for each of the
   * configured categories.
   */
  def detectMissingAttributes(categories:Map[String, CategoryDescriptor], attrs:Map[String, String]):Map[String, String] = {
    categories.flatMap {
      case (name, categoryType) => {
        attrs.get(name) match {
          case None => Some(name -> "property is missing")
          case Some(v) => None
        }
      }
    }.toMap
  }

  def detectOutsideConstraints(constraints:Seq[ScanConstraint], attrs:Map[String, TypedAttribute]):Map[String, String] = {
    val results:Seq[(String, String)] = constraints.flatMap(constraint =>
      attrs.get(constraint.getAttributeName) match {
        case None => None   // Missing attributes should be detected with `detectMissingAttributes`
        case Some(v) =>
          constraint match {
            case s:SetConstraint   =>
              if (s.contains(v.value)) {
                None
              } else {
                Some(constraint.getAttributeName -> (v.value + " is not a member of " + s.getValues.toSet))
              }
            case r:RangeConstraint =>
              val valid = r match {
                case i:IntegerRangeConstraint => i.contains(v.asInstanceOf[IntegerAttribute].int)
                case t:TimeRangeConstraint => t.contains(v.asInstanceOf[DateTimeAttribute].date)
                case d:DateRangeConstraint => d.contains(v.asInstanceOf[DateAttribute].date)
              }
              if (valid) {
                None
              } else {
                Some(constraint.getAttributeName -> "%s is not in range %s -> %s".format(v.value, r.getStartText, r.getEndText))
              }
            case p:StringPrefixConstraint =>
              if (p.contains(v.value)) {
                None
              } else {
                Some(constraint.getAttributeName -> (v.value + " does not have the prefix " + p.getPrefix))
              }
          }
      }
    )
    results.toMap[String, String]
  }
}