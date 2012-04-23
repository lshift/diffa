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

import javax.ws.rs.Consumes
import javax.ws.rs.ext.{MessageBodyReader, Provider}
import java.lang.reflect.Type
import java.lang.annotation.Annotation
import javax.ws.rs.core.{MultivaluedMap, MediaType}
import java.lang.{String, Class}
import net.lshift.diffa.participant.scanning.ScanResultEntry
import au.com.bytecode.opencsv.CSVReader
import java.io.{InputStreamReader, InputStream}
import scala.collection.JavaConversions._
import collection.mutable.ListBuffer
import org.joda.time.format.ISODateTimeFormat
import java.util.HashMap
import net.lshift.diffa.kernel.frontend.InvalidInventoryException

/**
 * Provider for encoding and decoding Diffa inventory submissions blocks.
 */
@Provider
@Consumes(Array("text/csv", "text/comma-separated-values"))
class InventoryReader extends MessageBodyReader[ScanResultList] {
  val updatedParser = ISODateTimeFormat.dateTimeNoMillis().withZoneUTC()

  def isReadable(propType : Class[_], genericType: Type, annotations: Array[Annotation], mediaType: MediaType) =
    classOf[ScanResultList].isAssignableFrom(propType)

  def readFrom(propType:Class[ScanResultList], genericType:Type, annotations:Array[Annotation], mediaType:MediaType, httpHeaders:MultivaluedMap[String, String], entityStream:InputStream) = {
    val reader = new CSVReader(new InputStreamReader(entityStream, "UTF-8"))  // TODO: Do we support other encodings?
    val header = reader.readNext()

    if (header == null) {
      throw new InvalidInventoryException("CSV file appears to be empty. No header line was found")
    }

    val idPosition = maybeField("id", header)
    val vsnPosition = requireField("version", header)
    val updatedPosition = maybeField("updated", header)

    // Index of positions to field names
    val headerIndex = header.zipWithIndex.
      filter {
        case ("id", _)       => false
        case ("version", _)  => false
        case ("updated", _)  => false
        case _               => true
      }

    val result = ListBuffer[ScanResultEntry]()
    var lineCounter = 2     // We're already on line 2, since we read the header
    var line:Array[String] = null
    do {
      line = reader.readNext()

      if (line != null) {
        if (line.length != header.length) {
            throw new InvalidInventoryException("Line %s has %s elements, but the header had %s".format(
              lineCounter, line.length, header.length))
        }

        val entry = new ScanResultEntry
        idPosition.foreach(p => entry.setId(line(p)))
        entry.setVersion(line(vsnPosition))
        updatedPosition.foreach(p => {
          try {
            entry.setLastUpdated(updatedParser.parseDateTime(line(p)))
          } catch {
            case ex => throw new InvalidInventoryException("Invalid updated timestamp '%s' on line %s: %s".format(
              line(p), lineCounter, ex.getMessage))
          }
        })
        entry.setAttributes(new HashMap[String, String])
        headerIndex.foreach { case (fieldName, idx) => entry.getAttributes.put(fieldName, line(idx)) }

        result += entry

        lineCounter += 1
      }
    } while (line != null)

    new ScanResultList(result.toSeq)
  }

  private def requireField(name:String, header:Array[String]) = header.indexOf(name) match {
    case -1 => throw new InvalidInventoryException("No '" + name + "' field is defined in the header")
    case x  => x
  }
  private def maybeField(name:String, header:Array[String]) = header.indexOf(name) match {
    case -1 => None
    case x  => Some(x)
  }
}

/**
 * Simple container class of scan results that ensure we won't run afoul of type erasure when working out whether our
 * reader is appropriate.
 */
case class ScanResultList(results:Seq[ScanResultEntry])