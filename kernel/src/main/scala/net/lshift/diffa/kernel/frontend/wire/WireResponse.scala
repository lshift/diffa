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

package net.lshift.diffa.kernel.frontend.wire

import scala.beans.BeanProperty
import java.util.List
import net.lshift.diffa.participant.correlation.ProcessingResponse
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.differencing.AttributesUtil

/**
 * This represents a processing response in a simple easy to format fashion.
 */
case class WireResponse(
  @BeanProperty var id:String,
  @BeanProperty var uvsn:String,
  @BeanProperty var dvsn:String,
  @BeanProperty var attributes:List[String]) {

  def this() = this(null, null, null, null)

}

object WireResponse {

  def toWire(r:ProcessingResponse) = WireResponse(r.getId, r.getUvsn, r.getDvsn, AttributesUtil.toSeq(r.getAttributes.toMap))
  def fromWire(r:WireResponse) = new ProcessingResponse(r.id, new java.util.HashMap, r.uvsn, r.dvsn)
}