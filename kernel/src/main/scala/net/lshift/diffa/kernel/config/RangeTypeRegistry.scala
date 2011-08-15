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

import net.lshift.diffa.kernel.participants._

/**
 * Simple registry to be allow to dispatching on RangeCategoryDescriptors by their data type.
 *
 * ATM this is not as strongly typed as it could be because the type is persisted as a string, but at least this
 * lookup is centralized.
 */
object RangeTypeRegistry {

  /**
   * Resolve the default category function for any given data type name
   */
  def defaultCategoryFunction(attrName:String, desc:RangeCategoryDescriptor) : Option[CategoryFunction] = desc.dataType match {
    case "date"     => defaultOrMaxGranularity(attrName, desc, DateDataType)
    case "datetime" => defaultOrMaxGranularity(attrName, desc, TimeDataType)
    case "int"      => Some(IntegerCategoryFunction(attrName, 1000, 10))
  }

  def defaultOrMaxGranularity(attrName:String, desc:RangeCategoryDescriptor, t:DateCategoryDataType) = desc.maxGranularity match {
    case "yearly" | "" | null => Some(YearlyCategoryFunction(attrName, t))
    case "monthly"            => Some(MonthlyCategoryFunction(attrName, t))
    case "daily"              => Some(DailyCategoryFunction(attrName, t))
    case "individual"         => None
  }
}