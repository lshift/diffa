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

import java.lang.String
import java.io.File
import net.lshift.diffa.kernel.participants.{CategoryFunction, DownstreamParticipant}
import net.lshift.diffa.participant.changes.ChangeEvent
import scala.collection.JavaConversions._

/**
 * Downstream participant implementation backed off the filesystem.
 */
class DownstreamFileParticipant(epName:String, root:String, agentRoot:String) extends FileParticipant(root, agentRoot, epName)
    with DownstreamParticipant {

    // TODO: Since we'll use the same version policy to begin with, this doesn't really matter. But we do
    //        probably want to cart enough metadata around eventually to make this work.
  def generateVersion(entityBody: String) = null

  protected def onFileChange(f: File) = {
    changesClient.onChangeEvent(ChangeEvent.forChange(idFor(f), versionFor(f), dateFor(f), attributesFor(f)))
  }
}