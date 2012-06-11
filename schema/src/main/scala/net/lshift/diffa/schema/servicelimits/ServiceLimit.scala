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
package net.lshift.diffa.schema.servicelimits

/**
 * Describes a pre-defined limit. The actual run time values will depend on what is configured
 * in the ServiceLimitsStore for a given key
 */
trait ServiceLimit {
  def key:String
  def description:String
  def defaultLimit:java.lang.Integer
  def hardLimit:java.lang.Integer

  private final val SEC_PER_MIN = 60
  private final val MS_PER_S = 1000

  /// Return type is java.lang.Integer since InsertBuilder.values requires a map of AnyRef
  protected def minutesToMs(minutes: Int): java.lang.Integer = minutes * SEC_PER_MIN * MS_PER_S
  protected def secondsToMs(seconds: Int): java.lang.Integer = seconds * MS_PER_S
}
