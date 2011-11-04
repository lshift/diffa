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
import net.lshift.diffa.kernel.config.{HibernateDomainConfigStoreTest, DomainConfigStore, DiffaPairRef}
import net.lshift.diffa.kernel.config.{Domain,Endpoint,Pair => DiffaPair}
import net.lshift.diffa.kernel.frontend.FrontendConversions._
import net.lshift.diffa.kernel.config.system.{HibernateSystemConfigStoreTest, SystemConfigStore}
import net.sf.ehcache.CacheManager
import net.lshift.diffa.kernel.util.DatabaseEnvironment
import org.hibernate.dialect.Dialect
import net.lshift.diffa.kernel.diag.{LocalDiagnosticsManager, DiagnosticsManager}
import java.io.File
import org.apache.lucene.store.MMapDirectory
import org.apache.commons.io.FileUtils
import org.junit.{After, Before, Test}
import net.lshift.diffa.kernel.matching.{MatchingStatusListener, MatchingManager, EventMatcher}

class StoreSynchronizationTest {

  // Data

  val domainName = "domain"
  val domain = Domain(name=domainName)

  val u = Endpoint(name = "1", scanUrl = "http://foo.com/scan", contentType = "application/json", inboundUrl = "changes")
  val d = Endpoint(name = "2", scanUrl = "http://bar.com/scan", contentType = "application/json", inboundUrl = "changes")

  val pair = DiffaPair(key = "pair", domain = domain, versionPolicyName = "policy", upstream = u, downstream = d)
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

  val domainConfigStore = HibernateDomainConfigStoreTest.domainConfigStore
  val systemConfigStore = HibernateDomainConfigStoreTest.systemConfigStore
  val sf = HibernateDomainConfigStoreTest.sessionFactory

  val dialect = Class.forName(DatabaseEnvironment.DIALECT).newInstance().asInstanceOf[Dialect]
  val domainDifferenceStore = new HibernateDomainDifferenceStore(sf, new CacheManager(), dialect)

  val indexDir = "target/storeSynchronizationTest"
  val explainDir = "target/storeSynchronizationTest-explain"


  val diagnosticsManager = new LocalDiagnosticsManager(domainConfigStore, explainDir)

  var store:VersionCorrelationStore = null
  var stores:LuceneVersionCorrelationStoreFactory[MMapDirectory] = null

  // Wire in the diffs manager

  val diffsManager = new DefaultDifferencesManager(
    systemConfigStore, domainConfigStore, domainDifferenceStore, matchingManager,
    participantFactory, listener)

  @Before
  def prepareScenario = {
    val dir = new File(indexDir)
    if (dir.exists()) {
      FileUtils.deleteDirectory(dir)
    }
    stores = new LuceneVersionCorrelationStoreFactory(indexDir, classOf[MMapDirectory], systemConfigStore, diagnosticsManager)
    store = stores(pairRef)

    systemConfigStore.createOrUpdateDomain(domain)
    domainConfigStore.createOrUpdateEndpoint(domainName, toEndpointDef(u))
    domainConfigStore.createOrUpdateEndpoint(domainName, toEndpointDef(d))
    domainConfigStore.createOrUpdatePair(domainName, toPairDef(pair))
    domainDifferenceStore.removeLatestRecordedVersion(pairRef)

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

    replayCorrelationStore(diffsManager, writer, store, pair, TriggeredByScan)

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
    writer.clearUpstreamVersion(id) // Should produce store version 3
    writer.clearDownstreamVersion(id) // Should produce store version 4

    writer.flush()

    def checkUnmatched(expectation:Int) = {
      val unmatched = store.unmatchedVersions(Seq(), Seq(), None)
      assertEquals(expectation, unmatched.toSeq.length)
    }

    checkUnmatched(1)

    replayCorrelationStore(diffsManager, writer, store, pair, TriggeredByScan)
    assertEquals(Some(4L), diffsManager.lastRecordedVersion(pairRef))

    checkUnmatched(0)
  }

  @Test
  def storeVersionShouldSurviveClose = {
    val firstWriter = store.openWriter()

    val attributes = Map("foo" -> StringAttribute("bar"))
    val lastUpdated = new DateTime(2019,5,7,8,12,15,0, DateTimeZone.UTC)
    val id = VersionID(pairRef, "id1")

    firstWriter.storeUpstreamVersion(id, attributes, lastUpdated, "v1") // Should produce store version 1

    firstWriter.flush()
    replayCorrelationStore(diffsManager, firstWriter, store, pair, TriggeredByScan)
    assertEquals(Some(1L), diffsManager.lastRecordedVersion(pairRef))

    stores.close(pairRef)
    store = stores(pairRef)

    val secondWriter = store.openWriter()
    replayCorrelationStore(diffsManager, secondWriter, store, pair, TriggeredByScan)
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
    replayCorrelationStore(diffsManager, writer, store, pair, TriggeredByScan)
    assertEquals(Some(2L), diffsManager.lastRecordedVersion(pairRef))

    writer.reset()
    replayCorrelationStore(diffsManager, writer, store, pair, TriggeredByScan)
    assertEquals(Some(2L), diffsManager.lastRecordedVersion(pairRef))

  }
}