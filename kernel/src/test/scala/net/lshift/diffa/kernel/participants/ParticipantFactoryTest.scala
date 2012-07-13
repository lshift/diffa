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

import org.junit.Assert._
import org.junit.Assume._
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.util.EasyMockScalaUtils._
import org.junit.experimental.theories.{Theories, Theory, DataPoint}
import org.junit.runner.RunWith
import scala.collection.JavaConversions._
import net.lshift.diffa.participant.correlation.ProcessingResponse
import net.lshift.diffa.participant.scanning.{ScanConstraint, ScanResultEntry}
import net.lshift.diffa.kernel.config.{DiffaPairRef, Endpoint}

/**
 * Test cases for the participant factory.
 */
@RunWith(classOf[Theories])
class ParticipantFactoryTest {
  import ParticipantFactoryTest._

  val pair = DiffaPairRef("foo","bar")

  private val scanning1 = createStrictMock("scanning1", classOf[ScanningParticipantFactory])
  private val scanning2 = createStrictMock("scanning2", classOf[ScanningParticipantFactory])
  private val content1 = createStrictMock("content1", classOf[ContentParticipantFactory])
  private val content2 = createStrictMock("content2", classOf[ContentParticipantFactory])
  private val versioning1 = createStrictMock("versioning1", classOf[VersioningParticipantFactory])
  private val versioning2 = createStrictMock("versioning2", classOf[VersioningParticipantFactory])
  private val allFactories = Seq(scanning1, scanning2, content1, content2, versioning1, versioning2)

  private val factory = new ParticipantFactory()
  factory.registerScanningFactory(scanning1)
  factory.registerScanningFactory(scanning2)
  factory.registerContentFactory(content1)
  factory.registerContentFactory(content2)
  factory.registerVersioningFactory(versioning1)
  factory.registerVersioningFactory(versioning2)

  private val scanningRef = createStrictMock("scanningRef", classOf[ScanningParticipantRef])
  private val contentRef = createStrictMock("contentRef", classOf[ContentParticipantRef])
  private val versionRef = createStrictMock("versionRef", classOf[VersioningParticipantRef])
  private val allRefs = Seq(scanningRef, contentRef, versionRef)

  // Factories aren't order dependent
  allFactories.foreach(checkOrder(_, false))

  // Apply an accepted URL for each factory
  expect(scanning1.supports(firstScanUrlEndpoint)).andReturn(true).anyTimes
  expect(scanning2.supports(secondScanUrlEndpoint)).andReturn(true).anyTimes
  expect(scanning1.supports(allUrlsEndpoint)).andReturn(true).anyTimes
  expect(content1.supports(firstContentUrlEndpoint)).andReturn(true).anyTimes
  expect(content2.supports(secondContentUrlEndpoint)).andReturn(true).anyTimes
  expect(content1.supports(allUrlsEndpoint)).andReturn(true).anyTimes
  expect(versioning1.supports(firstVersionUrlEndpoint)).andReturn(true).anyTimes
  expect(versioning2.supports(secondVersionUrlEndpoint)).andReturn(true).anyTimes
  expect(versioning1.supports(allUrlsEndpoint)).andReturn(true).anyTimes

  // Default to factories not supporting addresses
  allFactories.foreach(f => expect(f.supports(anyObject[Endpoint])).andReturn(false).anyTimes)

  @Theory
  def shouldFailToCreateUpstreamWhenAddressIsInvalid(e:EndpointConfig) {
    assumeTrue(!e.validUpstream)
    replayAll()

    expectsInvalidParticipantException {
      factory.createUpstreamParticipant(e.endpoint, pair)
    }
  }

  @Theory
  def shouldFailToCreateDownstreamWhenAddressIsInvalid(e:EndpointConfig) {
    assumeTrue(!e.validDownstream)
    replayAll()

    expectsInvalidParticipantException {
      factory.createDownstreamParticipant(e.endpoint, pair)
    }
  }

  @Theory
  def shouldCreateUpstreamParticipantEvenWhenUrlsAreMissingButFailOperation(e:EndpointConfig) {
    assumeTrue(e.validUpstream)
    expectParticipantCreation(e)
    replayAll()

    val part = factory.createUpstreamParticipant(e.endpoint, pair)
    if (e.scan == Fails) {
      expectsInvalidParticipantOperationException {
        part.scan(Seq(), Seq())
      }
    }
    if (e.retrieveContent == Fails) {
      expectsInvalidParticipantOperationException {
        part.retrieveContent("id1")
      }
    }
  }

  @Theory
  def shouldCreateDownstreamParticipantEvenWhenUrlsAreMissingButFailOperation(e:EndpointConfig) {
    assumeTrue(e.validDownstream)
    expectParticipantCreation(e)
    replayAll()

    val part = factory.createDownstreamParticipant(e.endpoint, pair)
    if (e.scan == Fails) {
      expectsInvalidParticipantOperationException {
        part.scan(Seq(), Seq())
      }
    }
    if (e.retrieveContent == Fails) {
      expectsInvalidParticipantOperationException {
        part.retrieveContent("id1")
      }
    }
    if (e.correlateVersion == Fails) {
      expectsInvalidParticipantOperationException {
        part.generateVersion("asdasdasd")
      }
    }
  }

  @Theory
  def shouldDelegateToValidRefsInUpstreamParticipant(e:EndpointConfig) {
    val constraints = Seq(createStrictMock(classOf[ScanConstraint]))
    val aggregations = Seq(createStrictMock(classOf[CategoryFunction]))
    val scanEntries = Seq(ScanResultEntry.forAggregate("v1", Map[String, String]()))

    assumeTrue(e.validUpstream)
    expectParticipantCreation(e)
    if (e.scan != Fails) {
      expect(scanningRef.scan(constraints, aggregations)).andReturn(scanEntries)
    }
    if (e.retrieveContent != Fails) {
      expect(contentRef.retrieveContent("id1")).andReturn("content1")
    }
    replayAll()

    val part = factory.createUpstreamParticipant(e.endpoint, pair)
    if (e.scan != Fails) {
      assertEquals(scanEntries, part.scan(constraints, aggregations))
    }
    if (e.retrieveContent != Fails) {
      assertEquals("content1", part.retrieveContent("id1"))
    }

    verifyAll()
  }
  
  @Theory
  def shouldDelegateToValidRefsInDownstreamParticipant(e:EndpointConfig) {
    val constraints = Seq(createStrictMock(classOf[ScanConstraint]))
    val aggregations = Seq(createStrictMock(classOf[CategoryFunction]))
    val scanEntries = Seq(ScanResultEntry.forAggregate("v1", Map[String, String]()))
    val procResponse = new ProcessingResponse("id", "uvsn", "dvsn")

    assumeTrue(e.validDownstream)
    expectParticipantCreation(e)
    if (e.scan != Fails) {
      expect(scanningRef.scan(constraints, aggregations)).andReturn(scanEntries)
    }
    if (e.retrieveContent != Fails) {
      expect(contentRef.retrieveContent("id1")).andReturn("content1")
    }
    if (e.correlateVersion != Fails) {
      expect(versionRef.generateVersion("body")).andReturn(procResponse)
    }
    replayAll()

    val part = factory.createDownstreamParticipant(e.endpoint, pair)
    if (e.scan != Fails) {
      assertEquals(scanEntries, part.scan(constraints, aggregations))
    }
    if (e.retrieveContent != Fails) {
      assertEquals("content1", part.retrieveContent("id1"))
    }
    if (e.correlateVersion != Fails) {
      assertEquals(procResponse, part.generateVersion("body"))
    }

    verifyAll()
  }

  def replayAll() { replay(allFactories: _*); replay(allRefs: _*) }
  def verifyAll() { verify(allFactories: _*); verify(allRefs: _*) }

  def expectParticipantCreation(e:EndpointConfig) {
    e.scan match {
      case Fails     =>
      case UseFirst  => expect(scanning1.createParticipantRef(e.endpoint, pair)).andReturn(scanningRef).anyTimes
      case UseSecond => expect(scanning2.createParticipantRef(e.endpoint, pair)).andReturn(scanningRef).anyTimes
    }
    e.retrieveContent match {
      case Fails     =>
      case UseFirst  => expect(content1.createParticipantRef(e.endpoint, pair)).andReturn(contentRef).anyTimes
      case UseSecond => expect(content2.createParticipantRef(e.endpoint, pair)).andReturn(contentRef).anyTimes
    }
    e.correlateVersion match {
      case Fails     =>
      case UseFirst  => expect(versioning1.createParticipantRef(e.endpoint, pair)).andReturn(versionRef).anyTimes
      case UseSecond => expect(versioning2.createParticipantRef(e.endpoint, pair)).andReturn(versionRef).anyTimes
    }
  }

  def expectsInvalidParticipantException(f: => Unit) {
    try {
      f
      fail("Should have thrown InvalidParticipantAddressException")
    } catch {
      case ipae:InvalidParticipantAddressException =>
    }
  }

  def expectsInvalidParticipantOperationException(f: => Unit) {
    try {
      f
      fail("Should have thrown InvalidParticipantOperationException")
    } catch {
      case ipoe:InvalidParticipantOperationException =>
    }
  }
}

abstract class OperationTarget
case object Fails extends OperationTarget
case object UseFirst extends OperationTarget
case object UseSecond extends OperationTarget

case class EndpointConfig(endpoint:Endpoint,
                          validUpstream:Boolean = true, validDownstream:Boolean = true,
                          scan:OperationTarget = Fails, retrieveContent:OperationTarget = Fails, correlateVersion:OperationTarget = Fails)

object ParticipantFactoryTest {
  @DataPoint def noUrls = EndpointConfig(
    Endpoint(name = "invalid"))

  var allUrlsEndpoint = Endpoint(name = "allUrls",
    scanUrl = "http://localhost/scan", contentRetrievalUrl = "http://localhost/content",
    versionGenerationUrl = "http://localhost/corr-version")

  def invalidScanUrlEndpoint: Endpoint = Endpoint(name = "invalidScanUrl", scanUrl = "ftp://blah")
  def firstScanUrlEndpoint: Endpoint = Endpoint(name = "firstScanUrl", scanUrl = "http://localhost/scan")
  def secondScanUrlEndpoint: Endpoint = Endpoint(name = "secondScanUrl", scanUrl = "amqp://localhost/scan")
  def invalidContentUrlEndpoint: Endpoint = Endpoint(name = "invalidContentUrl", contentRetrievalUrl = "ftp://blah")
  def firstContentUrlEndpoint: Endpoint = Endpoint(name = "firstContentUrl", contentRetrievalUrl = "http://localhost/content")
  def secondContentUrlEndpoint: Endpoint = Endpoint(name = "secondContentUrl", contentRetrievalUrl = "amqp://localhost/content")
  def invalidVersionUrlEndpoint: Endpoint = Endpoint(name = "invalidVersionUrl", versionGenerationUrl = "ftp://blah")
  def firstVersionUrlEndpoint: Endpoint = Endpoint(name = "firstVersionUrl", versionGenerationUrl = "http://localhost/corr-version")
  def secondVersionUrlEndpoint: Endpoint = Endpoint(name = "secondVersionUrl", versionGenerationUrl = "amqp://localhost/corr-version")

  @DataPoint def allUrls = EndpointConfig(
    allUrlsEndpoint, scan = UseFirst, retrieveContent = UseFirst, correlateVersion = UseFirst)

  @DataPoint def invalidScanUrl = EndpointConfig(
    invalidScanUrlEndpoint,
    validUpstream = false, validDownstream = false)

  @DataPoint def firstScanUrl = EndpointConfig(
    firstScanUrlEndpoint,
    scan = UseFirst)

  @DataPoint def secondScanUrl = EndpointConfig(
    secondScanUrlEndpoint,
    scan = UseSecond)

  @DataPoint def invalidContentUrl = EndpointConfig(
    invalidContentUrlEndpoint,
    validUpstream = false, validDownstream = false)

  @DataPoint def firstContentUrl = EndpointConfig(
    firstContentUrlEndpoint,
    retrieveContent = UseFirst)

  @DataPoint def secondContentUrl = EndpointConfig(
    secondContentUrlEndpoint,
    retrieveContent = UseSecond)

  @DataPoint def invalidVersionUrl = EndpointConfig(
    invalidVersionUrlEndpoint,
    validUpstream = true, validDownstream = false)

  @DataPoint def firstVersionUrl = EndpointConfig(
    firstVersionUrlEndpoint,
    correlateVersion = UseFirst)

  @DataPoint def secondVersionUrl = EndpointConfig(
    secondVersionUrlEndpoint,
    correlateVersion = UseSecond)

}