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

package net.lshift.diffa.kernel.notifications

import org.joda.time.DateTime
import net.lshift.diffa.kernel.events.VersionID
import scala.beans.BeanProperty

case class NotificationEvent (
  @BeanProperty var id:VersionID,
  @BeanProperty var lastUpdated:DateTime,
  @BeanProperty var upstreamVsn:String,
  @BeanProperty var downstreamVsn:String
  )
{

  def getPairKey = id.pair.name
  // TODO What about the domain ?
  def getDomain = id.pair.space
  def getEntityId = id.id
  def getTimestamp = lastUpdated.toString()
  def getUpstream = upstreamVsn
  def getDownstream = downstreamVsn
}