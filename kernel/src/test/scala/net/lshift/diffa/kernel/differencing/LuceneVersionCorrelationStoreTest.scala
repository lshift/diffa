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

import org.hibernate.cfg.Configuration
import org.junit.Assert._
import net.lshift.diffa.kernel.util.DateUtils._
import net.lshift.diffa.kernel.util.FullDateTimes._
import net.lshift.diffa.kernel.events._
import collection.mutable.{ListBuffer, HashMap}
import net.lshift.diffa.kernel.participants._
import net.lshift.diffa.kernel.indexing.{LuceneVersionCorrelationStore, LuceneVersionCorrelationStoreFactory}
import scala.collection.JavaConversions._
import org.apache.lucene.store.{MMapDirectory, FSDirectory, RAMDirectory}
import java.io.File
import org.junit.runner.RunWith
import org.junit.experimental.theories.{DataPoints, Theory, DataPoint, Theories}
import org.easymock.EasyMock
import org.joda.time.{LocalDate, DateTime}
import net.lshift.diffa.participant.scanning._
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.util.{CategoryChange, UpstreamEndpoint}
import net.lshift.diffa.kernel.config._
import org.junit.{Ignore, Before, Test}
import net.lshift.diffa.kernel.frontend.DomainPairDef

/**
 * Test cases for the Hibernate backed VersionCorrelationStore.
 */
@RunWith(classOf[Theories])
class LuceneVersionCorrelationStoreTest {
  import LuceneVersionCorrelationStoreTest._

  private val emptyAttributes:Map[String, TypedAttribute] = Map()
  private val emptyStrAttributes:Map[String, String] = Map()

  val log = LoggerFactory.getLogger(getClass)

  val store = stores(pair)
  val otherStore = stores(otherPair)
  val storeWithUnicodeOrder = stores(pairWithUnicodeOrder)

  @Before
  def cleanupStore {
    store.reset
    otherStore.reset
    storeWithUnicodeOrder.reset
  }

  @Test
  def matchedPairs = {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id1"), emptyAttributes, DEC_31_2009, "upstreamVsn", None)
    writer.storeDownstreamVersion(VersionID(pair, "id1"), emptyAttributes, DEC_31_2009, "upstreamVsn", "downstreamVsn", None)
    writer.flush()

    val unmatched = store.unmatchedVersions(Seq(), Seq(), None)
    assertEquals(0, unmatched.size)
  }

  @Test
  def rollbackChanges = {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id1"), emptyAttributes, DEC_31_2009, "upstreamVsn", None)
    writer.flush

    writer.storeDownstreamVersion(VersionID(pair, "id1"), emptyAttributes, DEC_31_2009, "upstreamVsn", "downstreamVsn", None)
    writer.rollback()

    val unmatched = store.unmatchedVersions(Seq(), Seq(), None)
    assertEquals(1, unmatched.size)
    assertEquals("id1", unmatched(0).id)
  }

  @Test
  def versionsShouldBeDeleteable = {
    val writer = store.openWriter()

    val id = VersionID(pair, "id1")

    writer.storeUpstreamVersion(id, emptyAttributes, DEC_31_2009, "uvsn", None)
    writer.flush

    def verifyUnmatched(expectation:Int, writer:ExtendedVersionCorrelationWriter) = {
      val unmatched = store.unmatchedVersions(Seq(), Seq(), None)
      assertEquals(expectation, unmatched.size)
    }

    verifyUnmatched(1, writer)

    writer.clearUpstreamVersion(id, None)
    writer.flush()
    verifyUnmatched(0, writer)

    writer.clearTombstones()

    verifyUnmatched(0, writer)
  }

  @Test
  def identicalVersionsShouldNotUpdateMaterialTimestamp {
    val writer = store.openWriter()

    val id = VersionID(pair, "id1")

    writer.storeUpstreamVersion(id, dateTimeAttributes, JUL_1_2010_1, "v1", None)

    val meaninglessUpdateTimestamp = JUL_1_2010_1.plusMinutes(1)

    writer.storeUpstreamVersion(id, dateTimeAttributes, meaninglessUpdateTimestamp , "v1", None)
    writer.flush()

    validateLastMaterialUpdate(id, JUL_1_2010_1)

    val meaningfulUpdateTimestamp1 = JUL_1_2010_1.plusMinutes(2)

    writer.storeUpstreamVersion(id, dateTimeAttributes, meaningfulUpdateTimestamp1 , "v2", None)
    writer.flush()

    validateLastMaterialUpdate(id, meaningfulUpdateTimestamp1)

    val meaningfulUpdateTimestamp2 = JUL_1_2010_1.plusMinutes(3)

    writer.storeUpstreamVersion(id, excludedByLaterDateTimeAttributes, meaningfulUpdateTimestamp2 , "v2", None)
    writer.flush()

    validateLastMaterialUpdate(id, meaningfulUpdateTimestamp2)

  }

  @Test
  def loadTest = {
    val writer = store.openWriter()

    val iterations = System.getProperty("lucene.loadtest.iterations","10000").toInt

    val start = System.currentTimeMillis()

    for (i <- 0 to iterations) {
      writer.storeUpstreamVersion(VersionID(pair, "id-" + i), dateTimeAttributes, JUL_1_2010_1, "v-" + i, None)
      if (i % 1000 == 0) {
        log.info("%sth iteration".format(i))
      }
    }
    writer.flush()

    val end = System.currentTimeMillis()

    val time = (end - start) / 1000

    log.info("Writer load test: %s s".format(time))
  }

  private def validateLastMaterialUpdate(id:VersionID, expected:DateTime) = {
    val c1 = store.retrieveCurrentCorrelation(id).get
    assertEquals(expected, c1.lastUpdate)
  }

  @Test
  def constrainedMatchedPairsWithDifferentCategories = {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id1"), dateTimeAttributes, JUL_1_2010_1, "upstreamVsn", None)
    writer.storeDownstreamVersion(VersionID(pair, "id1"), intAttributes, JUL_1_2010_1, "upstreamVsn", "downstreamVsn", None)
    writer.flush()

    val unmatched = store.unmatchedVersions(dateTimeConstraints, intConstraints, None)
    assertEquals(0, unmatched.size)
  }

  @Test
  def unmatchedPairFromUpstream = {
    val writer = store.openWriter()
    val timestamp = new DateTime()
    writer.storeUpstreamVersion(VersionID(pair, "id2"), emptyAttributes, DEC_31_2009, "upstreamVsn", None)
    writer.flush()

    val unmatched = store.unmatchedVersions(Seq(), Seq(), None)
    assertEquals(1, unmatched.size)
    assertCorrelationEquals(new Correlation(null, pair, "id2", emptyStrAttributes, emptyStrAttributes, DEC_31_2009, timestamp, "upstreamVsn", null, null, false), unmatched(0))
  }

  @Theory
  def constrainedAndIncludedUnmatchedPairFromUpstream(system:AttributeSystem) = {
    val timestamp = new DateTime()
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id2"), system.includedAttrs, DEC_31_2009, "upstreamVsn", None)
    writer.flush()

    val unmatched = store.unmatchedVersions(system.constraints, system.constraints, None)
    assertEquals(1, unmatched.size)
    assertCorrelationEquals(new Correlation(null, pair, "id2", system.includedStrAttrs, emptyStrAttributes, DEC_31_2009, timestamp, "upstreamVsn", null, null, false), unmatched(0))
  }

  @Theory
  def constrainedAndExcludedUnmatchedPairFromUpstream(system:AttributeSystem) = {
    val timestamp = new DateTime()
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id2"), system.excludedAttrs, DEC_31_2009, "upstreamVsn", None)
    writer.flush()

    val unmatched = store.unmatchedVersions(system.constraints, system.constraints, None)
    assertEquals(0, unmatched.size)
  }

  @Test
  def unmatchedPairFromUpstreamShouldBeIndicatedInReturnValue {
    val timestamp = new DateTime()
    val writer = store.openWriter()
    val corr = writer.storeUpstreamVersion(VersionID(pair, "id2"), emptyAttributes, DEC_31_2009, "upstreamVsn", None)
    writer.flush()

    assertCorrelationEquals(new Correlation(null, pair, "id2", emptyStrAttributes, emptyStrAttributes, DEC_31_2009, timestamp, "upstreamVsn", null, null, false), corr)
  }

  @Test
  def unmatchedPairFromDownstream = {
    val writer = store.openWriter()
    val timestamp = new DateTime()
    writer.storeDownstreamVersion(VersionID(pair, "id3"), emptyAttributes, DEC_31_2009, "upstreamVsn", "downstreamVsn", None)
    writer.flush()

    val unmatched = store.unmatchedVersions(Seq(), Seq(), None)
    assertEquals(1, unmatched.size)
    assertCorrelationEquals(new Correlation(null, pair, "id3", emptyStrAttributes, emptyStrAttributes, DEC_31_2009, timestamp,  null, "upstreamVsn", "downstreamVsn", false), unmatched(0))
  }

  @Test
  def unmatchedPairFromDownstreamShouldBeIndicatedInReturnValue {
    val timestamp = new DateTime()
    val writer = store.openWriter()
    val corr = writer.storeDownstreamVersion(VersionID(pair, "id3"), emptyAttributes, DEC_31_2009, "upstreamVsn", "downstreamVsn", None)
    writer.flush()
    assertCorrelationEquals(new Correlation(null, pair, "id3", emptyStrAttributes, emptyStrAttributes, DEC_31_2009, timestamp, null, "upstreamVsn", "downstreamVsn", false), corr)
  }

  @Test
  def matchedPairsAfterChanges = {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id4"), emptyAttributes, DEC_31_2009, "upstreamVsnA", None)
    writer.storeUpstreamVersion(VersionID(pair, "id4"), emptyAttributes, DEC_31_2009, "upstreamVsnB", None)
    writer.storeDownstreamVersion(VersionID(pair, "id4"), emptyAttributes, DEC_31_2009, "upstreamVsnA", "downstreamVsnA", None)
    writer.storeDownstreamVersion(VersionID(pair, "id4"), emptyAttributes, DEC_31_2009, "upstreamVsnB", "downstreamVsnB", None)
    writer.flush()

    val unmatched = store.unmatchedVersions(Seq(), Seq(), None)
    assertEquals(0, unmatched.size)
  }

  @Test
  def unmatchedPairsAfterChanges = {
    val timestamp = new DateTime()

    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id5"), emptyAttributes,DEC_31_2009, "upstreamVsnA", None)
    writer.storeDownstreamVersion(VersionID(pair, "id5"), emptyAttributes, DEC_31_2009, "upstreamVsnA", "downstreamVsnA", None)
    writer.storeUpstreamVersion(VersionID(pair, "id5"), emptyAttributes, DEC_31_2009, "upstreamVsnB", None)
    writer.flush()

    val unmatched = store.unmatchedVersions(Seq(), Seq(), None)
    assertEquals(1, unmatched.size)
    assertCorrelationEquals(new Correlation(null, pair, "id5", emptyStrAttributes, emptyStrAttributes, DEC_31_2009, timestamp, "upstreamVsnB", "upstreamVsnA", "downstreamVsnA", false), unmatched(0))
  }

  @Test
  def unmatchedPairsAfterUpstreamRemoved = {
    val timestamp = new DateTime()

    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id5"), emptyAttributes,DEC_31_2009, "upstreamVsnA", None)
    writer.storeDownstreamVersion(VersionID(pair, "id5"), emptyAttributes, DEC_31_2009, "upstreamVsnA", "downstreamVsnA", None)
    writer.clearUpstreamVersion(VersionID(pair, "id5"),  None)
    writer.flush()

    val unmatched = store.unmatchedVersions(Seq(), Seq(), None)
    assertEquals(1, unmatched.size)
    assertCorrelationEquals(new Correlation(null, pair, "id5", emptyStrAttributes, emptyStrAttributes, DEC_31_2009, timestamp, null, "upstreamVsnA", "downstreamVsnA", false), unmatched(0))
  }

  @Test
  def unmatchedPairsAfterDownstreamRemoved = {
    val timestamp = new DateTime()

    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id5"), emptyAttributes,DEC_31_2009, "upstreamVsnA", None)
    writer.storeDownstreamVersion(VersionID(pair, "id5"), emptyAttributes, DEC_31_2009, "upstreamVsnA", "downstreamVsnA", None)
    writer.clearDownstreamVersion(VersionID(pair, "id5"), None)
    writer.flush()

    val unmatched = store.unmatchedVersions(Seq(), Seq(), None)
    assertEquals(1, unmatched.size)
    assertCorrelationEquals(new Correlation(null, pair, "id5", emptyStrAttributes, emptyStrAttributes, DEC_31_2009, timestamp, "upstreamVsnA", null, null, false), unmatched(0))
  }

  @Test
  def unmatchedPairsAfterChangesShouldBeIndicatedInReturnValue = {
    val timestamp = new DateTime()

    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id5"), emptyAttributes, DEC_31_2009, "upstreamVsnA", None)
    writer.storeDownstreamVersion(VersionID(pair, "id5"), emptyAttributes, DEC_31_2009, "upstreamVsnA", "downstreamVsnA", None)
    val corr = writer.storeUpstreamVersion(VersionID(pair, "id5"), emptyAttributes, DEC_31_2009, "upstreamVsnB", None)
    writer.flush()

    assertCorrelationEquals(new Correlation(null, pair, "id5", emptyStrAttributes, emptyStrAttributes, DEC_31_2009, timestamp, "upstreamVsnB", "upstreamVsnA", "downstreamVsnA", false), corr)
  }

  @Test
  def deletingSource = {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id6"), bizDateTimeMap(DEC_1_2009), DEC_1_2009, "upstreamVsn-id6", None)
    writer.storeUpstreamVersion(VersionID(pair, "id7"), bizDateTimeMap(DEC_1_2009), DEC_1_2009, "upstreamVsn-id7", None)
    val corr = writer.clearUpstreamVersion(VersionID(pair, "id6"), None)
    writer.flush()
    assertCorrelationEquals(new Correlation(null, pair, "id6", null, null, null, null, null, null, null, true), corr)

    val collector = new Collector
    store.queryUpstreams(List(new TimeRangeConstraint("bizDateTime", DEC_1_2009, endOfDay(DEC_1_2009))), collector.collectUpstream)
    assertEquals(
      List(CollectedUpstreamDetail(VersionID(pair, "id7"), AttributesUtil.toUntypedMap(bizDateTimeMap(DEC_1_2009)), DEC_1_2009, "upstreamVsn-id7")),
      collector.upstreamObjs.toList)
  }

  @Theory
  def deletingSourceThatIsMatched(system:AttributeSystem) = {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id6"), system.includedAttrs, DEC_1_2009, "upstreamVsn-id6", None)
    writer.storeDownstreamVersion(VersionID(pair, "id6"), system.includedAttrs, DEC_1_2009, "upstreamVsn-id6", "downstreamVsn-id6", None)
    writer.clearUpstreamVersion(VersionID(pair, "id6"), None)
    writer.flush()

    val collector = new Collector
    store.queryUpstreams(List(new TimeRangeConstraint("bizDateTime", DEC_1_2009, endOfDay(DEC_1_2009))), collector.collectUpstream)
    assertEquals(0, collector.upstreamObjs.size)
  }

  @Test
  def deletingDest = {
    val writer1 = store.openWriter()
    writer1.storeDownstreamVersion(VersionID(pair, "id6"), bizDateTimeMap(DEC_1_2009), DEC_1_2009, "upstreamVsn-id6", "downstreamVsn-id6", None)
    writer1.storeDownstreamVersion(VersionID(pair, "id7"), bizDateTimeMap(DEC_1_2009), DEC_1_2009, "upstreamVsn-id7", "downstreamVsn-id7", None)
    writer1.flush()

    val writer2 = store.openWriter()
    val corr = writer2.clearDownstreamVersion(VersionID(pair, "id6"), None)
    writer2.flush()
    assertCorrelationEquals(new Correlation(null, pair, "id6", null, null, null, null, null, null, null, true), corr)

    val collector = new Collector
    val digests = store.queryDownstreams(List(new TimeRangeConstraint("bizDateTime", DEC_1_2009, endOfDay(DEC_1_2009))), collector.collectDownstream)
    assertEquals(
      List(CollectedDownstreamDetail(VersionID(pair, "id7"), AttributesUtil.toUntypedMap(bizDateTimeMap(DEC_1_2009)), DEC_1_2009, "upstreamVsn-id7", "downstreamVsn-id7")),
      collector.downstreamObjs.toList)
  }

  @Theory
  def deletingDestThatIsMatched(system:AttributeSystem) = {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id6"), system.includedAttrs, DEC_1_2009, "upstreamVsn-id6", None)
    writer.storeDownstreamVersion(VersionID(pair, "id6"), system.includedAttrs, DEC_1_2009, "upstreamVsn-id6", "downstreamVsn-id6", None)
    writer.clearDownstreamVersion(VersionID(pair, "id6"), None)
    writer.flush()

    val collector = new Collector
    store.queryDownstreams(List(new TimeRangeConstraint("bizDate", DEC_1_2009, endOfDay(DEC_1_2009))), collector.collectDownstream)
    assertEquals(0, collector.downstreamObjs.size)
  }

  @Theory
  def queryUpstreamRangeExcludesExcluded(system:AttributeSystem) = {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id1"), system.includedAttrs, DEC_31_2009, "upstreamVsn-id1", None)
    writer.storeUpstreamVersion(VersionID(pair, "id2"), system.excludedAttrs, DEC_31_2009, "upstreamVsn-id2", None)
    writer.flush()

    val collector = new Collector
    val digests = store.queryUpstreams(system.constraints, collector.collectUpstream)
    assertEquals(
      List(CollectedUpstreamDetail(VersionID(pair, "id1"), AttributesUtil.toUntypedMap(system.includedAttrs), DEC_31_2009, "upstreamVsn-id1")),
      collector.upstreamObjs.toList)
  }

  @Theory
  def queryDownstreamRangeExcludesExcluded(system:AttributeSystem) = {
    val writer = store.openWriter()
    writer.storeDownstreamVersion(VersionID(pair, "id1"), system.includedAttrs, DEC_31_2009, "upstreamVsn-id1", "downstreamVsn-id1", None)
    writer.storeDownstreamVersion(VersionID(pair, "id2"), system.excludedAttrs, DEC_31_2009, "upstreamVsn-id2", "downstreamVsn-id1", None)
    writer.flush()

    val collector = new Collector
    val digests = store.queryDownstreams(system.constraints, collector.collectDownstream)
    assertEquals(
      List(CollectedDownstreamDetail(VersionID(pair, "id1"), AttributesUtil.toUntypedMap(system.includedAttrs), DEC_31_2009, "upstreamVsn-id1", "downstreamVsn-id1")),
      collector.downstreamObjs.toList)
  }

  @Test
  def queryUpstreamRangeReturnsInIDOrderWithAsciiOrdering = {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id7"), bizDateTimeMap(DEC_2_2009), DEC_2_2009, "upstreamVsn-id7", None)
    writer.storeUpstreamVersion(VersionID(pair, "id6"), bizDateTimeMap(DEC_1_2009), DEC_1_2009, "upstreamVsn-id6", None)
    writer.flush()

    val collector = new Collector
    val digests = store.queryUpstreams(List(), collector.collectUpstream)
    assertEquals(
      List(
        CollectedUpstreamDetail(VersionID(pair, "id6"), AttributesUtil.toUntypedMap(bizDateTimeMap(DEC_1_2009)), DEC_1_2009, "upstreamVsn-id6"),
        CollectedUpstreamDetail(VersionID(pair, "id7"), AttributesUtil.toUntypedMap(bizDateTimeMap(DEC_2_2009)), DEC_2_2009, "upstreamVsn-id7")),
      collector.upstreamObjs.toList)
  }

  @Test
  def queryDownstreamRangeReturnsInIDOrderWithAsciiOrdering = {
    val writer = store.openWriter()
    writer.storeDownstreamVersion(VersionID(pair, "id7"), bizDateTimeMap(DEC_2_2009), DEC_2_2009, "upstreamVsn-id7", "downstreamVsn-id7", None)
    writer.storeDownstreamVersion(VersionID(pair, "id6"), bizDateTimeMap(DEC_1_2009), DEC_1_2009, "upstreamVsn-id6", "downstreamVsn-id6", None)
    writer.flush()

    val collector = new Collector
    val digests = store.queryDownstreams(List(), collector.collectDownstream)
    assertEquals(
      List(
        CollectedDownstreamDetail(VersionID(pair, "id6"), AttributesUtil.toUntypedMap(bizDateTimeMap(DEC_1_2009)), DEC_1_2009, "upstreamVsn-id6", "downstreamVsn-id6"),
        CollectedDownstreamDetail(VersionID(pair, "id7"), AttributesUtil.toUntypedMap(bizDateTimeMap(DEC_2_2009)), DEC_2_2009, "upstreamVsn-id7", "downstreamVsn-id7")),
      collector.downstreamObjs.toList)
  }

  @Test
  def queryUpstreamRangeCanReturnResultsWithUnicodeCollation = {
    val writer = storeWithUnicodeOrder.openWriter()
    writer.storeUpstreamVersion(VersionID(pairWithUnicodeOrder, "FooBarWithSuffix"), bizDateTimeMap(DEC_2_2009), DEC_2_2009, "upstreamVsn-id7", None)
    writer.storeUpstreamVersion(VersionID(pairWithUnicodeOrder, "foo"), bizDateTimeMap(DEC_1_2009), DEC_1_2009, "upstreamVsn-id6", None)
    writer.flush()

    val collector = new Collector
    val digests = storeWithUnicodeOrder.queryUpstreams(List(), collector.collectUpstream)
    assertEquals(
      List(
        CollectedUpstreamDetail(VersionID(pairWithUnicodeOrder, "foo"), AttributesUtil.toUntypedMap(bizDateTimeMap(DEC_1_2009)), DEC_1_2009, "upstreamVsn-id6"),
        CollectedUpstreamDetail(VersionID(pairWithUnicodeOrder, "FooBarWithSuffix"), AttributesUtil.toUntypedMap(bizDateTimeMap(DEC_2_2009)), DEC_2_2009, "upstreamVsn-id7")),
      collector.upstreamObjs.toList)
  }

  @Test
  def queryDownstreamRangeCanReturnResultsWithUnicodeCollation = {
    val writer = storeWithUnicodeOrder.openWriter()
    writer.storeDownstreamVersion(VersionID(pairWithUnicodeOrder, "foo"), bizDateTimeMap(DEC_1_2009), DEC_1_2009, "upstreamVsn-id6", "downstreamVsn-id6", None)
    writer.storeDownstreamVersion(VersionID(pairWithUnicodeOrder, "FooBarWithSuffix"),bizDateTimeMap(DEC_2_2009), DEC_2_2009, "upstreamVsn-id7", "downstreamVsn-id7", None)
    writer.flush()

    val collector = new Collector
    val digests = storeWithUnicodeOrder.queryDownstreams(List(), collector.collectDownstream)
    assertEquals(
      List(
        CollectedDownstreamDetail(VersionID(pairWithUnicodeOrder, "foo"), AttributesUtil.toUntypedMap(bizDateTimeMap(DEC_1_2009)), DEC_1_2009, "upstreamVsn-id6", "downstreamVsn-id6"),
        CollectedDownstreamDetail(VersionID(pairWithUnicodeOrder, "FooBarWithSuffix"), AttributesUtil.toUntypedMap(bizDateTimeMap(DEC_2_2009)), DEC_2_2009, "upstreamVsn-id7", "downstreamVsn-id7")),
      collector.downstreamObjs.toList)
  }

  @Test
  def storedUpstreamShouldBeRetrievable = {
    val timestamp = new DateTime()
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id23"), emptyAttributes, DEC_1_2009, "upstreamVsn-id23", None)
    writer.flush()
    val corr = store.retrieveCurrentCorrelation(VersionID(pair, "id23")).getOrElse(null)

    assertCorrelationEquals(
      new Correlation(null, pair, "id23", emptyStrAttributes, null, DEC_1_2009, timestamp, "upstreamVsn-id23", null, null, false),
      corr)
  }

  @Test
  def storedDownstreamShouldBeRetrievable = {
    val timestamp = new DateTime()
    val writer = store.openWriter()
    writer.storeDownstreamVersion(VersionID(pair, "id23"), emptyAttributes, DEC_1_2009, "upstreamVsn-id23", "downstreamVsn-id23", None)
    writer.flush()
    val corr = store.retrieveCurrentCorrelation(VersionID(pair, "id23")).getOrElse(null)

    assertCorrelationEquals(
      new Correlation(null, pair, "id23", null, emptyStrAttributes, DEC_1_2009, timestamp, null, "upstreamVsn-id23", "downstreamVsn-id23", false),
      corr)
  }

  @Test
  def storedMatchShouldBeRetrievable = {
    val timestamp = new DateTime()
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id23"), emptyAttributes, DEC_1_2009, "upstreamVsn-id23", None)
    writer.storeDownstreamVersion(VersionID(pair, "id23"), emptyAttributes, DEC_1_2009, "upstreamVsn-id23", "downstreamVsn-id23", None)
    writer.flush()
    val corr = store.retrieveCurrentCorrelation(VersionID(pair, "id23")).getOrElse(null)

    assertCorrelationEquals(
      new Correlation(null, pair, "id23", emptyStrAttributes, emptyStrAttributes, DEC_1_2009, timestamp, "upstreamVsn-id23", "upstreamVsn-id23", "downstreamVsn-id23", true),
      corr)
  }

  @Test
  def unknownCorrelationShouldNotBeRetrievable = {
    val corr = store.retrieveCurrentCorrelation(VersionID(pair, "id99-missing"))
    assertEquals(None, corr)
  }

  @Test
  def storesMustBeIsolatedByPairKey = {
    val writer = store.openWriter()
    val otherWriter = otherStore.openWriter()

    otherWriter.storeUpstreamVersion(VersionID(otherPair, "123456789"), emptyAttributes, DEC_1_2009, "up-123456789", None)
    otherWriter.storeDownstreamVersion(VersionID(otherPair, "123456789"), emptyAttributes, DEC_1_2009, "up-123456789", "down-123456789", None)
    otherWriter.flush()

    assertCorrelationEquals(
      new Correlation(null, otherPair, "123456789", Map[String,String](), Map[String,String](), DEC_1_2009, null, "up-123456789", "up-123456789", "down-123456789", true),
      otherStore.retrieveCurrentCorrelation(VersionID(otherPair, "123456789")).getOrElse(null))

    writer.storeUpstreamVersion(VersionID(pair, "123456789"), emptyAttributes, DEC_1_2009, "up-987654321", None)
    writer.flush()
    assertCorrelationEquals(
      new Correlation(null, pair, "123456789", Map[String,String](), Map[String,String](), DEC_1_2009, null, "up-987654321", null, null, false),
      store.retrieveCurrentCorrelation(VersionID(pair, "123456789")).getOrElse(null))

    // re-check other store
    assertCorrelationEquals(
      new Correlation(null, otherPair, "123456789", Map[String,String](), Map[String,String](), DEC_1_2009, null, "up-123456789", "up-123456789", "down-123456789", true),
      otherStore.retrieveCurrentCorrelation(VersionID(otherPair, "123456789")).getOrElse(null))
  }

  @Test
  def flushingWriterMustClearBuffers {
    val writer = store.openWriter()
    assertFalse(writer.isDirty)
    writer.storeUpstreamVersion(VersionID(pair, "id23"), emptyAttributes, DEC_1_2009, "upstreamVsn-id23", None)
    assertTrue(writer.isDirty)
    writer.storeDownstreamVersion(VersionID(pair, "id23"), emptyAttributes, DEC_1_2009, "upstreamVsn-id23", "downstreamVsn-id23", None)
    assertTrue(writer.isDirty)
    writer.flush()
    assertFalse(writer.isDirty)
    writer.clearUpstreamVersion(VersionID(pair, "id23"), None)
    assertTrue(writer.isDirty)
    writer.flush()
    assertFalse(writer.isDirty)
  }

  @Test
  def writerMustFlushWhenMaxBufferSizeIsReached {
    val writer = store.openWriter()
    assertFalse(writer.isDirty)
    for (i <- 1 to 9999) {
      writer.storeUpstreamVersion(VersionID(pair, "id" + i), emptyAttributes, DEC_1_2009, "upstreamVsn-id" + i, None)
      assertTrue(writer.isDirty)
    }
    writer.storeUpstreamVersion(VersionID(pair, "id10000"), emptyAttributes, DEC_1_2009, "upstreamVsn-id10000", None)
    // should be flushed implicitly at this point
    assertFalse(writer.isDirty)
  }

  @Test
  def storeShouldClearWhenRemoved = {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id1"), emptyAttributes, DEC_31_2009, "upstreamVsn", None)
    writer.storeDownstreamVersion(VersionID(pair, "id2"), emptyAttributes, DEC_31_2009, "upstreamVsn", "downstreamVsn", None)
    writer.flush()
    assertEquals(2, store.unmatchedVersions(Seq(), Seq(), None).length)

    stores.remove(pair)
    val reopenedStore = stores(pair)

    assertEquals(0, reopenedStore.unmatchedVersions(Seq(), Seq(), None).length)
  }

  @Test
  def shouldAllowCategoriesToBeAddedWhenEmpty() {
    store.ensureUpgradeable(UpstreamEndpoint,
      Seq(CategoryChange("newSet", None, Some(new SetCategoryDescriptor(Set("aaa"))))))
  }

  @Test
  def shouldPreventAddingCategoryWhenDataExists() {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id1"), emptyAttributes, DEC_31_2009, "upstreamVsn", None)
    writer.flush()

    try {
      store.ensureUpgradeable(UpstreamEndpoint,
        Seq(CategoryChange("newSet", None, Some(new SetCategoryDescriptor(Set("aaa"))))))
      fail("Expected IncompatibleCategoryChangeException")
    } catch {
      case e:IncompatibleCategoryChangeException =>
        assertEquals(
          "Change to category newSet is not allowed: Cannot add a category as existing data is stored for pair DiffaPairRef(pair,domain)",
          e.getMessage)
    }
  }

  @Test
  def shouldAllowAdditionOfSetCategoryValue() {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id1"), Map("someSet" -> StringAttribute("aaa")), DEC_31_2009, "upstreamVsn", None)
    writer.flush()

    store.ensureUpgradeable(UpstreamEndpoint,
      Seq(CategoryChange("someSet",
        Some(new SetCategoryDescriptor(Set("aaa"))),
        Some(new SetCategoryDescriptor(Set("aaa", "bbb"))))))
  }

  @Test
  def shouldNotAllowRemovalOfUsedSetCategoryValue() {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id1"), Map("someSet" -> StringAttribute("aaa")), DEC_31_2009, "upstreamVsn", None)
    writer.flush()

    try {
      store.ensureUpgradeable(UpstreamEndpoint,
        Seq(CategoryChange("someSet",
          Some(new SetCategoryDescriptor(Set("aaa", "bbb"))),
          Some(new SetCategoryDescriptor(Set("bbb"))))))
      fail("Expected IncompatibleCategoryChangeException")
    } catch {
      case e:IncompatibleCategoryChangeException =>
        assertEquals(
          "Change to category someSet is not allowed: Updated category bounds do not cover all stored values for pair DiffaPairRef(pair,domain)",
          e.getMessage)
    }
  }

  @Test
  def shouldAllowRemovalOfUnusedSetCategoryValue() {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id1"), Map("someSet" -> StringAttribute("aaa")), DEC_31_2009, "upstreamVsn", None)
    writer.flush()

    store.ensureUpgradeable(UpstreamEndpoint,
      Seq(CategoryChange("someSet",
        Some(new SetCategoryDescriptor(Set("aaa", "bbb"))),
        Some(new SetCategoryDescriptor(Set("aaa"))))))
  }

  @Test
  def shouldAllowChangeOfAttributeTypeWhenDataIsNotPresent() {
    store.ensureUpgradeable(UpstreamEndpoint,
      Seq(CategoryChange("someSet",
        Some(new SetCategoryDescriptor(Set("aaa", "bbb"))),
        Some(new RangeCategoryDescriptor("date")))))
  }

  @Test
  def shouldNotAllowChangeOfAttributeTypeWhenDataIsPresent() {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id1"), Map("someSet" -> StringAttribute("aaa")), DEC_31_2009, "upstreamVsn", None)
    writer.flush()

    try {
      store.ensureUpgradeable(UpstreamEndpoint,
        Seq(CategoryChange("someSet",
          Some(new SetCategoryDescriptor(Set("aaa", "bbb"))),
          Some(new RangeCategoryDescriptor("date")))))
      fail("Expected IncompatibleCategoryChangeException")
    } catch {
      case e:IncompatibleCategoryChangeException =>
        assertEquals(
          "Change to category someSet is not allowed: Cannot change category type as existing data is stored for pair DiffaPairRef(pair,domain)",
          e.getMessage)
    }
  }

  @Test
  def shouldNotAllowChangeOfRangeAttributeDataTypeWhenDataIsPresent() {
    val writer = store.openWriter()
    writer.storeUpstreamVersion(VersionID(pair, "id1"),
      Map("someDate" -> DateAttribute(DEC_31_2009.toLocalDate)), DEC_31_2009, "upstreamVsn", None)
    writer.flush()

    try {
      store.ensureUpgradeable(UpstreamEndpoint,
        Seq(CategoryChange("someDate",
          Some(new RangeCategoryDescriptor("date")),
          Some(new RangeCategoryDescriptor("datetime")))))
      fail("Expected IncompatibleCategoryChangeException")
    } catch {
      case e:IncompatibleCategoryChangeException =>
        assertEquals(
          "Change to category someDate is not allowed: Cannot change category type as existing data is stored for pair DiffaPairRef(pair,domain)",
          e.getMessage)
    }
  }

  private def assertCorrelationEquals(expected:Correlation, actual:Correlation) {
    if (expected == null) {
      assertNull(actual)
    } else {
      assertNotNull(actual)

      assertEquals(expected.id, actual.id)
      assertEquals(expected.pairing, actual.pairing)
      assertEquals(expected.upstreamVsn, actual.upstreamVsn)
      assertEquals(expected.downstreamUVsn, actual.downstreamUVsn)
      assertEquals(expected.downstreamDVsn, actual.downstreamDVsn)
      assertEquals(expected.upstreamAttributes, actual.upstreamAttributes)
      assertEquals(expected.downstreamAttributes, actual.downstreamAttributes)
      assertEquals(expected.isMatched, actual.isMatched)
    }
  }
}

case class CollectedUpstreamDetail(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, vsn:String)
case class CollectedDownstreamDetail(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, uvsn:String, dvsn:String)
class Collector {
  val upstreamObjs = new ListBuffer[CollectedUpstreamDetail]
  val downstreamObjs = new ListBuffer[CollectedDownstreamDetail]

  def collectUpstream(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, vsn:String) = {
    upstreamObjs += CollectedUpstreamDetail(id, attributes, lastUpdate, vsn)
  }
  def collectDownstream(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, uvsn:String, dvsn:String) = {
    downstreamObjs += CollectedDownstreamDetail(id, attributes, lastUpdate, uvsn, dvsn)
  }
}

object LuceneVersionCorrelationStoreTest {

  val domainName = "domain"
  val pair = DiffaPairRef(key="pair",domain=domainName)
  val otherPair = DiffaPairRef(key="other-pair",domain=domainName)
  val pairWithUnicodeOrder = DiffaPairRef(key="pair-with-unicode-ordering",domain=domainName)

  val dummyConfigStore = EasyMock.createMock(classOf[SystemConfigStore])

  val dummyDomainConfigStore = EasyMock.createMock(classOf[DomainConfigStore])


  EasyMock.expect(dummyConfigStore.
    maybeSystemConfigOption(VersionCorrelationStore.schemaVersionKey)).
    andStubReturn(Some(VersionCorrelationStore.currentSchemaVersion.toString))

  Map(pair -> AsciiCollationOrdering.name,
    pairWithUnicodeOrder -> UnicodeCollationOrdering.name).foreach {
    case (pair, collation) =>
    EasyMock.expect(dummyDomainConfigStore.getPairDef(pair)
    ).andStubReturn(
      DomainPairDef(
        key = pair.key,
        domain = domainName,
        upstreamName = "%s-dummyUpstream".format(pair.key),
        downstreamName = "%s-dummyDownstream".format(pair.key))
    )
    Seq( "dummyUpstream", "dummyDownstream").foreach { sideName: String =>
      EasyMock.expect(dummyDomainConfigStore.getEndpoint(domainName, "%s-%s".format(pair.key, sideName))
      ).andStubReturn(Endpoint(collation=collation))
    }
  }

  EasyMock.replay(dummyConfigStore)

  EasyMock.replay(dummyDomainConfigStore)

  val dummyDiagnostics = EasyMock.createNiceMock(classOf[DiagnosticsManager])
  EasyMock.replay(dummyDiagnostics)

  val stores = new LuceneVersionCorrelationStoreFactory("target", dummyConfigStore,
    dummyDomainConfigStore, dummyDiagnostics)

  // Helper methods for various constraint/attribute scenarios
  def bizDateTimeSeq(d:DateTime) = Seq(d.toString())
  def bizDateTimeMap(d:DateTime) = Map("bizDateTime" -> DateTimeAttribute(d))
  def bizDateSeq(d:LocalDate) = Seq(d.toString())
  def bizDateMap(d:LocalDate) = Map("bizDate" -> DateAttribute(d))
  def intMap(i:Int) = Map("someInt" -> IntegerAttribute(i))
  def stringMap(s:String) = Map("someString" -> StringAttribute(s))

  // Standard attribute/constraint definitions
  private val dateTimeAttributes = bizDateTimeMap(JUL_1_2010_1)
  private val excludedByEarlierDateTimeAttributes = bizDateTimeMap(FEB_15_2010)
  private val excludedByLaterDateTimeAttributes = bizDateTimeMap(AUG_11_2010_1)
  private val dateTimeConstraints = Seq(new TimeRangeConstraint("bizDateTime", JUL_2010, END_JUL_2010))
  private val unboundedLowerDateTimeConstraint = Seq(new TimeRangeConstraint("bizDateTime", null, END_JUL_2010))
  private val unboundedUpperDateTimeConstraint = Seq(new TimeRangeConstraint("bizDateTime", JUL_2010, null))

  private val dateAttributes = bizDateMap(JUL_1_2010.toLocalDate)
  private val excludedByEarlierDateAttributes = bizDateMap(FEB_15_2010.toLocalDate)
  private val excludedByLaterDateAttributes = bizDateMap(AUG_11_2010.toLocalDate)
  private val dateConstraints = Seq(new DateRangeConstraint("bizDate", JUL_1_2010.toLocalDate, JUL_31_2010.toLocalDate))
  private val unboundedLowerDateConstraint = Seq(new DateRangeConstraint("bizDate", null, JUL_31_2010.toLocalDate))
  private val unboundedUpperDateConstraint = Seq(new DateRangeConstraint("bizDate", JUL_1_2010.toLocalDate, null))

  private val intAttributes = intMap(2500)
  private val excludedIntAttributes = intMap(20000)
  private val intConstraints = Seq(new IntegerRangeConstraint("someInt", 2000, 2999))
  private val stringAttributes = stringMap("abc")
  private val excludedStringAttributes = stringMap("def")
  private val stringConstraints = Seq(new StringPrefixConstraint("someString", "ab"))
  private val setConstraints = Seq(new SetConstraint("someString", Set("abc","abc123","abcdef")))

  // Defines a testable combination of constraints/attributes the store should be able to handle
  case class AttributeSystem(constraints:Seq[ScanConstraint], includedAttrs:Map[String, TypedAttribute], excludedAttrs:Map[String, TypedAttribute]) {
    def includedStrAttrs = includedAttrs.map { case (k, v) => k -> v.value }.toMap
    def excludedStrAttrs = excludedAttrs.map { case (k, v) => k -> v.value }.toMap
  }

  @DataPoints def dateTimes = Array(
    AttributeSystem(dateTimeConstraints, dateTimeAttributes, excludedByLaterDateTimeAttributes),
    AttributeSystem(dateTimeConstraints, dateTimeAttributes, excludedByEarlierDateTimeAttributes)
  )
  @DataPoints def unboundedDateTimes = Array(
    AttributeSystem(unboundedLowerDateTimeConstraint, dateTimeAttributes, excludedByLaterDateTimeAttributes),
    AttributeSystem(unboundedUpperDateTimeConstraint, dateTimeAttributes, excludedByEarlierDateTimeAttributes)
  )
  @DataPoints def dates = Array(
    AttributeSystem(dateConstraints, dateAttributes, excludedByLaterDateAttributes),
    AttributeSystem(dateConstraints, dateAttributes, excludedByEarlierDateAttributes)
  )
  @DataPoints def unboundedDates = Array(
    AttributeSystem(unboundedLowerDateConstraint, dateAttributes, excludedByLaterDateAttributes),
    AttributeSystem(unboundedUpperDateConstraint, dateAttributes, excludedByEarlierDateAttributes)
  )
  @DataPoint def ints = AttributeSystem(intConstraints, intAttributes, excludedIntAttributes)
  @DataPoint def strings = AttributeSystem(stringConstraints, stringAttributes, excludedStringAttributes)
  @DataPoint def set = AttributeSystem(setConstraints, stringAttributes, excludedStringAttributes)
  @DataPoints def setAndDateTimes = Array(
    AttributeSystem(dateTimeConstraints ++ setConstraints, dateTimeAttributes ++ stringAttributes, excludedByLaterDateTimeAttributes ++ excludedStringAttributes),
    AttributeSystem(dateTimeConstraints ++ setConstraints, dateTimeAttributes ++ stringAttributes, dateTimeAttributes ++ excludedStringAttributes),
    AttributeSystem(dateTimeConstraints ++ setConstraints, dateTimeAttributes ++ stringAttributes, excludedByLaterDateTimeAttributes ++ stringAttributes)
  )
}
