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

import org.junit.Test
import org.junit.Assert._
import org.joda.time.DateTime
import scala.collection.Map
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.frontend.wire._

class JSONEncodingUtilsTest {

  def time() = new DateTime().toString()

  @Test
  def optionallyDeserializeEventList = {
    val events = Seq(WireEvent("baz1", Map("foo1" -> "bar1"), List("a", "b", "c")),
                    WireEvent("baz2", Map("foo2" -> "bar2"), List("d", "e", "f")))
    val serialized = JSONEncodingUtils.serializeEventList(events)
    val deserialized = JSONEncodingUtils.maybeDeserializeEventList(serialized)
    assertEquals(events, deserialized)
  }

  @Test
  def optionallyDeserializeSingleEvent = {
    val event = Seq(WireEvent("baz1", Map("foo1" -> "bar1"), List("a", "b", "c")))
    val serialized = JSONEncodingUtils.serializeEventList(event)
    val deserialized = JSONEncodingUtils.maybeDeserializeEventList(serialized)
    assertEquals(event, deserialized)
  }

  @Test
  def wireEventRoundTrip = {
    val event = WireEvent("baz", Map("foo" -> "bar"), List("a", "b", "c"))
    val serialized = JSONEncodingUtils.serializeEvent(event)
    val deserialized = JSONEncodingUtils.deserializeEvent(serialized)
    assertNotNull(deserialized)
    assertEquals(event, deserialized)
  }

  @Test
  def wireEventListRoundTrip = {
    val event = Seq(WireEvent("baz1", Map("foo1" -> "bar1"), List("a", "b", "c")),
                    WireEvent("baz2", Map("foo2" -> "bar2"), List("d", "e", "f")))
    val serialized = JSONEncodingUtils.serializeEventList(event)
    val deserialized = JSONEncodingUtils.deserializeEventList(serialized)
    assertNotNull(deserialized)
    assertEquals(event, deserialized)
  }

  @Test
  def contentRoundTrip = {
    val content = "foobar"
    val serialized = JSONEncodingUtils.serializeEntityContent(content)
    val deserialized = JSONEncodingUtils.deserializeEntityContent(serialized)
    assertNotNull(deserialized)
    assertEquals(content, deserialized)
  }

  @Test
  def idRequestRoundTrip = {
    val id = "foobar"
    val serialized = JSONEncodingUtils.serializeEntityContentRequest(id)
    val deserialized = JSONEncodingUtils.deserializeEntityContentRequest(serialized)
    assertNotNull(deserialized)
    assertEquals(id, deserialized)
  }

  @Test
  def bodyRequestRoundTrip = {
    val id = "foobar"
    val serialized = JSONEncodingUtils.serializeEntityBodyRequest(id)
    val deserialized = JSONEncodingUtils.deserializeEntityBodyRequest(serialized)
    assertNotNull(deserialized)
    assertEquals(id, deserialized)
  }

  @Test
  def wireResponseRoundTrip = {
    val response = WireResponse("foobar", "up", "down", List("a", "b", "c"))
    val serialized = JSONEncodingUtils.serializeWireResponse(response)
    val deserialized = JSONEncodingUtils.deserializeWireResponse(serialized)
    assertNotNull(deserialized)
    assertEquals(response, deserialized)
  }

  @Test
  def actionRequestRoundTrip = {
    val response = ActionInvocation("foo","bar")
    val serialized = JSONEncodingUtils.serializeActionRequest(response)
    val deserialized = JSONEncodingUtils.deserializeActionRequest(serialized)
    assertNotNull(deserialized)
    assertEquals(response, deserialized)
  }

  @Test
  def actionResponseRoundTrip = {
    val response = InvocationResult("foo","bar")
    val serialized = JSONEncodingUtils.serializeActionResult(response)
    val deserialized = JSONEncodingUtils.deserializeActionResult(serialized)
    assertNotNull(deserialized)
    assertEquals(response, deserialized)
  }
}