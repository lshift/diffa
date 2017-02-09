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

/**
 * This is metadata associated with a category that gives it strong type information.
 */
abstract class TypeDescriptor {

  /**
   * The name of the data type.
   */
  def name : String
}

/**
 * No type information.
 */
case object AnyTypeDescriptor extends TypeDescriptor {
  def name = "any"
}

/**
 * This defines a date with a granularity of one day (i.e. yyyy-MM-dd).
 */
case object DateTypeDescriptor extends TypeDescriptor {
  def name = "date"
}

/**
 * This defines a date with a granularity of one day (i.e. yyyy-MM-ddThh:mm:ss.mmmmZ).
 */
case object DateTimeTypeDescriptor extends TypeDescriptor {
  def name = "datetime"
}

/**
 * This defines a generic string type.
 */
case object StringTypeDescriptor extends TypeDescriptor {
  def name = "string"
}

/**
 * This defines a generic integer type.
 */
case object IntegerTypeDescriptor extends TypeDescriptor {
  def name = "int"
}