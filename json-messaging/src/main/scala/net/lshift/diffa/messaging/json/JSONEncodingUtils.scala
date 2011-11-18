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

package net.lshift.diffa.messaging.json

import org.codehaus.jackson.map.ObjectMapper
import net.lshift.diffa.kernel.frontend.wire._
import org.codehaus.jackson.`type`.TypeReference

import java.util.List
import scala.collection.JavaConversions._

/**
 * Standard utilities for JSON encoding.
 */
object JSONEncodingUtils {

  val mapper = new ObjectMapper

  // API

  def serializeSimpleMessage(content:String) = serializeSimpleMap("message", content)

  def serializeEntityContent(content:String) = serializeSimpleMap("content", content)
  def deserializeEntityContent(wire:String) = deserializeSimpleMap(wire, "content")
  def serializeEntityContentRequest(id:String) = serializeSimpleMap("id", id)
  def deserializeEntityContentRequest(wire:String) = deserializeSimpleMap(wire, "id")
  def serializeEntityBodyRequest(body:String) = serializeSimpleMap("entityBody", body)
  def deserializeEntityBodyRequest(wire:String) = deserializeSimpleMap(wire, "entityBody")

  def deserializeWireResponse(wire:String) = mapper.readValue(wire, classOf[WireResponse])
  def serializeWireResponse(response:WireResponse) = mapper.writeValueAsString(response)

  def deserializeActionResult(wire:String) = mapper.readValue(wire, classOf[InvocationResult])
  def serializeActionResult(response:InvocationResult) = mapper.writeValueAsString(response)
  def deserializeActionRequest(wire:String) = mapper.readValue(wire, classOf[ActionInvocation])
  def serializeActionRequest(response:ActionInvocation) = mapper.writeValueAsString(response)

  def serializeEmptyResponse() = "{}"

  // Internal plumbing

  private def deserializeSimpleMap(wire:String, id:String) = mapper.readTree(wire).get(id).getTextValue

  private def serializeSimpleMap(id:String, content:String) = {
    val node = mapper.createObjectNode
    node.put(id, content)
    node.toString()
  }
  
}
