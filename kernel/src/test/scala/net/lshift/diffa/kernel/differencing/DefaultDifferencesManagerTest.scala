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

package net.lshift.diffa.kernel.differencing

import org.easymock.EasyMock._
import net.lshift.diffa.kernel.events.VersionID
import org.junit.{Before, Test}
import org.junit.Assert._
import net.lshift.diffa.kernel.participants._
import net.lshift.diffa.kernel.matching.{MatchingStatusListener, EventMatcher, MatchingManager}
import net.lshift.diffa.kernel.actors.PairPolicyClient
import org.easymock.EasyMock
import net.lshift.diffa.participant.scanning.ScanConstraint
import net.lshift.diffa.kernel.config.{PairRef, Domain, Endpoint, DomainConfigStore}
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.frontend.FrontendConversions._
import org.junit.experimental.theories.{Theories, Theory, DataPoint}
import org.joda.time.{DateTimeZone, DateTime, Duration, Interval}
import net.lshift.diffa.kernel.frontend.DomainPairDef
import net.lshift.diffa.kernel.escalation.EscalationHandler
import net.lshift.diffa.kernel.util.EasyMockScalaUtils._

/**
 * Test cases for the participant protocol factory.
 */
class StubParticipantProtocolFactory
    extends ScanningParticipantFactory {

  def supports(endpoint: Endpoint) = true

  def createParticipantRef(endpoint: Endpoint, pair:PairRef) = new ScanningParticipantRef {
    def scan(constraints: Seq[ScanConstraint], aggregations: Seq[CategoryFunction]) = null
  }
}
class StubContentParticipantProtocolFactory
    extends ContentParticipantFactory {

  def supports(endpoint: Endpoint) = true

  def createParticipantRef(endpoint: Endpoint, pair:PairRef) = new ContentParticipantRef {
    def retrieveContent(identifier: String) = "Some Content for " + identifier
  }
}

class DefaultDifferencesManagerTest {
  val listener = createStrictMock("listener1", classOf[DifferencingListener])

  val matcher = createStrictMock("matcher", classOf[EventMatcher])

  val domainName = "domain"
  val domainName2 = "domain2"
  val domain1 = Domain(name=domainName)
  val domain2 = Domain(name=domainName2)

  val spaceId = System.currentTimeMillis()

  val u = Endpoint(name = "1", scanUrl = "http://foo.com/scan", inboundUrl = "changes")
  val d = Endpoint(name = "2", scanUrl = "http://bar.com/scan", inboundUrl = "changes", contentRetrievalUrl = "http://foo.com/content")

  val pair1 = DomainPairDef(key = "pair1", space = spaceId, versionPolicyName = "policy", upstreamName = u.name, downstreamName = d.name)
  val pair2 = DomainPairDef(key = "pair2", space = spaceId, versionPolicyName = "policy", upstreamName = u.name, downstreamName = d.name, matchingTimeout = 5)


  val systemConfigStore = createStrictMock("systemConfigStore", classOf[SystemConfigStore])
  expect(systemConfigStore.listDomains).andStubReturn(Seq(domainName, domainName2))
  replay(systemConfigStore)

  val domainConfigStore = createStrictMock("domainConfigStore", classOf[DomainConfigStore])
  expect(domainConfigStore.listPairs(spaceId)).andStubReturn(Seq(pair1, pair2))
  replay(domainConfigStore)

  val matchingManager = createStrictMock("matchingManager", classOf[MatchingManager])
  matchingManager.addListener(anyObject.asInstanceOf[MatchingStatusListener]); expectLastCall.once
  replay(matchingManager)

  // TODO The versionPolicyManager and participantFactory should probably go into some
  // kind of test factory - i.e. look out for copy and paste

  val versionPolicy = createStrictMock("vp", classOf[VersionPolicy])


  val versionPolicyManager = new VersionPolicyManager()
  versionPolicyManager.registerPolicy("policy", versionPolicy)
  private val protocol1 = new StubParticipantProtocolFactory()
  private val protocol2 = new StubParticipantProtocolFactory()

  val participantFactory = new ParticipantFactory()
  participantFactory.registerScanningFactory(protocol1)
  participantFactory.registerScanningFactory(protocol2)
  participantFactory.registerContentFactory(new StubContentParticipantProtocolFactory)

  val pairPolicyClient = createStrictMock("pairPolicyClient", classOf[PairPolicyClient])
  EasyMock.checkOrder(pairPolicyClient, false)

  val domainDifferenceStore = createStrictMock(classOf[DomainDifferenceStore])

  val escalationHandler = createStrictMock(classOf[EscalationHandler])

  val manager = new DefaultDifferencesManager(
    systemConfigStore, domainConfigStore, domainDifferenceStore, matchingManager,
    participantFactory, listener, escalationHandler)

  // The matching manager and config stores will have been called on difference manager startup. Reset them so they
  // can be re-used
  verify(matchingManager, systemConfigStore, domainConfigStore)
  reset(matchingManager, systemConfigStore, domainConfigStore)

  @Before
  def setupStubs = {
    // TODO consider using a stub factory to build stateful objects
    // that always just get copy and pasted
    // i.e. only stub out the behavior that actually care about and want to test
    // so in this example, everything above this comment should be expect() calls
    // and everything below should be stub() calls on a factory
   
    participantFactory.createUpstreamParticipant(u, pair1.asRef)
    participantFactory.createDownstreamParticipant(d, pair1.asRef)

    expect(domainConfigStore.getPairDef(pair1.asRef)).andStubReturn(pair1)
    expect(domainConfigStore.getPairDef(pair2.asRef)).andStubReturn(pair2)
    expect(systemConfigStore.listPairs).andStubReturn(Seq(pair1,pair2))
    expect(matchingManager.getMatcher(pair1.asRef)).andStubReturn(Some(matcher))
    expect(matchingManager.getMatcher(pair2.asRef)).andStubReturn(Some(matcher))
    expect(matcher.isVersionIDActive(VersionID(pair1.asRef, "id"))).andStubReturn(true)
    expect(matcher.isVersionIDActive(VersionID(pair2.asRef, "id"))).andStubReturn(true)
    expect(matcher.isVersionIDActive(VersionID(pair1.asRef, "id2"))).andStubReturn(false)

    expect(domainConfigStore.listPairs(spaceId)).andStubReturn(Seq(pair1, pair2))

    replay(systemConfigStore, matchingManager, domainConfigStore)
  }

  def expectDifferenceForPair(pairs:DomainPairDef*)  = {
    pairs.foreach(p => {
      expect(pairPolicyClient.difference(p.asRef)).atLeastOnce
    })

    replay(pairPolicyClient)
  }
  def expectScanAndDifferenceForPair(pairs:PairRef*)  = {
    pairs.foreach(p => {
      expect(pairPolicyClient.difference(p)).atLeastOnce
    })
    pairs.foreach(p => {
      expect(pairPolicyClient.scanPair(p, None, None)).atLeastOnce
    })

    replay(pairPolicyClient)
  }

  @Test
 def shouldNotInformMatchEvents {
    // The listener should never be invoked, since listeners can just subscribe directly to the difference output
    replayAll

    expectDifferenceForPair(pair1, pair2)

    manager.onMatch(VersionID(PairRef(pair1.key,spaceId), "id"), "version", LiveWindow)

    verifyAll
  }

  @Test
  def shouldNotTriggerMismatchEventsWhenIdIsActive {
    val timestamp = new DateTime()
    // Replay to get blank stubs
    replayAll

    expectDifferenceForPair(pair1, pair2)

    manager.onMismatch(VersionID(pair1.asRef, "id"), timestamp, "uvsn", "dvsn", LiveWindow, Unfiltered)
    verifyAll
  }

  @Test
  def shouldTriggerMismatchEventsWhenIdIsInactive {
    val timestamp = new DateTime()
    val evt = DifferenceEvent(
      seqId = "123", objId = VersionID(PairRef("pair1", spaceId), "id2"), detectedAt = timestamp,
      state = MatchState.UNMATCHED, upstreamVsn = "uvsn", downstreamVsn = "dvsn")
    expect(listener.onMismatch(evt.objId, evt.detectedAt, evt.upstreamVsn, evt.downstreamVsn, LiveWindow, MatcherFiltered))
    expect(domainDifferenceStore.addReportableUnmatchedEvent(
        EasyMock.eq(evt.objId), EasyMock.eq(evt.detectedAt), EasyMock.eq(evt.upstreamVsn), EasyMock.eq(evt.downstreamVsn), anyTimestamp)).
      andReturn((NewUnmatchedEvent, evt))
    escalationHandler.initiateEscalation(evt); expectLastCall()
    replayAll
    replay(domainDifferenceStore, escalationHandler)

    expectDifferenceForPair(pair1, pair2)

    manager.onMismatch(VersionID(PairRef("pair1", spaceId), "id2"), timestamp, "uvsn", "dvsn", LiveWindow, Unfiltered)
    verifyAll
  }

    @Test
  def shouldNotEscalateEventThatHasAlreadyBeenSeen {
    val timestamp = new DateTime()
    val evt = DifferenceEvent(
      seqId = "123", objId = VersionID(PairRef("pair1", spaceId), "id2"), detectedAt = timestamp,
      state = MatchState.UNMATCHED, upstreamVsn = "uvsn", downstreamVsn = "dvsn")
    expect(listener.onMismatch(evt.objId, evt.detectedAt, evt.upstreamVsn, evt.downstreamVsn, LiveWindow, MatcherFiltered))
    expect(domainDifferenceStore.addReportableUnmatchedEvent(
        EasyMock.eq(evt.objId), EasyMock.eq(evt.detectedAt), EasyMock.eq(evt.upstreamVsn), EasyMock.eq(evt.downstreamVsn), anyTimestamp)).
      andReturn((UpdatedUnmatchedEvent, evt))
    replayAll
    replay(domainDifferenceStore, escalationHandler)

    expectDifferenceForPair(pair1, pair2)

    manager.onMismatch(VersionID(PairRef("pair1", spaceId), "id2"), timestamp, "uvsn", "dvsn", LiveWindow, Unfiltered)
    verifyAll
  }

  @Test
  def shouldNotGenerateUnmatchedEventWhenANonPendingEventExpires {
    // This will frequently occur when a repair action occurs. A miscellaneous downstream will be seen, which the
    // correlation store will immediately match, but the EventMatcher will see as expiring.

    replayAll

    expectDifferenceForPair(pair1, pair2)

    manager.onDownstreamExpired(VersionID(PairRef("pair",spaceId), "unknownid"), "dvsn")
  }

  @Test
  def shouldHandleExpiryOfAnEventThatIsPending {
    val id = VersionID(pair2.asRef, "id1")
    val now = new DateTime

    expect(matcher.isVersionIDActive(id)).andReturn(true).once()
    listener.onMismatch(id, now, "u", "d", LiveWindow, MatcherFiltered); expectLastCall().once()
    replayAll

    expectDifferenceForPair(pair1, pair2)

    manager.onMismatch(id, now, "u", "d", LiveWindow, Unfiltered)
    manager.onDownstreamExpired(id, "d")
  }

  @Test
  def shouldRetrieveContentFromAnEndpointWithContentRetrievalUrl() {
    expect(domainDifferenceStore.getEvent(spaceId, "123")).
      andReturn(DifferenceEvent(seqId = "123", objId = VersionID(pair1.asRef, "id1"), upstreamVsn = "u", downstreamVsn = "d"))
    replay(domainDifferenceStore)
    reset(domainConfigStore)
    expect(domainConfigStore.getEndpoint(spaceId, d.name)).andReturn(d)
    expect(domainConfigStore.getPairDef(pair1.asRef)).andStubReturn(pair1)
    replay(domainConfigStore)

    val content = manager.retrieveEventDetail(spaceId, "123", ParticipantType.DOWNSTREAM)
    assertEquals("Some Content for id1", content)
  }

  @Test
  def shouldNotAttemptToRetrieveContentFromEndpointWithNoContentRetrievalUrl() {
    expect(domainDifferenceStore.getEvent(spaceId, "123")).
      andReturn(DifferenceEvent(seqId = "123", objId = VersionID(pair1.asRef, "id1"), upstreamVsn = "u", downstreamVsn = "d"))
    replay(domainDifferenceStore)
    reset(domainConfigStore)
    expect(domainConfigStore.getEndpoint(spaceId, u.name)).andStubReturn(u)
    expect(domainConfigStore.getPairDef(pair1.asRef)).andStubReturn(pair1)
    replay(domainConfigStore)

    val content = manager.retrieveEventDetail(spaceId, "123", ParticipantType.UPSTREAM)
    assertEquals("Content retrieval not supported", content)
  }

  @Test
  def shouldPassThroughAggregateRequests() {
    val end = new DateTime
    val start = end.minusDays(1)
    val result = Seq(AggregateTile(start, start.plusHours(12), 52), AggregateTile(end.minusHours(12), end, 14))

    expect(domainDifferenceStore.retrieveAggregates(pair1.asRef, start, end, Some(12 * 60))).andReturn(result)
    replay(domainDifferenceStore)

    assertEquals(result, manager.retrieveAggregates(pair1.asRef, start, end, Some(12 * 60)))
  }

  private def replayAll = replay(listener, matcher)
  private def verifyAll = verify(listener, matcher)
}