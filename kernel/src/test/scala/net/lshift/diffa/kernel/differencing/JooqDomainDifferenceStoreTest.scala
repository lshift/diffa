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

import org.hibernate.exception.ConstraintViolationException
import org.junit.Assert._
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.kernel.events.VersionID
import org.junit._
import experimental.theories.{Theories, DataPoint, Theory}
import runner.RunWith
import net.lshift.diffa.kernel.differencing.JooqDomainDifferenceStoreTest.TileScenario
import org.joda.time.{DateTime, Interval, DateTimeZone}
import org.hibernate.dialect.Dialect
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import net.lshift.diffa.kernel.StoreReferenceContainer
import net.lshift.hibernate.migrations.dialects.DialectExtensionSelector
import org.jooq.exception.DataAccessException
import java.sql.SQLIntegrityConstraintViolationException
import collection.mutable.ListBuffer
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.frontend.{RepairActionDef, EscalationDef, EndpointDef, PairDef}
import org.apache.commons.lang.RandomStringUtils

/**
 * Test cases for the JooqDomainDifferenceStore.
 */
@RunWith(classOf[Theories])
class JooqDomainDifferenceStoreTest {
  private val storeReferences = JooqDomainDifferenceStoreTest.storeReferences

  private val systemConfigStore = storeReferences.systemConfigStore
  private val domainConfigStore = storeReferences.domainConfigStore
  private val domainDiffStore = storeReferences.domainDifferenceStore

  private val domainName = RandomStringUtils.randomAlphanumeric(10)

  var space = JooqDomainDifferenceStoreTest.space

  @Before
  def clear() {
    domainDiffStore.clearAllDifferences
    
    space = systemConfigStore.createOrUpdateSpace(domainName)
        
    val us = EndpointDef(name = "upstream")
    val ds = EndpointDef(name = "downstream")
    domainConfigStore.createOrUpdateEndpoint(space.id, us)
    domainConfigStore.createOrUpdateEndpoint(space.id, ds)

    val pairTemplate = PairDef(upstreamName = us.name, downstreamName = ds.name)
    val pair1 = pairTemplate.copy(key = "pair1",
      repairActions = Set(RepairActionDef(name = "r1", url = "http://localhost/repair", scope = "entity")),
      escalations = Set(EscalationDef(name = "esc1", action = "r1", actionType = "repair", rule = "upstreamMissing")))
    val pair2 = pairTemplate.copy(key = "pair2",
      repairActions = Set(RepairActionDef(name = "r1", url = "http://localhost/repair", scope = "entity")),
      escalations = Set(EscalationDef(name = "esc2", action = "r1", actionType = "repair", rule = "upstreamMissing")))

    domainConfigStore.listPairs(space.id).foreach(p => domainConfigStore.deletePair(space.id, p.key))
    domainConfigStore.createOrUpdatePair(space.id, pair1)
    domainConfigStore.createOrUpdatePair(space.id, pair2)

    domainDiffStore.reset
  }

  @Test
  def shouldNotPublishPendingUnmatchedEventInAllUnmatchedList() {
    val now = new DateTime()
    domainDiffStore.addPendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"), now, "uV", "dV", now)
    val interval = new Interval(now.minusDays(1), now.plusDays(1))
    assertEquals(0, domainDiffStore.retrieveUnmatchedEvents(space.id, interval).length)
  }

  @Test
  def shouldPublishUpgradedUnmatchedEventInAllUnmatchedList() {
    val timestamp = currentDateTime
    domainDiffStore.addPendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.upgradePendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"))

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(1, unmatched.length)
    assertEquals(VersionID(PairRef("pair1",  space.id), "id1"), unmatched.head.objId)
    assertEquals(timestamp, unmatched.head.detectedAt)
    assertEquals("uV", unmatched.head.upstreamVsn)
    assertEquals("dV", unmatched.head.downstreamVsn)
  }

  @Test
  def shouldIgnoreUpgradeRequestsForUnknownIDs() {
    domainDiffStore.upgradePendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"))
    val interval = new Interval(new DateTime(), new DateTime())
    assertEquals(0, domainDiffStore.retrieveUnmatchedEvents(space.id, interval).length)
  }

  @Test
  def shouldOverwritePendingEventsWhenNewPendingEventsArrive() {
    val timestamp = currentDateTime
    domainDiffStore.addPendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.addPendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"), timestamp, "uV2", "dV2", timestamp)
    domainDiffStore.upgradePendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"))

    // Even if there were multiple pending registrations, we should only see one unmatched event when we upgrade, and
    // it should use the details of the final pending event.
    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(1, unmatched.length)
    assertEquals(VersionID(PairRef("pair1",  space.id), "id1"), unmatched.head.objId)
    assertEquals(timestamp, unmatched.head.detectedAt)
    assertEquals("uV2", unmatched.head.upstreamVsn)
    assertEquals("dV2", unmatched.head.downstreamVsn)
  }

  @Test
  def shouldIgnoreUpgradeRequestWhenPendingEventHasBeenUpgradedAlready() {
    val timestamp = new DateTime()
    domainDiffStore.addPendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.upgradePendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"))
    domainDiffStore.upgradePendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"))

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    assertEquals(1, domainDiffStore.retrieveUnmatchedEvents(space.id, interval).length)
  }

  @Test
  def shouldIgnoreUpgradeRequestWhenPendingEventHasBeenCancelled() {
    val timestamp = new DateTime()
    domainDiffStore.addPendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"), timestamp, "uV", "dV", timestamp)
    assertTrue(domainDiffStore.cancelPendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"), "uV"))
    domainDiffStore.upgradePendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"))

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    assertEquals(0, domainDiffStore.retrieveUnmatchedEvents(space.id, interval).length)
  }

  @Test
  def shouldNotCancelPendingEventWhenProvidedVersionIsDifferent() {
    val timestamp = new DateTime()
    domainDiffStore.addPendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"), timestamp, "uV", "dV", timestamp)
    assertFalse(domainDiffStore.cancelPendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"), "uV-different"))
    domainDiffStore.upgradePendingUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"))

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    assertEquals(1, domainDiffStore.retrieveUnmatchedEvents(space.id, interval).length)
  }

  @Test
  def shouldPublishAnAddedReportableUnmatchedEvent() {
    val timestamp = currentDateTime
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(1, unmatched.length)
    assertEquals(MatchState.UNMATCHED, unmatched.head.state)
    assertEquals(VersionID(PairRef("pair2",  space.id), "id2"), unmatched.head.objId)
    assertEquals(timestamp, unmatched.head.detectedAt)
    assertEquals("uV", unmatched.head.upstreamVsn)
    assertEquals("dV", unmatched.head.downstreamVsn)
  }

  @Test(expected = classOf[IllegalArgumentException])
  def shouldNotBeAbleToIgnoreDifferenceViaWrongDomain() {
    val timestamp = new DateTime()
    val (_, evt) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)
    val randomSpaceId = System.currentTimeMillis()
    domainDiffStore.ignoreEvent(randomSpaceId, evt.seqId)
  }

  @Test
  def shouldNotPublishAnIgnoredReportableUnmatchedEventInRetrieveUnmatchedEventsQuery() {
    val timestamp = new DateTime()
    val (_, evt) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.ignoreEvent(space.id, evt.seqId)

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(0, unmatched.length)
  }

  @Test
  def shouldUpdatePreviouslyIgnoredReportableUnmatchedEvent() {
    val timestamp = new DateTime()
    val (_, event1) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV1", "dV1", timestamp)
    domainDiffStore.ignoreEvent(space.id, event1.seqId)
    val (_, event2) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV2", "dV1", timestamp)
    assertTrue(event1.seqId.toInt < event2.seqId.toInt)
  }

  @Test
  def shouldReportUnmatchedEventWithinInterval() {
    val start = new DateTime(2004, 11, 6, 3, 5, 15, 0)
    val size = 60
    var frontFence = 10
    var rearFence = 10

    val interval = addUnmatchedEvents(start, size, frontFence, rearFence)

    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(size - frontFence - rearFence, unmatched.length)
  }

  @Test
  def shouldAllowAnEscalationToBeScheduled() {
    val timestamp = (new DateTime()).withMillis(0)      // MySQL truncates milliseconds, so use a round value

    val (_, event1) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id2"), timestamp, "uV1", "dV1", timestamp)
    domainDiffStore.scheduleEscalation(event1, "esc1", timestamp.plusSeconds(10))
    val scheduledEvent = domainDiffStore.getEvent(space.id, event1.seqId)
    assertEquals("esc1", scheduledEvent.nextEscalation)
    assertEquals(timestamp.plusSeconds(10), scheduledEvent.nextEscalationTime)

    val (_, event2) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id3"), timestamp, "uV1", "dV1", timestamp)
    domainDiffStore.scheduleEscalation(event2, "esc1", timestamp.plusSeconds(30))

    def collectDifferences(cutoff:DateTime) = {
      val buffer = ListBuffer[DifferenceEvent]()
      domainDiffStore.pendingEscalatees(cutoff, e => buffer += e)
      buffer.toSeq
    }

    assertEquals(Set[String](), collectDifferences(timestamp).map(_.objId.id).toSet)
    assertEquals(Set[String]("id2"), collectDifferences(timestamp.plusSeconds(10)).map(_.objId.id).toSet)
    assertEquals(Set[String]("id2", "id3"), collectDifferences(timestamp.plusSeconds(30)).map(_.objId.id).toSet)
  }

  @Test
  def shouldRemoveScheduledEscalationsFromMatchedEvent() {
    val timestamp = new DateTime()

    val (_, event1) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id2"), timestamp, "uV1", "dV1", timestamp)
    domainDiffStore.scheduleEscalation(event1, "esc1", timestamp.plusSeconds(10))
    val matched = domainDiffStore.addMatchedEvent(event1.objId, "v1")

    val scheduledEvent = domainDiffStore.getEvent(space.id, matched.seqId)
    assertNull(scheduledEvent.nextEscalation)
    assertNull(scheduledEvent.nextEscalationTime)
  }

  @Test
  def shouldAllowEscalationsToBeClearedForAPair() {
    val timestamp = new DateTime()

    val (_, event1) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id1"), timestamp, "uV1", "dV1", timestamp)
    domainDiffStore.scheduleEscalation(event1, "esc1", timestamp.plusSeconds(10))
    val (_, event2) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair1", space.id), "id2"), timestamp, "uV1", "dV1", timestamp)
    domainDiffStore.scheduleEscalation(event2, "esc1", timestamp.plusSeconds(10))
    val (_, event3) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id1"), timestamp, "uV1", "dV1", timestamp)
    domainDiffStore.scheduleEscalation(event3, "esc2", timestamp.plusSeconds(10))

    domainDiffStore.unscheduleEscalations(PairRef("pair1", space.id))

    val updatedEvent1 = domainDiffStore.getEvent(space.id, event1.seqId)
    assertNull(updatedEvent1.nextEscalation)
    assertNull(updatedEvent1.nextEscalationTime)
    val updatedEvent2 = domainDiffStore.getEvent(space.id, event2.seqId)
    assertNull(updatedEvent2.nextEscalation)
    assertNull(updatedEvent2.nextEscalationTime)
    val updatedEvent3 = domainDiffStore.getEvent(space.id, event3.seqId)
    assertEquals("esc2", updatedEvent3.nextEscalation)

    // Shred the milliseconds due to MySQL limitation
    assertEquals(timestamp.plusSeconds(10).withMillis(0), updatedEvent3.nextEscalationTime.withMillis(0))
  }

  @Test
  def shouldCountUnmatchedEventWithinInterval() {
    val start = new DateTime(2004, 11, 6, 3, 5, 15, 0)
    val size = 60
    var frontFence = 10
    var rearFence = 10

    val interval = addUnmatchedEvents(start, size, frontFence, rearFence)

    val unmatchedCount = domainDiffStore.countUnmatchedEvents(PairRef("pair2", space.id), interval.getStart, interval.getEnd)
    assertEquals(size - frontFence - rearFence, unmatchedCount)
  }

  @Test
  def shouldCountUnmatchedEventWithinOpenBottomInterval() {
    val start = new DateTime(2004, 11, 6, 3, 5, 15, 0)
    val size = 60
    val frontFence = 10
    val rearFence = 10

    val interval = addUnmatchedEvents(start, size, frontFence, rearFence)

    val unmatchedOpenBottomCount = domainDiffStore.countUnmatchedEvents(PairRef("pair2", space.id), null, interval.getEnd)
    assertEquals(size - rearFence, unmatchedOpenBottomCount)
  }

  @Test
  def shouldCountUnmatchedEventWithinOpenTopInterval() {
    val start = new DateTime(2004, 11, 6, 3, 5, 15, 0)
    val size = 60
    val frontFence = 10
    val rearFence = 10

    val interval = addUnmatchedEvents(start, size, frontFence, rearFence)

    val unmatchedOpenTopCount = domainDiffStore.countUnmatchedEvents(PairRef("pair2", space.id), interval.getStart, null)
    assertEquals(size - frontFence, unmatchedOpenTopCount)
  }

  @Test
  def shouldNotCountMatchedEvents() {
    val timestamp = new DateTime

    val (_, evt) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.addMatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), "uV")

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatchedCount = domainDiffStore.countUnmatchedEvents(PairRef("pair2", space.id), interval.getStart, interval.getEnd)
    assertEquals(0, unmatchedCount)
  }

  @Test
  def matchedEventShouldCancelPreviouslyIgnoredEvent() {
    val timestamp = new DateTime
    val id = VersionID(PairRef("pair2", space.id), "id2")

    val (_, event1) = domainDiffStore.addReportableUnmatchedEvent(id, timestamp, "uV", "dV", timestamp)
    val event2 = domainDiffStore.ignoreEvent(space.id, event1.seqId)
    assertMatchState(space.id, event2.seqId, MatchState.IGNORED)

    val event3 = domainDiffStore.addMatchedEvent(id, "version")
    assertMatchState(space.id, event3.seqId, MatchState.MATCHED)
  }

  private def assertMatchState(space:Long, sequenceId:String, state:MatchState) = {
    val event = domainDiffStore.getEvent(space, sequenceId)
    assertEquals(state, event.state)
  }

  @Test
  def shouldNotCountIgnoredEvents() {
    val timestamp = new DateTime

    val (_, evt) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.ignoreEvent(space.id, evt.seqId)

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatchedCount = domainDiffStore.countUnmatchedEvents(PairRef("pair2", space.id), interval.getStart, interval.getEnd)
    assertEquals(0, unmatchedCount)
  }

  def addUnmatchedEvents(start:DateTime, size:Int, frontFence:Int, rearFence:Int) : Interval = {
    for (i <- 1 to size) {
      val timestamp = start.plusMinutes(i)
      domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id" + i), timestamp, "uV", "dV", timestamp)
    }
    new Interval(start.plusMinutes(frontFence + 1), start.plusMinutes(size - rearFence + 1))
  }

  @Test
  def shouldFindMaximumEventIdForInterval() {
    val start = new DateTime(2004, 11, 6, 3, 5, 15, 0)
    val size = 60
    val frontFence = 10
    val rearFence = 10

    val interval = addUnmatchedEvents(start, size, frontFence, rearFence)
    val pair = PairRef("pair2", space.id)

    val middleMax = domainDiffStore.maxSequenceId(pair, interval.getStart, interval.getEnd)
    val lowerMax = domainDiffStore.maxSequenceId(pair, null, interval.getStart)
    val upperMax = domainDiffStore.maxSequenceId(pair, interval.getEnd, null)

    assertTrue(middleMax + " is not > " + lowerMax, middleMax > lowerMax)
    assertTrue(middleMax + " is not < " + upperMax, middleMax < upperMax)
  }

  @Test
  def shouldPageReportableUnmatchedEvent() {
    val start = new DateTime(1982, 5, 5, 14, 15, 19, 0)
    val size = 100
    var frontFence = 20
    var rearFence = 50

    // Set a bound so that 30 events fall into the window
    val interval = addUnmatchedEvents(start, size, frontFence, rearFence)

    // Create an interval that is wide enough to get every event ever
    val veryWideInterval = new Interval(start.minusDays(1), start.plusDays(1))

    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, veryWideInterval)
    assertEquals(size, unmatched.length)

    // Requesting 19 elements with an offset of 10 from 30 elements should yield elements 10 through to 28
    val containedPage = domainDiffStore.retrievePagedEvents(PairRef("pair2", space.id), interval, 10, 19)
    assertEquals(19, containedPage.length)

    // Requesting 19 elements with an offset of 20 from 30 elements should yield elements 20 through to 29
    val splitPage = domainDiffStore.retrievePagedEvents(PairRef("pair2", space.id), interval, 20, 19)
    assertEquals(10, splitPage.length)

  }

  @Test
  def shouldNotPublishAnIgnoredReportableUnmatchedEventInPagedEventQuery() {
    val timestamp = new DateTime()
    val (_, evt) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.ignoreEvent(space.id, evt.seqId)

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val containedPage = domainDiffStore.retrievePagedEvents(PairRef("pair2", space.id), interval, 0, 100)
    assertEquals(0, containedPage.length)
  }

  @Test
  def shouldPublishAnIgnoredReportableUnmatchedEventInPagedEventQueryWhenIgnoredEntitiesAreRequested() {
    val timestamp = new DateTime()
    val (_, evt) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.ignoreEvent(space.id, evt.seqId)

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val containedPage = domainDiffStore.retrievePagedEvents(PairRef("pair2", space.id), interval, 0, 100, EventOptions(includeIgnored = true))

    assertEquals(1, containedPage.length)
    assertEquals(VersionID(PairRef("pair2", space.id), "id2"), containedPage(0).objId)
  }

  @Test
  def shouldPublishAnEventThatHasBeenUnignored() {
    val timestamp = new DateTime()
    val (_, evt) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)
    val ignored = domainDiffStore.ignoreEvent(space.id, evt.seqId)
    domainDiffStore.unignoreEvent(space.id, ignored.seqId)

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val containedPage = domainDiffStore.retrievePagedEvents(PairRef("pair2", space.id), interval, 0, 100)

    assertEquals(1, containedPage.length)
    assertEquals(VersionID(PairRef("pair2", space.id), "id2"), containedPage(0).objId)
  }

  @Test
  def shouldAddIgnoredEventThatOverridesUnmatchedEventWhenAskingForSequenceUpdate() {
    val timestamp = currentDateTime
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uuV", "ddV", timestamp)

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)

    assertEquals(1, unmatched.length)
    assertEquals(MatchState.UNMATCHED, unmatched.head.state)

    val event = domainDiffStore.addMatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), "uuV")
    val updated = domainDiffStore.getEvent(space.id, event.seqId)

    assertEquals(MatchState.MATCHED, updated.state)
    // We don't know deterministically when the updated timestamp will be because this
    // is timestamped on the fly from within the implementation of the cache
    // but we do want to assert that it is not before the reporting timestamp
    assertFalse(timestamp.isAfter(updated.detectedAt))
    assertEquals(VersionID(PairRef("pair2", space.id), "id2"), updated.objId)
  }

  @Test
  def shouldAddMatchedEventThatOverridesIgnoredEventWhenAskingForSequenceUpdate() {
    val timestamp = currentDateTime
    val (_, newUnmatched) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uuV", "ddV", timestamp)

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval).sortWith(_.sequenceId < _.sequenceId)
    assertEquals(1, unmatched.length)
    assertEquals(MatchState.UNMATCHED, unmatched.head.state)

    val event = domainDiffStore.ignoreEvent(space.id, newUnmatched.seqId)
    val updated = domainDiffStore.getEvent(space.id, event.seqId)

    assertEquals(MatchState.IGNORED, updated.state)  // Match events for ignored differences have a state IGNORED
    // We don't know deterministically when the updated timestamp will be because this
    // is timestamped on the fly from within the implementation of the cache
    // but we do want to assert that it is not before the reporting timestamp
    assertFalse(timestamp.isAfter(updated.detectedAt))
    assertEquals(VersionID(PairRef("pair2", space.id), "id2"), updated.objId)
  }

  @Test
  def shouldRemoveUnmatchedEventFromAllUnmatchedWhenAMatchHasBeenAdded() {
    val timestamp = new DateTime()
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uuV", "ddV", timestamp)
    domainDiffStore.addMatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), "uuV")
    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val updates = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)

    assertEquals(0, updates.length)
  }

  @Test
  def shouldIgnoreMatchedEventWhenNoOverridableUnmatchedEventIsStored() {
    val timestamp = new DateTime()
    // Get an initial event and a sequence number
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)
    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(1, unmatched.length)
    assertEquals(MatchState.UNMATCHED, unmatched.head.state)

    // Add a matched event for something that we don't have marked as unmatched
    domainDiffStore.addMatchedEvent(VersionID(PairRef("pair3",space.id), "id3"), "eV")
    val updates = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(1, updates.length)
  }

  @Test
  def shouldOverrideOlderUnmatchedEventsWhenNewMismatchesOccurWithDifferentDetails() {
    // Add two events for the same object, and then ensure the old list only includes the most recent one
    val timestamp = currentDateTime
    val seen = timestamp.plusSeconds(5)
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2",space.id), "id2"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2",space.id), "id2"), timestamp, "uV2", "dV2", seen)

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(1, unmatched.length)
    validateUnmatchedEvent(unmatched(0), VersionID(PairRef("pair2",space.id), "id2"), "uV2", "dV2", timestamp, seen)
  }

  @Test
  def shouldRetainOlderUnmatchedEventsWhenNewEventsAreAddedWithSameDetailsButUpdateTheSeenTime() {
    // Add two events for the same object with all the same details, and ensure that we don't modify the event
    val timestamp = currentDateTime
    val newSeen = timestamp.plusSeconds(5)
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2",space.id), "id2"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2",space.id), "id2"), timestamp.plusSeconds(15), "uV", "dV", newSeen)

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(1, unmatched.length)
    validateUnmatchedEvent(unmatched(0), VersionID(PairRef("pair2",space.id), "id2"), "uV", "dV", timestamp, newSeen)
  }

  @Test
  def shouldRemoveEventsNotSeenAfterTheGivenCutoff() {
    val timestamp = currentDateTime
    val seen1 = timestamp.plusSeconds(5)
    val seen2 = timestamp.plusSeconds(8)
    val cutoff = timestamp.plusSeconds(9)
    val seen3 = timestamp.plusSeconds(10)
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2",space.id), "id1"), timestamp, "uV", "dV", seen1)   // Before the cutoff, will be removed
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2",space.id), "id2"), timestamp, "uV", "dV", seen2)   // Before the cutoff, will be removed
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2",space.id), "id3"), timestamp, "uV", "dV", seen3)

    val event = domainDiffStore.addMatchedEvent(VersionID(PairRef("pair2",space.id), "id3"), "uV")
    domainDiffStore.addMatchedEvent(VersionID(PairRef("pair2",space.id), "id2"), "uV")
    domainDiffStore.addMatchedEvent(VersionID(PairRef("pair2",space.id), "id1"), "uV")

    domainDiffStore.expireMatches(cutoff)


    val matched = domainDiffStore.getEvent(space.id, event.seqId)
    validateMatchedEvent(matched, VersionID(PairRef("pair2",space.id), "id3"), "uV", timestamp)
  }

  @Test
  def shouldNotRemoveEventsSeenExactlyAtTheGivenCutoff() {
    val timestamp = currentDateTime
    val seen1 = timestamp.plusSeconds(5)
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2",space.id), "id1"), timestamp, "uV", "dV", seen1)

    val event = domainDiffStore.addMatchedEvent(VersionID(PairRef("pair2",space.id), "id1"), "uV")
    domainDiffStore.expireMatches(seen1)

    val matched = domainDiffStore.getEvent(space.id, event.seqId)
    validateMatchedEvent(matched, VersionID(PairRef("pair2",space.id), "id1"), "uV", timestamp)
  }


  @Test
  def shouldAllowRetrievalOfReportedEvent() {
    val timestamp = currentDateTime
    val (_, evt) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)

    val retrieved = domainDiffStore.getEvent(space.id, evt.seqId)
    validateUnmatchedEvent(retrieved, evt.objId, evt.upstreamVsn, evt.downstreamVsn, evt.detectedAt, evt.lastSeen)
  }

  @Test
  def shouldThrowExceptionWhenRetrievingNonExistentSeqNumber() {
    try {
      domainDiffStore.getEvent(space.id, "55")    // Cache should be empty, so any seqId should be invalid
      fail("Should have thrown InvalidSequenceNumberException")
    } catch {
      case e:InvalidSequenceNumberException => assertEquals("55", e.id)
    }
  }

  @Test(expected = classOf[InvalidSequenceNumberException])
  def shouldThrowExceptionWhenRetrievingEventBySequenceNumberOfRemovedEvent() {
    val timestamp = new DateTime()
    val (_, unmatchedEvt) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.addMatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), "uV")

    domainDiffStore.getEvent(space.id, unmatchedEvt.seqId)    // Unmatched event should have been removed when matched event was added
  }

  @Test
  def shouldRemoveEventsWhenDomainIsRemoved() {
    val timestamp = new DateTime()
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id3"), timestamp, "uV", "dV", timestamp)

    domainDiffStore.removeDomain(space.id)

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(0, unmatched.length)
  }

  @Test
  def shouldRemovePendingEventsWhenDomainIsRemoved() {
    val timestamp = new DateTime()
    domainDiffStore.addPendingUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)

    domainDiffStore.removeDomain(space.id)

    // Upgrade the pending difference we previously created. We shouldn't see any differences, because we should
    // have just submitted an upgrade for a pending event that doesn't exist.
    domainDiffStore.upgradePendingUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"))

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(0, unmatched.length)
  }

  @Test
  def shouldRemoveMatches() {
    val timestamp = new DateTime()
    val seen1 = timestamp.plusSeconds(5)
    val seen2 = timestamp.plusSeconds(8)
    val cutoff = timestamp.plusSeconds(9)
    val afterAll = timestamp.plusSeconds(20)
    val interval = new Interval(timestamp, afterAll)


    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2",space.id), "id1"), timestamp, "uV", "dV", seen1)   // Before the cutoff, will be removed
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2",space.id), "id2"), timestamp, "uV", "dV", seen2)   // Before the cutoff, will be removed

    domainDiffStore.addMatchedEvent(VersionID(PairRef("pair2",space.id), "id1"), "uV")
    domainDiffStore.addMatchedEvent(VersionID(PairRef("pair2",space.id), "id2"), "uV")

    domainDiffStore.expireMatches(cutoff)

    val events = domainDiffStore.retrieveUnmatchedEvents(space.id,interval)
    assertEquals(0, events.length)
  }

  @Test
  def shouldRemoveEventsWhenPairIsRemoved() {
    val timestamp = new DateTime()
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)
    domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id3"), timestamp, "uV", "dV", timestamp)

    domainDiffStore.removePair(PairRef("pair2", space.id))

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(0, unmatched.length)
  }

  @Test
  def shouldRemovePendingEventsWhenPairIsRemoved() {
    val timestamp = new DateTime()
    domainDiffStore.addPendingUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), timestamp, "uV", "dV", timestamp)

    domainDiffStore.removePair(PairRef("pair2", space.id))

    // Upgrade the pending difference we previously created. We shouldn't see any differences, because we should
    // have just submitted an upgrade for a pending event that doesn't exist.
    domainDiffStore.upgradePendingUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"))

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)
    assertEquals(0, unmatched.length)
  }

  @Test
  def pendingEventShouldUpgradePreviouslyReportedEvent() {

    // Coarsen the time precision to seconds to workaround MySQL timestamp granularity limitation :-(
    val timestamp = new DateTime().withMillisOfSecond(0)
    val detectedAt = timestamp.plusSeconds(1)
    val seenFirst = timestamp.plusSeconds(2)
    val unmatchedAt = timestamp.plusSeconds(3)
    val seenNext = timestamp.plusSeconds(4)

    val (_, event) = domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("pair2",space.id), "id2"), detectedAt, "uV", null, seenFirst)
    domainDiffStore.addPendingUnmatchedEvent(VersionID(PairRef("pair2", space.id), "id2"), unmatchedAt, "uV", "dV", seenNext)

    try {
      domainDiffStore.getEvent(space.id, event.seqId.toString)
      fail("Should have generated an InvalidSequenceNumberException for sequence id " + event.seqId)
    }
    catch {
      case i:InvalidSequenceNumberException => // expected
    }

    val interval = new Interval(timestamp.minusDays(1), timestamp.plusDays(1))
    val unmatched = domainDiffStore.retrieveUnmatchedEvents(space.id, interval)

    assertEquals(1, unmatched.length)
    assertEquals(event.sequenceId + 1, unmatched(0).sequenceId)
    assertEquals(detectedAt, unmatched(0).detectedAt)
  }

  /**
   * Oracle DB throws an SQLException with ORA-14400 when attempting to
   * insert a partition key that does not map to any partition.  This exception
   * pre-empts what would otherwise happen if the table were not partitioned
   * (ConstraintViolationException).
   */
  @Test
  def shouldFailToAddReportableEventForNonExistentPair() {
    val lastUpdate = new DateTime()
    val seen = lastUpdate.plusSeconds(5)

    try {
      domainDiffStore.addReportableUnmatchedEvent(VersionID(PairRef("nonexistent-pair1", space.id), "id1"), lastUpdate, "uV", "dV", seen)
      fail("No DataAccessException was thrown")
    } catch {
      case e: DataAccessException =>
        assertTrue("Cause must be an SQLIntegrityConstraintViolationException or ORA-14400",
          e.getCause.isInstanceOf[SQLIntegrityConstraintViolationException]
          || e.getMessage.contains("ORA-14400"))
      case unexpected =>
        throw unexpected
    }
  }


  @Test
  def shouldFailToAddPendingEventForNonExistentPair() {
    val lastUpdate = new DateTime()
    val seen = lastUpdate.plusSeconds(5)

    try {
      domainDiffStore.addPendingUnmatchedEvent(VersionID(PairRef("nonexistent-pair2", space.id), "id1"), lastUpdate, "uV", "dV", seen)
      fail("No DataAccessException was thrown")
    } catch {
      case e: DataAccessException =>
        assertTrue("Cause must be an SQLIntegrityConstraintViolationException",
          e.getCause.isInstanceOf[SQLIntegrityConstraintViolationException])
    }
  }

  @Test
  def shouldStoreLatestVersionPerPair = {
    val pair = PairRef("pair1", space.id)
    assertEquals(None, domainDiffStore.lastRecordedVersion(pair))
    domainDiffStore.recordLatestVersion(pair, 5294967296L)
    assertEquals(Some(5294967296L), domainDiffStore.lastRecordedVersion(pair))
    domainDiffStore.removePair(pair)
    assertEquals(None, domainDiffStore.lastRecordedVersion(pair))
  }

  @Theory
  def shouldTileEvents(scenario:TileScenario) = {
    scenario.events.foreach(e => domainDiffStore.addReportableUnmatchedEvent(e.id, e.timestamp, "", "", e.timestamp))
    scenario.zoomLevels.foreach{ case (zoom, expected) => {
      expected.foreach{ case (pair, tileGroups) => {
        tileGroups.foreach(group => {
          val retrieved = retrieveEventTiles(PairRef(pair, scenario.space), zoom, group.lowerBound)
          assertEquals("Failure @ zoom level %s; ".format(zoom), group.tiles, retrieved.get.tiles)
        })
      }}
    }}
  }

  @Test
  def tilesShouldBeWithinTimeSpan = ZoomLevels.levels.foreach(tilesShouldBeWithinTimeSpanAtZoomLevel(_))

  private def tilesShouldBeWithinTimeSpanAtZoomLevel(zoomLevel:Int) {

    val observationTime = new DateTime(2008,9,7,0,0,0,0)
    val backOneWeek = observationTime.minusWeeks(1)
    val tileGroupInterval = ZoomLevels.containingTileGroupInterval(backOneWeek.minusMinutes(1), zoomLevel)
    val queryTime = tileGroupInterval.getStart

    val pair = PairRef("pair1", space.id)

    domainDiffStore.clearAllDifferences

    domainDiffStore.addReportableUnmatchedEvent(VersionID(pair, "10a"), tileGroupInterval.getEnd.plusMinutes(1), "", "", observationTime)
    domainDiffStore.addReportableUnmatchedEvent(VersionID(pair, "10b"), tileGroupInterval.getEnd.minusMinutes(1), "", "", observationTime)
    domainDiffStore.addReportableUnmatchedEvent(VersionID(pair, "10c"), tileGroupInterval.getStart.plusMinutes(1), "", "", observationTime)
    domainDiffStore.addReportableUnmatchedEvent(VersionID(pair, "10d"), tileGroupInterval.getStart.minusMinutes(1), "", "", observationTime)

    // Deliberately allow passing a potentially unaligned timestamp
    val tiles = retrieveEventTiles(pair, zoomLevel, queryTime)

    val firstIntervalStart = ZoomLevels.containingInterval(tileGroupInterval.getEnd.minusMinutes(1), zoomLevel).getStart
    val secondIntervalStart = ZoomLevels.containingInterval(tileGroupInterval.getStart.plusMinutes(1), zoomLevel).getStart

    // Special case for daily granularity - a daily zoom level will result in only one tile group,
    // hence the aggregate will contain both events
    val expectedAggregates = if (firstIntervalStart == secondIntervalStart) {
      Map(firstIntervalStart -> 2)
    }
    else {
      Map(firstIntervalStart -> 1, secondIntervalStart -> 1)
    }

    assertEquals("Zoom level %s;".format(zoomLevel), TileGroup(queryTime, expectedAggregates), tiles.get)
  }

  @Test
  def eventsShouldCauseAggregatesToBeUpdated = ZoomLevels.levels.foreach(playThroughEventsAtZoomLevel(_))

  private def playThroughEventsAtZoomLevel(zoomLevel:Int) = {
    val observationTime = new DateTime()
    val timestamp1 = observationTime.minusMinutes(ZoomLevels.lookupZoomLevel(zoomLevel) + 1)
    val timestamp2 = observationTime.minusMinutes(ZoomLevels.lookupZoomLevel(zoomLevel) + 2)

    val pair = PairRef("pair1", space.id)

    val id1 = VersionID(pair, "7a")
    val id2 = VersionID(pair, "7b")

    domainDiffStore.clearAllDifferences

    domainDiffStore.addReportableUnmatchedEvent(id1, timestamp1, "", "", observationTime)
    validateZoomRange(timestamp1, pair, zoomLevel, timestamp1)

    domainDiffStore.addReportableUnmatchedEvent(id2, timestamp2, "", "", observationTime)
    validateZoomRange(timestamp2, pair, zoomLevel, timestamp1, timestamp2)

    domainDiffStore.addMatchedEvent(id2, "")
    validateZoomRange(timestamp2, pair, zoomLevel, timestamp1)

    domainDiffStore.addMatchedEvent(id1, "")
    val tileGroup = retrieveEventTiles(pair, zoomLevel, timestamp1)
    assertTrue(tileGroup.get.tiles.isEmpty)
  }

  private def validateZoomRange(timestamp:DateTime, pair:PairRef, zoomLevel:Int, eventTimes:DateTime*) = {

    val expectedTiles = new scala.collection.mutable.HashMap[DateTime,Int]
    eventTimes.foreach(time => {

      val interval = ZoomLevels.containingInterval(time, zoomLevel)
      val startTime = interval.getStart

      expectedTiles.get(startTime) match {
        case None    => expectedTiles(startTime) = 1
        case Some(x) => expectedTiles(startTime) = x + 1
      }
    })

    // Deliberately allow passing a potentially unaligned timestamp

    val tileGroup = retrieveEventTiles(pair, zoomLevel, timestamp)

    assertEquals("Expected tile set not in range at zoom level %s;".format(zoomLevel), expectedTiles, tileGroup.get.tiles)
  }

  def retrieveEventTiles(pair:PairRef, zoomLevel:Int, timestamp:DateTime) = {
    val alignedTimespan = ZoomLevels.containingTileGroupInterval(timestamp, zoomLevel)
    val aggregateMinutes = ZoomLevels.lookupZoomLevel(zoomLevel)
    val aggregates =
      domainDiffStore.retrieveAggregates(pair, alignedTimespan.getStart, alignedTimespan.getEnd, Some(aggregateMinutes))

    val interestingAggregates = aggregates.filter(t => t.count > 0)
    Some(TileGroup(alignedTimespan.getStart, interestingAggregates.map(t => t.start -> t.count).toMap))
  }

  //
  // Helpers
  //

  private def currentDateTime = {
    if (DialectExtensionSelector.select(storeReferences.dialect).supportsFractionalSeconds) {
      DateTime.now
    } else {
      // Truncate the DateTime to the nearest second in order to work around
      // a limitation in MySQL: prior to version 5.6.4, there is no column type
      // that will keep fractional seconds.  Therefore, we should use this
      // dialect-specific truncation in order to satisfy time ordering
      // expectations.
      new DateTime().withMillisOfSecond(0)
    }
  }

  def validateUnmatchedEvent(event:DifferenceEvent, id:VersionID, usVsn:String, dsVsn:String, timestamp:DateTime, seen:DateTime) {
    assertEquals(id, event.objId)
    assertEquals(MatchState.UNMATCHED, event.state)
    assertEquals(usVsn, event.upstreamVsn)
    assertEquals(dsVsn, event.downstreamVsn)
    assertEquals(timestamp, event.detectedAt)
    assertEquals(seen, event.lastSeen)
  }

  def validateMatchedEvent(event:DifferenceEvent, id:VersionID, vsn:String, now:DateTime) {
    assertEquals(id, event.objId)
    assertEquals(MatchState.MATCHED, event.state)
    assertEquals(vsn, event.upstreamVsn)
    assertEquals(vsn, event.downstreamVsn)
    assertTrue(!event.detectedAt.isBefore(now))      // Detection should be some time at or after now
    assertTrue(!event.lastSeen.isBefore(now))        // Last seen should be some time at or after now
  }
}

object JooqDomainDifferenceStoreTest {

  var space:Space = null

  private[JooqDomainDifferenceStoreTest] val env =
    TestDatabaseEnvironments.uniqueEnvironment("target/domainCache")

  private[JooqDomainDifferenceStoreTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  @AfterClass
  def cleanupSchema {
    storeReferences.tearDown
  }

  @DataPoint def tiles = TileScenario(space.id, new Interval(new DateTime(2002,10,4,14,2,0,0, DateTimeZone.UTC),
                                                             new DateTime(2002,10,5,14,5,30,0, DateTimeZone.UTC)),
      Seq(
        // - 1 day
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1a"), timestamp = new DateTime(2002,10,4,14,2,0,0, DateTimeZone.UTC)),
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1b"), timestamp = new DateTime(2002,10,4,14,3,0,0, DateTimeZone.UTC)),
        // - 8 hours
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1c"), timestamp = new DateTime(2002,10,5,6,7,0,0, DateTimeZone.UTC)),
        // - 4 hours
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1d"), timestamp = new DateTime(2002,10,5,10,9,0,0, DateTimeZone.UTC)),
        // - 2 hours
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1e"), timestamp = new DateTime(2002,10,5,12,2,0,0, DateTimeZone.UTC)),
        // - 1 hour
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1f"), timestamp = new DateTime(2002,10,5,13,11,0,0, DateTimeZone.UTC)),
        // - 45 minutes
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1g"), timestamp = new DateTime(2002,10,5,13,21,0,0, DateTimeZone.UTC)),
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1h"), timestamp = new DateTime(2002,10,5,13,22,0,0, DateTimeZone.UTC)),
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1i"), timestamp = new DateTime(2002,10,5,13,23,0,0, DateTimeZone.UTC)),
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1j"), timestamp = new DateTime(2002,10,5,13,24,0,0, DateTimeZone.UTC)),
        // - 30 minutes
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1k"), timestamp = new DateTime(2002,10,5,13,32,0,0, DateTimeZone.UTC)),
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1l"), timestamp = new DateTime(2002,10,5,13,33,0,0, DateTimeZone.UTC)),
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1m"), timestamp = new DateTime(2002,10,5,13,34,0,0, DateTimeZone.UTC)),
        // - 15 minutes
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1n"), timestamp = new DateTime(2002,10,5,13,47,0,0, DateTimeZone.UTC)),
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1p"), timestamp = new DateTime(2002,10,5,13,48,0,0, DateTimeZone.UTC)),
        // no offset
        ReportableEvent(id = VersionID(PairRef("pair1", space.id), "1q"), timestamp = new DateTime(2002,10,5,14,4,0,0, DateTimeZone.UTC)),
        // 2nd pair
        ReportableEvent(id = VersionID(PairRef("pair2", space.id), "2a"), timestamp = new DateTime(2002,10,5,14,5,0,0, DateTimeZone.UTC)),
        ReportableEvent(id = VersionID(PairRef("pair2", space.id), "2b"), timestamp = new DateTime(2002,10,5,14,5,30,0, DateTimeZone.UTC))
      ),
      Map(ZoomLevels.QUARTER_HOURLY -> Map(
           "pair1" -> Seq(TileGroup(new DateTime(2002,10,5,8,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,14,0,0,0, DateTimeZone.UTC)  -> 1,
                                        new DateTime(2002,10,5,13,45,0,0, DateTimeZone.UTC) -> 2,
                                        new DateTime(2002,10,5,13,30,0,0, DateTimeZone.UTC) -> 3,
                                        new DateTime(2002,10,5,13,15,0,0, DateTimeZone.UTC) -> 4,
                                        new DateTime(2002,10,5,13,0,0,0, DateTimeZone.UTC)  -> 1,
                                        new DateTime(2002,10,5,12,0,0,0, DateTimeZone.UTC)  -> 1,
                                        new DateTime(2002,10,5,10,0,0,0, DateTimeZone.UTC)  -> 1)),
                          TileGroup(new DateTime(2002,10,5,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,6,0,0,0, DateTimeZone.UTC)   -> 1)),
                          TileGroup(new DateTime(2002,10,4,8,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,4,14,0,0,0, DateTimeZone.UTC)  -> 2))),
           "pair2" -> Seq(TileGroup(new DateTime(2002,10,5,8,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,14,0,0,0, DateTimeZone.UTC)  -> 2)))
         ),
          ZoomLevels.HALF_HOURLY -> Map(
           "pair1" -> Seq(TileGroup(new DateTime(2002,10,5,12,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,14,0,0,0, DateTimeZone.UTC)  -> 1,
                                        new DateTime(2002,10,5,13,30,0,0, DateTimeZone.UTC) -> 5,
                                        new DateTime(2002,10,5,13,0,0,0, DateTimeZone.UTC)  -> 5,
                                        new DateTime(2002,10,5,12,0,0,0, DateTimeZone.UTC)  -> 1)),
                          TileGroup(new DateTime(2002,10,5,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,10,0,0,0, DateTimeZone.UTC)  -> 1,
                                        new DateTime(2002,10,5,6,0,0,0, DateTimeZone.UTC)   -> 1)),
                          TileGroup(new DateTime(2002,10,4,12,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,4,14,0,0,0, DateTimeZone.UTC)  -> 2))),
           "pair2" -> Seq(TileGroup(new DateTime(2002,10,5,14,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,14,0,0,0, DateTimeZone.UTC)  -> 2)))
         ),
          ZoomLevels.HOURLY -> Map(
           "pair1" -> Seq(TileGroup(new DateTime(2002,10,5,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,14,0,0,0, DateTimeZone.UTC) -> 1,
                                         new DateTime(2002,10,5,13,0,0,0, DateTimeZone.UTC) -> 10,
                                         new DateTime(2002,10,5,12,0,0,0, DateTimeZone.UTC) -> 1,
                                         new DateTime(2002,10,5,10,0,0,0, DateTimeZone.UTC) -> 1,
                                         new DateTime(2002,10,5,6,0,0,0, DateTimeZone.UTC)  -> 1)),
                          TileGroup(new DateTime(2002,10,4,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,4,14,0,0,0, DateTimeZone.UTC) -> 2))),
           "pair2" -> Seq(TileGroup(new DateTime(2002,10,5,14,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,14,0,0,0, DateTimeZone.UTC) -> 2)))
         ),
          ZoomLevels.TWO_HOURLY -> Map(
           "pair1" -> Seq(TileGroup(new DateTime(2002,10,5,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,14,0,0,0, DateTimeZone.UTC) -> 1,
                                          new DateTime(2002,10,5,12,0,0,0, DateTimeZone.UTC) -> 11,
                                          new DateTime(2002,10,5,10,0,0,0, DateTimeZone.UTC) -> 1,
                                          new DateTime(2002,10,5,6,0,0,0, DateTimeZone.UTC)  -> 1)),
                          TileGroup(new DateTime(2002,10,4,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,4,14,0,0,0, DateTimeZone.UTC) -> 2))),
           "pair2" -> Seq(TileGroup(new DateTime(2002,10,5,14,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,14,0,0,0, DateTimeZone.UTC) -> 2)))
         ),
          ZoomLevels.FOUR_HOURLY -> Map(
           "pair1" -> Seq(TileGroup(new DateTime(2002,10,5,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,12,0,0,0, DateTimeZone.UTC) -> 12,
                                          new DateTime(2002,10,5,8,0,0,0, DateTimeZone.UTC)  -> 1,
                                          new DateTime(2002,10,5,4,0,0,0, DateTimeZone.UTC)  -> 1)),
                          TileGroup(new DateTime(2002,10,4,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,4,12,0,0,0, DateTimeZone.UTC) -> 2))),
           "pair2" -> Seq(TileGroup(new DateTime(2002,10,5,12,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,12,0,0,0, DateTimeZone.UTC) -> 2)))
         ),
          ZoomLevels.EIGHT_HOURLY -> Map(
           "pair1" -> Seq(TileGroup(new DateTime(2002,10,5,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,8,0,0,0, DateTimeZone.UTC)  -> 13,
                                          new DateTime(2002,10,5,0,0,0,0, DateTimeZone.UTC)  -> 1)),
                          TileGroup(new DateTime(2002,10,4,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,4,8,0,0,0, DateTimeZone.UTC)  -> 2))),
           "pair2" -> Seq(TileGroup(new DateTime(2002,10,5,8,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,8,0,0,0, DateTimeZone.UTC)  -> 2)))
         ),
          ZoomLevels.DAILY -> Map(
           "pair1" -> Seq(TileGroup(new DateTime(2002,10,5,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,0,0,0,0, DateTimeZone.UTC) -> 14)),
                          TileGroup(new DateTime(2002,10,4,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,4,0,0,0,0, DateTimeZone.UTC) -> 2))),
           "pair2" -> Seq(TileGroup(new DateTime(2002,10,5,0,0,0,0, DateTimeZone.UTC),
                                    Map(new DateTime(2002,10,5,0,0,0,0, DateTimeZone.UTC) -> 2)))
         )
      )
  )

  case class ReportableEvent(
    id:VersionID,
    timestamp:DateTime
  )

  case class TileScenario(
    space:Long,
    timespan:Interval,
    events:Seq[ReportableEvent],
    zoomLevels:Map[Int,Map[String,Seq[TileGroup]]]
  )
}
