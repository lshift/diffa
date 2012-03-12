/**
 * Copyright (C) 2010-2012 LShift Ltd.
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

import javax.ws.rs.core.MultivaluedMap
import net.lshift.diffa.participant.scanning.{StringPrefixConstraint, RangeConstraint, SetConstraint, ScanConstraint}
import net.lshift.diffa.kernel.participants.{StringPrefixCategoryFunction, CategoryFunction}
import scala.collection.JavaConversions._

/**
 * Helper for building requests. Provides support for serialising various data types into request semantics.
 */
object RequestBuildingHelper {
  def constraintsToQueryArguments(params:MultivaluedMap[String,String], constraints:Seq[ScanConstraint]) {
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
  }

  def aggregationsToQueryArguments(params:MultivaluedMap[String,String], aggregations:Seq[CategoryFunction]) {
    aggregations.foreach {
      case spf:StringPrefixCategoryFunction =>
        params.add(spf.getAttributeName + "-length", spf.prefixLength.toString)
      case f =>
        params.add(f.getAttributeName + "-granularity", f.name)
    }
  }
}