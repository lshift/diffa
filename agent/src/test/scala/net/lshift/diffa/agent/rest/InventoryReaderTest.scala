/**
 * Copyright (C) 2012 LShift Ltd.
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
package net.lshift.diffa.agent.rest

import org.junit.Test
import org.junit.Assert._
import java.io.ByteArrayInputStream
import net.lshift.diffa.participant.scanning.ScanResultEntry
import org.joda.time.{DateTimeZone, DateTime}
import java.util.HashMap
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.frontend.InvalidInventoryException

class InventoryReaderTest {
  val reader = new InventoryReader
  val emptyAttrs = new HashMap[String, String]

  @Test
  def shouldParseAnAttributelessUpload() {
    val result = parseCSV(
      "id,vsn,updated",
      "a,v1,2012-03-07T12:31:00Z",
      "b,v4,2011-12-31T07:15:12Z"
    )

    assertEquals(Seq(
      ScanResultEntry.forEntity("a", "v1", new DateTime(2012, 3, 7, 12, 31, 0, 0, DateTimeZone.UTC), emptyAttrs),
      ScanResultEntry.forEntity("b", "v4", new DateTime(2011, 12, 31, 7, 15, 12, 0, DateTimeZone.UTC), emptyAttrs)),
      result.results)
  }

  @Test
  def shouldParseExcessColumnsIntoAttributes() {
    val result = parseCSV(
      "id,vsn,updated,foo,bar,pop",
      "a,v1,2012-03-07T12:31:00Z,p,q,r",
      "b,v4,2011-12-31T07:15:12Z,x,y,z"
    )

    assertEquals(Seq(
      ScanResultEntry.forEntity("a", "v1", new DateTime(2012, 3, 7, 12, 31, 0, 0, DateTimeZone.UTC),
        new HashMap[String,String](Map("foo" -> "p", "bar" -> "q", "pop" -> "r"))),
      ScanResultEntry.forEntity("b", "v4", new DateTime(2011, 12, 31, 7, 15, 12, 0, DateTimeZone.UTC),
        new HashMap[String,String](Map("foo" -> "x", "bar" -> "y", "pop" -> "z")))),
      result.results)
  }

  @Test
  def shouldRejectEmptyCSV() {
    try {
      parseCSV("")
    } catch {
      case e:InvalidInventoryException => assertEquals("CSV file appears to be empty. No header line was found", e.getMessage)
    }
  }

  @Test
  def shouldAcceptCSVWithHeaderOnly() {
    val result = parseCSV("id,vsn,updated")
    assertEquals(Seq(), result.results)
  }

  @Test
  def shouldRejectCSVWithMissingID() {
    try {
      parseCSV("vsn,updated", "v1,2012-03-07T12:31:00Z")
    } catch {
      case e:InvalidInventoryException => assertEquals("No 'id' field is defined in the header", e.getMessage)
    }
  }

  @Test
  def shouldRejectCSVWithMissingVsn() {
    try {
      parseCSV("id,updated", "a,2012-03-07T12:31:00Z")
    } catch {
      case e:InvalidInventoryException => assertEquals("No 'vsn' field is defined in the header", e.getMessage)
    }
  }

  @Test
  def shouldAcceptCSVWithoutUpdated() {
    val result = parseCSV("id,vsn", "a,v1", "b,v2")
    assertEquals(Seq(
      ScanResultEntry.forEntity("a", "v1", null, emptyAttrs),
      ScanResultEntry.forEntity("b", "v2", null, emptyAttrs)),
      result.results)
  }

  @Test
  def shouldRejectCSVWithAnInvalidUpdatedValue() {
    try {
      parseCSV("id,vsn,updated", "a,v1,2012-03-07T12:31:00Z", "b,v2,garbled")
    } catch {
      case e:InvalidInventoryException => assertEquals("Invalid updated timestamp 'garbled' on line 3: Invalid format: \"garbled\"", e.getMessage)
    }
  }

  @Test
  def shouldRejectCSVWithIncompleteLine() {
    try {
      parseCSV("id,vsn,updated,foo,bar", "a,v1,2012-03-07T12:31:00Z,a,b", "b,v2,2012-03-07T12:31:00Z")
    } catch {
      case e:InvalidInventoryException => assertEquals("Line 3 has 3 elements, but the header had 5", e.getMessage)
    }
  }

  @Test
  def shouldAcceptCSVWithEmptyValues() {
    val result = parseCSV("id,vsn,updated,foo,bar", "a,v1,2012-03-07T12:31:00Z,x,y", "b,v2,2011-12-31T07:15:12Z,,")
    assertEquals(Seq(
      ScanResultEntry.forEntity("a", "v1", new DateTime(2012, 3, 7, 12, 31, 0, 0, DateTimeZone.UTC),
        new HashMap[String,String](Map("foo" -> "x", "bar" -> "y"))),
      ScanResultEntry.forEntity("b", "v2", new DateTime(2011, 12, 31, 7, 15, 12, 0, DateTimeZone.UTC),
        new HashMap[String,String](Map("foo" -> "", "bar" -> "")))),
      result.results)
  }

  private def parseCSV(s:String*) =
    reader.readFrom(null, null, null, null, null, new ByteArrayInputStream(s.mkString("\n").getBytes("UTF-8")))
}