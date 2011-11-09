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

package net.lshift.diffa.kernel.events

import org.joda.time.DateTime
import net.lshift.diffa.kernel.differencing.TypedAttribute

/**
 * Base inherited by the various types of events
 */
abstract class PairChangeEvent {
  def id:VersionID
  def attributes:Map[String, TypedAttribute]
  def lastUpdate:DateTime
}

/**
 * Event indicating that a change has occurred within an upstream system.
 */
case class UpstreamPairChangeEvent(id:VersionID, attributes:Map[String, TypedAttribute], lastUpdate:DateTime, vsn:String)
  extends PairChangeEvent

/**
 * Event indicating that a change has occurred within a downstream system.
 */
case class DownstreamPairChangeEvent(id:VersionID, attributes:Map[String, TypedAttribute], lastUpdate:DateTime, vsn:String)
  extends PairChangeEvent

/**
 * Event indicating that a correlatable change has occurred within a downstream system. A correlatable downstream
 * change indicates that the change occurring in the downstream did not result in the same content being at the
 * downstream, but provides details on correlating the version information between the systems.
 */
case class DownstreamCorrelatedPairChangeEvent(id:VersionID, attributes:Map[String, TypedAttribute], lastUpdate:DateTime, upstreamVsn:String, downstreamVsn:String)
  extends PairChangeEvent