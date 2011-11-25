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

package net.lshift.diffa.kernel.participants

import collection.mutable.ListBuffer
import net.lshift.diffa.kernel.config.Endpoint
import net.lshift.diffa.participant.scanning.ScanConstraint

/**
 * Factory that will resolve participant addresses to participant instances for querying.
 */
class ParticipantFactory() {

  private val scanningFactories = new ListBuffer[ScanningParticipantFactory]
  private val contentFactories = new ListBuffer[ContentParticipantFactory]
  private val versioningFactories = new ListBuffer[VersioningParticipantFactory]

  def registerScanningFactory(f:ScanningParticipantFactory) = scanningFactories += f
  def registerContentFactory(f:ContentParticipantFactory) = contentFactories += f
  def registerVersioningFactory(f:VersioningParticipantFactory) = versioningFactories += f

  def createUpstreamParticipant(endpoint:Endpoint): UpstreamParticipant = {
    val scanningParticipant = createScanningParticipant(endpoint)
    val contentParticipant = createContentParticipant(endpoint)

    new CompositeUpstreamParticipant(endpoint.name, scanningParticipant, contentParticipant)
  }

  def createDownstreamParticipant(endpoint:Endpoint): DownstreamParticipant = {
    val scanningParticipant = createScanningParticipant(endpoint)
    val contentParticipant = createContentParticipant(endpoint)
    val versioningParticipant = createVersioningParticipant(endpoint)

    new CompositeDownstreamParticipant(endpoint.name, scanningParticipant, contentParticipant, versioningParticipant)
  }

  def createScanningParticipant(endpoint:Endpoint): Option[ScanningParticipantRef] =
    createParticipant(scanningFactories, endpoint.scanUrl)
  def createContentParticipant(endpoint:Endpoint): Option[ContentParticipantRef] =
    createParticipant(contentFactories, endpoint.contentRetrievalUrl)
  def createVersioningParticipant(endpoint:Endpoint): Option[VersioningParticipantRef] =
    createParticipant(versioningFactories, endpoint.versionGenerationUrl)

  private def createParticipant[T](factories:Seq[AddressDrivenFactory[T]], url:String):Option[T] = url match {
    case null => None
    case _ =>
      factories.find(f => f.supportsAddress(url)) match {
        case None     => throw new InvalidParticipantAddressException(url)
        case Some(f)  => Some(f.createParticipantRef(url))
      }
  }

  private class CompositeParticipant(partName:String, scanning:Option[ScanningParticipantRef], content:Option[ContentParticipantRef]) extends Participant {
    def retrieveContent(identifier: String) = content match {
      case None         => throw new InvalidParticipantOperationException(partName, "content retrieval")
      case Some(cpart)  => cpart.retrieveContent(identifier)
    }

    def scan(constraints: Seq[ScanConstraint], aggregations: Seq[CategoryFunction]) = scanning match {
      case None        => throw new InvalidParticipantOperationException(partName, "scanning")
      case Some(spart) => spart.scan(constraints, aggregations)
    }

    def close() {
      scanning.foreach(_.close())
      content.foreach(_.close())
    }
  }

  private class CompositeUpstreamParticipant(partName:String, scanning:Option[ScanningParticipantRef], content:Option[ContentParticipantRef])
      extends CompositeParticipant(partName, scanning, content)
      with UpstreamParticipant {
  }

  private class CompositeDownstreamParticipant(partName:String, scanning:Option[ScanningParticipantRef], content:Option[ContentParticipantRef], versioning:Option[VersioningParticipantRef])
      extends CompositeParticipant(partName, scanning, content)
      with DownstreamParticipant {
    
    def generateVersion(entityBody: String) = versioning match {
      case None        => throw new InvalidParticipantOperationException(partName, "version recovery")
      case Some(vpart) => vpart.generateVersion(entityBody)
    }

    override def close() {
      super.close()
      
      versioning.foreach(_.close())
    }
  }
}

class InvalidParticipantAddressException(addr:String)
    extends Exception("The address " + addr + " is not a valid participant address")
class InvalidParticipantOperationException(partName:String, op:String)
    extends Exception("The participant " + partName + " does not support " + op)