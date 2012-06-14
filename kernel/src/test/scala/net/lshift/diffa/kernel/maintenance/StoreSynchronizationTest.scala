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

package net.lshift.diffa.kernel.maintenance

import org.easymock.EasyMock._
import net.lshift.diffa.kernel.config.DiffaPairRef._
import net.lshift.diffa.kernel.indexing.LuceneVersionCorrelationStoreFactory
import net.lshift.diffa.kernel.events.VersionID._
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.differencing.StringAttribute._
import org.joda.time.{DateTime, DateTimeZone}
import net.lshift.diffa.kernel.differencing._
import net.lshift.diffa.kernel.participants.ParticipantFactory
import net.lshift.diffa.kernel.actors.PairPolicyClient
import org.junit.Assert._
import net.lshift.diffa.kernel.util.StoreSynchronizationUtils._
import net.lshift.diffa.kernel.frontend.FrontendConversions._
import net.lshift.diffa.kernel.diag.{LocalDiagnosticsManager, DiagnosticsManager}
import java.io.File
import org.apache.commons.io.FileUtils
import net.lshift.diffa.kernel.matching.{MatchingStatusListener, MatchingManager, EventMatcher}
import net.lshift.diffa.kernel.StoreReferenceContainer
import org.junit.{AfterClass, After, Before, Test}
import net.lshift.diffa.kernel.config.{Domain, Endpoint, DiffaPair}
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments

class StoreSynchronizationTest {

  // Data

  val domainName = "domain"
  val domain = Domain(name=domainName)

  val u = Endpoint(name = "1", scanUrl = "http://foo.com/scan", inboundUrl = "changes")
  val d = Endpoint(name = "2", scanUrl = "http://bar.com/scan", inboundUrl = "changes")

  val pair = DiffaPair(key = "pair", domain = domain, versionPolicyName = "policy", upstream = u.name, downstream = d.name)
  val pairRef = pair.asRef

  // Stub Wiring

  val listener = createStrictMock("listener1", classOf[DifferencingListener])
  val matcher = createStrictMock("matcher", classOf[EventMatcher])

  val matchingManager = createStrictMock("matchingManager", classOf[MatchingManager])
  matchingManager.addListener(anyObject[MatchingStatusListener]); expectLastCall.once
  expect(matchingManager.getMatcher(pairRef)).andReturn(None)
  replay(matchingManager)


  val participantFactory = new ParticipantFactory()
  participantFactory.registerScanningFactory(new StubParticipantProtocolFactory())

  val pairPolicyClient = createStrictMock("pairPolicyClient", classOf[PairPolicyClient])
  checkOrder(pairPolicyClient, false)

  // Real Wiring
  private val storeReferences = StoreSynchronizationTest.storeReferences

  private val systemConfigStore = storeReferences.systemConfigStore
  private val domainConfigStore = storeReferences.domainConfigStore
  private val domainDiffsStore = storeReferences.domainDifferenceStore
  private val serviceLimitsStore = storeReferences.serviceLimitsStore

  val diagnosticsManager: DiagnosticsManager =
    new LocalDiagnosticsManager(systemConfigStore, domainConfigStore, serviceLimitsStore, StoreSynchronizationTest.explainDir)

  var store:VersionCorrelationStore = null
  var stores:LuceneVersionCorrelationStoreFactory = null

  // Wire in the diffs manager

  val diffsManager = new DefaultDifferencesManager(
    systemConfigStore, domainConfigStore, domainDiffsStore, matchingManager,
    participantFactory, listener)

  @Before
  def prepareScenario = {
    val dir = new File(StoreSynchronizationTest.indexDir)
    if (dir.exists()) {
      FileUtils.deleteDirectory(dir)
    }
    stores = new LuceneVersionCorrelationStoreFactory(
      StoreSynchronizationTest.indexDir, systemConfigStore, domainConfigStore, diagnosticsManager)
    store = stores(pairRef)

    systemConfigStore.createOrUpdateDomain(domain)
    domainConfigStore.createOrUpdateEndpoint(domainName, toEndpointDef(u))
    domainConfigStore.createOrUpdateEndpoint(domainName, toEndpointDef(d))
    domainConfigStore.createOrUpdatePair(domainName, toPairDef(pair))

    domainDiffsStore.removeDomain(domainName)
    domainDiffsStore.reset

    assertEquals(None, diffsManager.lastRecordedVersion(pairRef))
  }

  @After
  def closeStore = stores.close

  @Test
  def storesShouldSynchronizeIncrementally = {

    val writer = store.openWriter()

    val attributes = Map("foo" -> StringAttribute("bar"))
    val lastUpdated = new DateTime(2019,5,7,8,12,15,0, DateTimeZone.UTC)
    val id = VersionID(pairRef, "id1")

    writer.storeUpstreamVersion(id, attributes, lastUpdated, "v1") // Should produce store version 1
    writer.storeDownstreamVersion(id, attributes, lastUpdated.plusMinutes(1), "v2", "v3") // Should produce store version 2

    writer.flush()

    replayCorrelationStore(diffsManager, writer, store, pairRef, u, d, TriggeredByScan)

    assertEquals(Some(2L), diffsManager.lastRecordedVersion(pairRef))

  }

  @Test
  def tombstonesShouldGetRemovedAfterSync = {

    val writer = store.openWriter()

    val attributes = Map("foo" -> StringAttribute("bar"))
    val lastUpdated = new DateTime(2019,5,7,8,12,15,0, DateTimeZone.UTC)
    val id = VersionID(pairRef, "id1")

    writer.storeUpstreamVersion(id, attributes, lastUpdated, "v1") // Should produce store version 1
    writer.storeDownstreamVersion(id, attributes, lastUpdated.plusMinutes(1), "v2", "v3") // Should produce store version 2
    writer.flush()
    writer.clearUpstreamVersion(id) // Should produce store version 3
    writer.clearDownstreamVersion(id) // Should produce store version 4

    writer.flush()

    val before = store.retrieveCurrentCorrelation(id)
    assertTrue(before.isDefined)
    assertTrue(before.get.isMatched)

    replayCorrelationStore(diffsManager, writer, store, pairRef, u, d, TriggeredByScan)

      // The last recorded version won't change, since all we processed was a tombstone - which doesn't increment the
      // last processed version.
    assertEquals(Some(0L), diffsManager.lastRecordedVersion(pairRef))


    val after = store.retrieveCurrentCorrelation(id)
    assertFalse(after.isDefined)
  }

  @Test
  def storeVersionShouldSurviveClose = {
    val firstWriter = store.openWriter()

    val attributes = Map("foo" -> StringAttribute("bar"))
    val lastUpdated = new DateTime(2019,5,7,8,12,15,0, DateTimeZone.UTC)
    val id = VersionID(pairRef, "id1")

    firstWriter.storeUpstreamVersion(id, attributes, lastUpdated, "v1") // Should produce store version 1

    firstWriter.flush()
    replayCorrelationStore(diffsManager, firstWriter, store, pairRef, u, d, TriggeredByScan)
    assertEquals(Some(1L), diffsManager.lastRecordedVersion(pairRef))

    stores.close(pairRef)
    store = stores(pairRef)

    val secondWriter = store.openWriter()
    replayCorrelationStore(diffsManager, secondWriter, store, pairRef, u, d, TriggeredByScan)
    assertEquals(Some(1L), diffsManager.lastRecordedVersion(pairRef))
  }

  @Test
  def storeVersionShouldSurviveReset = {
    val writer = store.openWriter()

    val attributes = Map("foo" -> StringAttribute("bar"))
    val lastUpdated = new DateTime(2019,5,7,8,12,15,0, DateTimeZone.UTC)
    val id = VersionID(pairRef, "id1")

    writer.storeUpstreamVersion(id, attributes, lastUpdated, "v1") // Should produce store version 1
    writer.storeUpstreamVersion(id, attributes, lastUpdated, "v2") // Should produce store version 2

    writer.flush()
    replayCorrelationStore(diffsManager, writer, store, pairRef, u, d, TriggeredByScan)
    assertEquals(Some(2L), diffsManager.lastRecordedVersion(pairRef))

    writer.reset()
    replayCorrelationStore(diffsManager, writer, store, pairRef, u, d, TriggeredByScan)
    assertEquals(Some(2L), diffsManager.lastRecordedVersion(pairRef))

  }
}

object StoreSynchronizationTest {
  private[StoreSynchronizationTest] val indexDir = "target/storeSynchronizationTest"
  private[StoreSynchronizationTest] val explainDir = "target/storeSynchronizationTest-explain"

  private[StoreSynchronizationTest] val env =
    TestDatabaseEnvironments.uniqueEnvironment(indexDir)

  private[StoreSynchronizationTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  @AfterClass
  def cleanupSchema {
    storeReferences.tearDown
  }
}
