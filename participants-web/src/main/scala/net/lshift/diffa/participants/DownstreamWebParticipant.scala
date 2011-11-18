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

package net.lshift.diffa.participants

import net.lshift.diffa.kernel.participants.{DownstreamMemoryParticipant}
import org.joda.time.DateTime
import org.apache.commons.codec.digest.DigestUtils
import net.lshift.diffa.participant.changes.ChangeEvent
import scala.collection.JavaConversions._

/**
 * An implementation of the DownstreamParticipant using the MemoryParticipant base, whereby the body is the version
 * of an entity.
 */
class DownstreamWebParticipant(val epName:String, val agentRoot:String, val domain:String)
    extends DownstreamMemoryParticipant(DigestUtils.md5Hex, DigestUtils.md5Hex)
    with WebParticipant {

  override def addEntity(id: String, someDate:DateTime, someString:String, lastUpdated: DateTime, body: String) = {
    super.addEntity(id, someDate, someString, lastUpdated, body)

    changesClient.onChangeEvent(ChangeEvent.forChange(id, dvsnGen(body), lastUpdated, Map("someDate" -> someDate.toString(), "someString" -> someString)))
  }


  override def removeEntity(id: String) = {
    super.removeEntity(id)

    changesClient.onChangeEvent(ChangeEvent.forChange(id, null, new DateTime))
  }
}