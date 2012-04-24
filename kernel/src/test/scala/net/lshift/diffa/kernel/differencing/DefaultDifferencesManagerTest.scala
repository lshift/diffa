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
import net.lshift.diffa.kernel.config.{DiffaPairRef, Domain, Endpoint, DomainConfigStore, DiffaPair}
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.frontend.FrontendConversions._
import net.lshift.diffa.kernel.differencing.DefaultDifferencesManagerTest.Scenario
import org.junit.runner.RunWith
import org.junit.experimental.theories.{Theories, Theory, DataPoint}
import org.joda.time.{DateTimeZone, DateTime, Duration, Interval}

/**
 * Test cases for the participant protocol factory.
 */
class StubParticipantProtocolFactory
    extends ScanningParticipantFactory {

  def supportsAddress(address:String) = true

  def createParticipantRef(address: String) = new ScanningParticipantRef {
    def scan(constraints: Seq[ScanConstraint], aggregations: Seq[CategoryFunction]) = null
    def close() {}
  }
}
class StubContentParticipantProtocolFactory
    extends ContentParticipantFactory {

  def supportsAddress(address:String) = true

  def createParticipantRef(address: String) = new ContentParticipantRef {
    def retrieveContent(identifier: String) = "Some Content for " + identifier
    def close() {}
  }
}

@RunWith(classOf[Theories])
class DefaultDifferencesManagerTest {
  val listener = createStrictMock("listener1", classOf[DifferencingListener])

  val matcher = createStrictMock("matcher", classOf[EventMatcher])

  val domainName = "domain"
  val domainName2 = "domain2"
  val domain1 = Domain(name=domainName)
  val domain2 = Domain(name=domainName2)

  val u = Endpoint(name = "1", scanUrl = "http://foo.com/scan", inboundUrl = "changes")
  val d = Endpoint(name = "2", scanUrl = "http://bar.com/scan", inboundUrl = "changes", contentRetrievalUrl = "http://foo.com/content")

  val pair1 = DiffaPair(key = "pair1", domain = domain1, versionPolicyName = "policy", upstream = u.name, downstream = d.name)
  val pair2 = DiffaPair(key = "pair2", domain = domain1, versionPolicyName = "policy", upstream = u.name, downstream = d.name, matchingTimeout = 5)


  val systemConfigStore = createStrictMock("systemConfigStore", classOf[SystemConfigStore])
  expect(systemConfigStore.listDomains).andStubReturn(Seq(domain1, domain2))
  replay(systemConfigStore)

  val domainConfigStore = createStrictMock("domainConfigStore", classOf[DomainConfigStore])
  expect(domainConfigStore.listPairs(domainName)).andStubReturn(Seq(toPairDef(pair1), toPairDef(pair2)))
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

  val manager = new DefaultDifferencesManager(
    systemConfigStore, domainConfigStore, domainDifferenceStore, matchingManager,
    participantFactory, listener)

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
   
    participantFactory.createUpstreamParticipant(u)
    participantFactory.createDownstreamParticipant(d) 

    expect(systemConfigStore.getPair(domainName, "pair1")).andStubReturn(pair1)
    expect(systemConfigStore.getPair(domainName, "pair2")).andStubReturn(pair2)
    expect(systemConfigStore.listPairs).andStubReturn(Seq(pair1,pair2))
    expect(matchingManager.getMatcher(pair1.asRef)).andStubReturn(Some(matcher))
    expect(matchingManager.getMatcher(pair2.asRef)).andStubReturn(Some(matcher))
    expect(matcher.isVersionIDActive(VersionID(pair1.asRef, "id"))).andStubReturn(true)
    expect(matcher.isVersionIDActive(VersionID(pair2.asRef, "id"))).andStubReturn(true)
    expect(matcher.isVersionIDActive(VersionID(pair1.asRef, "id2"))).andStubReturn(false)

    expect(domainConfigStore.listPairs(domainName)).andStubReturn(Seq(toPairDef(pair1), toPairDef(pair2)))

    replay(systemConfigStore, matchingManager, domainConfigStore)
  }

  def expectDifferenceForPair(pairs:DiffaPair*)  = {
    pairs.foreach(p => {
      expect(pairPolicyClient.difference(p.asRef)).atLeastOnce
    })

    replay(pairPolicyClient)
  }
  def expectScanAndDifferenceForPair(pairs:DiffaPair*)  = {
    pairs.foreach(p => {
      expect(pairPolicyClient.difference(p.asRef)).atLeastOnce
    })
    pairs.foreach(p => {
      expect(pairPolicyClient.scanPair(p.asRef, None)).atLeastOnce
    })

    replay(pairPolicyClient)
  }

  @Test
 def shouldNotInformMatchEvents {
    // The listener should never be invoked, since listeners can just subscribe directly to the difference output
    replayAll

    expectDifferenceForPair(pair1, pair2)

    manager.onMatch(VersionID(DiffaPairRef(pair1.key,"domain"), "id"), "version", LiveWindow)

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
    expect(listener.onMismatch(VersionID(DiffaPairRef("pair1", domainName), "id2"), timestamp, "uvsn", "dvsn", LiveWindow, MatcherFiltered))
    replayAll

    expectDifferenceForPair(pair1, pair2)

    manager.onMismatch(VersionID(DiffaPairRef("pair1", domainName), "id2"), timestamp, "uvsn", "dvsn", LiveWindow, Unfiltered)
    verifyAll
  }

  @Test
  def shouldNotGenerateUnmatchedEventWhenANonPendingEventExpires {
    // This will frequently occur when a repair action occurs. A miscellaneous downstream will be seen, which the
    // correlation store will immediately match, but the EventMatcher will see as expiring.

    replayAll

    expectDifferenceForPair(pair1, pair2)

    manager.onDownstreamExpired(VersionID(DiffaPairRef("pair",domainName), "unknownid"), "dvsn")
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
    expect(domainDifferenceStore.getEvent(domainName, "123")).
      andReturn(DifferenceEvent(seqId = "123", objId = VersionID(pair1.asRef, "id1"), upstreamVsn = "u", downstreamVsn = "d"))
    replay(domainDifferenceStore)
    reset(domainConfigStore)
    expect(domainConfigStore.getEndpoint(domainName, d.name)).andReturn(d)
    replay(domainConfigStore)

    val content = manager.retrieveEventDetail(domainName, "123", ParticipantType.DOWNSTREAM)
    assertEquals("Some Content for id1", content)
  }

  @Test
  def shouldNotAttemptToRetrieveContentFromEndpointWithNoContentRetrievalUrl() {
    expect(domainDifferenceStore.getEvent(domainName, "123")).
      andReturn(DifferenceEvent(seqId = "123", objId = VersionID(pair1.asRef, "id1"), upstreamVsn = "u", downstreamVsn = "d"))
    replay(domainDifferenceStore)
    reset(domainConfigStore)
    expect(domainConfigStore.getEndpoint(domainName, u.name)).andStubReturn(u)
    replay(domainConfigStore)

    val content = manager.retrieveEventDetail(domainName, "123", ParticipantType.UPSTREAM)
    assertEquals("Content retrieval not supported", content)
  }

  @Theory
  def shouldRetrieveTiles(scenario:Scenario) {

    val blobs = Array.fill(scenario.arraySize)(0)

    def divisions(d:Duration) = d.getStandardMinutes.intValue() / ZoomLevels.lookupZoomLevel(scenario.zoomLevel)

    scenario.events.foreach{ case(tileGroupStart, events) => {

      if (events.isEmpty) {
        expect(domainDifferenceStore.retrieveEventTiles(
            EasyMock.eq(pair1.asRef),
            EasyMock.eq(scenario.zoomLevel),
            EasyMock.eq(tileGroupStart))
        ).andStubReturn(Some(TileGroup(tileGroupStart, Map())))
      }
      else {
        events.foreach{ case(timestamp, blobSize) => {

          val eventTileStart = ZoomLevels.containingInterval(timestamp, scenario.zoomLevel).getStart

          expect(domainDifferenceStore.retrieveEventTiles(
            EasyMock.eq(pair1.asRef),
            EasyMock.eq(scenario.zoomLevel),
            EasyMock.eq(tileGroupStart))
          ).andStubReturn(Some(TileGroup(tileGroupStart, Map(eventTileStart -> blobSize))))

          val alignedStartInterval = ZoomLevels.containingInterval(scenario.interval.getStart, scenario.zoomLevel)
          val offset = divisions(new Duration(alignedStartInterval.getStart, timestamp))
          blobs(offset) = blobSize
        }
      }
     }

    }}

    expect(domainDifferenceStore.retrieveEventTiles(
      EasyMock.eq(pair2.asRef),
      EasyMock.anyObject(),
      EasyMock.anyObject())
    ).andStubReturn(None)

    replay(domainDifferenceStore)

    val domainTiles = manager.retrieveEventTiles(pair1.domain.name, scenario.zoomLevel, scenario.interval)
    val pair1Tiles = domainTiles(pair1.key)
    val pair2Tiles = domainTiles(pair2.key)

    assertArrayEquals(Array.fill(scenario.arraySize)(0), pair2Tiles)

    assertArrayEquals(blobs, pair1Tiles)
  }

  private def replayAll = replay(listener, matcher)
  private def verifyAll = verify(listener, matcher)
}

/**
 * The data sets are divided into two groups:
 *
 * - Queries that should yield 32-column aligned results, seeing as these are easier to read
 * - Queries that span tile group aligned boundaries, to verify that the merging is working properly
 *
 * This should make the data points more comprehensive and easier to troubleshoot.
 *
 */
object DefaultDifferencesManagerTest {

  // ALIGNED QUERIES

  /**
   * Tiles at 15 minute zoom level should be grouped into 8 hour aligned slots, which gives 32 tiles.
   */
  @DataPoint def alignedQuarterHourly = Scenario(
                                          zoomLevel = ZoomLevels.QUARTER_HOURLY,
                                          arraySize = 32,
                                          events = Map(
                                            new DateTime(1976,10,14,8,0,0,0, DateTimeZone.UTC)
                                                -> Map(new DateTime(1976,10,14,8,23,0,0, DateTimeZone.UTC) -> 12)
                                          ),
                                          interval = new Interval(new DateTime(1976,10,14,8,0,0,0, DateTimeZone.UTC),
                                                                  new DateTime(1976,10,14,15,45,0,0, DateTimeZone.UTC))
                                        )

  /**
   * Tiles at 30 minute zoom level should be grouped into 12 hour aligned slots, which gives 24 tiles.
   */
  @DataPoint def alignedHalfHourly = Scenario(
                                          zoomLevel = ZoomLevels.HALF_HOURLY,
                                          arraySize = 24,
                                          events = Map(
                                            new DateTime(1993,3,3,12,0,0,0, DateTimeZone.UTC)
                                                -> Map(new DateTime(1993,3,3,12,23,0,0, DateTimeZone.UTC) -> 76)
                                          ),
                                          interval = new Interval(new DateTime(1993,3,3,12,0,0,0, DateTimeZone.UTC),
                                                                  new DateTime(1993,3,3,23,30,0,0, DateTimeZone.UTC))
                                        )

  @DataPoint def alignedDaily = Scenario(
                                          zoomLevel = ZoomLevels.DAILY,
                                          arraySize = 13,
                                          events = Map(
                                            new DateTime(2009,8,14,0,0,0,0, DateTimeZone.UTC)
                                                -> Map(new DateTime(2009,8,14,12,15,17,445, DateTimeZone.UTC) -> 88),
                                            new DateTime(2009,8,15,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(2009,8,16,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(2009,8,17,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(2009,8,18,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(2009,8,19,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(2009,8,20,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(2009,8,21,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(2009,8,22,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(2009,8,23,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(2009,8,24,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(2009,8,25,0,0,0,0, DateTimeZone.UTC)
                                                -> Map(new DateTime(2009,8,25,1,49,33,745, DateTimeZone.UTC)  -> 81)
                                          ),
                                          interval = new Interval(new DateTime(2009,8,14,0,0,0,0, DateTimeZone.UTC),
                                                                  new DateTime(2009,8,26,0,0,0,0, DateTimeZone.UTC))
                                        )

  // SPANNING QUERIES

  /**
   * Show that 15 minute zoom level can span 8 hour hour aligned slots.
   */

  @DataPoint def spanningQuarterHourly = Scenario(
                                          zoomLevel = ZoomLevels.QUARTER_HOURLY,
                                          arraySize = 4,
                                          events = Map(
                                            new DateTime(1922,1,11,0,0,0,0, DateTimeZone.UTC)
                                                -> Map(new DateTime(1922,1,11,7,57,34,345, DateTimeZone.UTC) -> 17),
                                            new DateTime(1922,1,11,8,0,0,0, DateTimeZone.UTC)
                                                -> Map(new DateTime(1922,1,11,8,1,19,883, DateTimeZone.UTC)  -> 29)
                                          ),
                                          interval = new Interval(new DateTime(1922,1,11,7,31,0,0, DateTimeZone.UTC),
                                                                  new DateTime(1922,1,11,8,29,0,0, DateTimeZone.UTC))
                                        )

  /**
   * Show that 15 minute zoom level can span 8 hour hour aligned slots.
   */
  @DataPoint def spanningHalfHourly = Scenario(
                                          zoomLevel = ZoomLevels.HALF_HOURLY,
                                          arraySize = 9,
                                          events = Map(
                                            new DateTime(1968,5,6,0,0,0,0, DateTimeZone.UTC)
                                                -> Map(new DateTime(1968,5,6,10,19,33,745, DateTimeZone.UTC) -> 53),
                                            new DateTime(1968,5,6,12,0,0,0, DateTimeZone.UTC)
                                                -> Map(new DateTime(1968,5,6,14,17,10,983, DateTimeZone.UTC) -> 102)
                                          ),
                                          interval = new Interval(new DateTime(1968,5,6,10,2,0,0, DateTimeZone.UTC),
                                                                  new DateTime(1968,5,6,14,3,0,0, DateTimeZone.UTC))
                                        )

  @DataPoint def multiPeriodDaily = Scenario(
                                          zoomLevel = ZoomLevels.DAILY,
                                          arraySize = 5,
                                          events = Map(
                                            new DateTime(1995,2,11,0,0,0,0, DateTimeZone.UTC)
                                                -> Map(new DateTime(1995,2,11,0,0,0,0, DateTimeZone.UTC) -> 110),
                                            new DateTime(1995,2,12,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(1995,2,13,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(1995,2,14,0,0,0,0, DateTimeZone.UTC) -> Map(),
                                            new DateTime(1995,2,15,0,0,0,0, DateTimeZone.UTC)
                                                -> Map(new DateTime(1995,2,15,0,0,0,0, DateTimeZone.UTC) -> 210)
                                          ),
                                          interval = new Interval(new DateTime(1995,2,11,10,0,0,0, DateTimeZone.UTC),
                                                                  new DateTime(1995,2,15,10,0,0,0, DateTimeZone.UTC))
                                        )

  case class Scenario(zoomLevel:Int, arraySize:Int, events:Map[DateTime, Map[DateTime,Int]], interval:Interval)
}