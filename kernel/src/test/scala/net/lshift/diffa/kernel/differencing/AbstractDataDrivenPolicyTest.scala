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

import scala.collection.JavaConversions._
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.util.EasyMockScalaUtils._
import org.apache.commons.codec.digest.DigestUtils
import net.lshift.diffa.kernel.participants._
import net.lshift.diffa.kernel.participants.IntegerCategoryFunction._
import org.junit.runner.RunWith
import net.lshift.diffa.kernel.util.FullDateTimes._
import net.lshift.diffa.kernel.util.SimpleDates._
import net.lshift.diffa.kernel.util.ConvenienceDateTimes._
import org.junit.experimental.theories.{Theory, Theories, DataPoint}
import org.easymock.{IAnswer, EasyMock}
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.config._
import org.joda.time.{LocalDate, DateTime}
import concurrent.SyncVar
import net.lshift.diffa.kernel.util.NonCancellingFeedbackHandle
import net.lshift.diffa.participant.scanning._
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import org.junit.Assume._
import java.util.HashMap
import net.lshift.diffa.kernel.config.{Pair => DiffaPair}
import net.lshift.diffa.kernel.config.system.SystemConfigStore

/**
 * Framework and scenario definitions for data-driven policy tests.
 */
@RunWith(classOf[Theories])
abstract class AbstractDataDrivenPolicyTest {
  import AbstractDataDrivenPolicyTest._

  // The policy instance under test
  protected def policy:VersionPolicy

  // The various mocks for listeners and participants
  val usMock = createStrictMock("us", classOf[UpstreamParticipant])
  val dsMock = createStrictMock("ds", classOf[DownstreamParticipant])
  EasyMock.checkOrder(usMock, false)   // Not all participant operations are going to be strictly ordered
  EasyMock.checkOrder(dsMock, false)   // Not all participant operations are going to be strictly ordered

  val nullListener = new NullDifferencingListener
  val diagnostics = createStrictMock("diagnostics", classOf[DiagnosticsManager])

  val writer = createMock("writer", classOf[LimitedVersionCorrelationWriter])
  val extendedWriter = createMock("extendedWriter", classOf[ExtendedVersionCorrelationWriter])
  val store = createMock("versionStore", classOf[VersionCorrelationStore])
  val stores = new VersionCorrelationStoreFactory {
    def apply(pair: DiffaPairRef) = store
    def remove(pair: DiffaPairRef) {}
    def close(pair: DiffaPairRef) {}
    def close {}
  }

  val feedbackHandle = new NonCancellingFeedbackHandle

  val listener = createStrictMock("listener", classOf[DifferencingListener])
  EasyMock.checkOrder(listener, false)   // Not all participant operations are going to be strictly ordered

  val diffWriter = createStrictMock("diffWriter", classOf[DifferenceWriter])
  EasyMock.checkOrder(diffWriter, false)  // Not all match write operations are going to be strictly ordered

  val systemConfigStore = createStrictMock("configStore", classOf[SystemConfigStore])

  protected def replayAll = replay(systemConfigStore, usMock, dsMock, store, writer, listener)
  protected def verifyAll = verify(systemConfigStore, usMock, dsMock, store, writer, listener)

  /**
   * Scenario with the top levels matching. The policy should not progress any further than the top level.
   */
  @Theory
  def shouldStopAtTopLevelWhenTopLevelBucketsMatch(scenario:Scenario) {
    setupStubs(scenario)
    assumeTrue(scenario.tx.forall(_.isInstanceOf[AggregateTx]))     // Only relevant in scenarios where aggregation occurs

    scenario.tx.foreach { case tx:AggregateTx =>
      expectUpstreamAggregateScan(scenario.pair.asRef, tx.bucketing, tx.constraints, tx.respBuckets, tx.respBuckets)
      expectDownstreamAggregateScan(scenario.pair.asRef, tx.bucketing, tx.constraints, tx.respBuckets, tx.respBuckets)
    }

    replayAll

    policy.scanUpstream(scenario.pair.asRef, scenario.upstreamEp, None, writer, usMock, nullListener, feedbackHandle)
    policy.scanDownstream(scenario.pair.asRef, scenario.downstreamEp, None, writer, usMock, dsMock, listener, feedbackHandle)

    verifyAll
  }

  /**
   * Scenario with the store not any content for either half. Policy should run top-level, then jump directly
   * to the individual level.
   */
  @Theory
  def shouldJumpToLowestLevelsStraightAfterTopWhenStoreIsEmpty(scenario:Scenario) {
    setupStubs(scenario)
    assumeTrue(scenario.tx.forall(_.isInstanceOf[AggregateTx]))     // Only relevant in scenarios where aggregation occurs

    scenario.tx.foreach { case tx:AggregateTx =>
      expectUpstreamAggregateScan(scenario.pair.asRef, tx.bucketing, tx.constraints, tx.respBuckets, Seq())
      tx.respBuckets.foreach(b => {
        expectUpstreamEntityScan(scenario.pair.asRef, b.nextTx.constraints, b.allVsns, Seq())
        expectUpstreamEntityStore(scenario.pair.asRef, b.allVsns, false)
      })

      expectDownstreamAggregateScan(scenario.pair.asRef, tx.bucketing, tx.constraints, tx.respBuckets, Seq())
      tx.respBuckets.foreach(b => {
        expectDownstreamEntityScan(scenario.pair.asRef, b.nextTx.constraints, b.allVsns, Seq())
        expectDownstreamEntityStore(scenario.pair.asRef, b.allVsns, false)
      })
    }

    replayAll

    policy.scanUpstream(scenario.pair.asRef, scenario.upstreamEp, None, writer, usMock, nullListener, feedbackHandle)
    policy.scanDownstream(scenario.pair.asRef, scenario.downstreamEp, None, writer, usMock, dsMock, listener, feedbackHandle)

    verifyAll
  }

  /**
   * Scenario with the store being out-of-date for a upstream leaf-node.
   */
  @Theory
  def shouldCorrectOutOfDateUpstreamEntity(scenario:Scenario) {
    setupStubs(scenario)

    scenario.tx.foreach { tx =>
      // Alter the version of the first entity in the upstream tree, then expect traversal to it
      val updated = tx.alterFirstVsn("newVsn1")

      traverseFirstBranch(updated, tx) {
        case (tx1:AggregateTx, tx2:AggregateTx) =>
          expectUpstreamAggregateScan(scenario.pair.asRef, tx1.bucketing, tx1.constraints, tx1.respBuckets, tx2.respBuckets)
        case (tx1:EntityTx, tx2:EntityTx) =>
          expectUpstreamEntityScan(scenario.pair.asRef, tx1.constraints, tx1.entities, tx2.entities)
      }
      expectUpstreamEntityStore(scenario.pair.asRef, Seq(updated.firstVsn), true)

      // Expect to see an event about the version being matched (since we told the datastore to report it as matched)
      listener.onMatch(VersionID(scenario.pair.asRef, updated.firstVsn.id), updated.firstVsn.vsn, TriggeredByScan)

      tx match {
        case atx:AggregateTx =>
          // Expect only a top-level scan on the downstream
          expectDownstreamAggregateScan(scenario.pair.asRef, atx.bucketing, atx.constraints, atx.respBuckets, atx.respBuckets)
        case etx:EntityTx =>
          // Expect entity-query, since we can't aggregate anyway
          expectDownstreamEntityScan(scenario.pair.asRef, etx.constraints, etx.entities, etx.entities)
      }
    }

    replayAll

    policy.scanUpstream(scenario.pair.asRef, scenario.upstreamEp, None, writer, usMock, nullListener, feedbackHandle)
    policy.scanDownstream(scenario.pair.asRef, scenario.downstreamEp, None, writer, usMock, dsMock, listener, feedbackHandle)

    verifyAll
  }

  /**
   * Scenario with the store being out-of-date for a downstream leaf-node.
   */
  @Theory
  def shouldCorrectOutOfDateDownstreamEntity(scenario:Scenario) {
    setupStubs(scenario)

    scenario.tx.foreach { tx =>
      tx match {
        case atx:AggregateTx =>
          // Expect only a top-level scan on the upstream
          expectUpstreamAggregateScan(scenario.pair.asRef, atx.bucketing, atx.constraints, atx.respBuckets, atx.respBuckets)
        case etx:EntityTx =>
          // Expect entity-query, since we can't aggregate anyway
          expectUpstreamEntityScan(scenario.pair.asRef, etx.constraints, etx.entities, etx.entities)
      }


      // Alter the version of the first entity in the downstream tree, then expect traversal to it
      val updated = tx.alterFirstVsn("newVsn1")
      traverseFirstBranch(updated, tx) {
        case (tx1:AggregateTx, tx2:AggregateTx) =>
          expectDownstreamAggregateScan(scenario.pair.asRef, tx1.bucketing, tx1.constraints, tx1.respBuckets, tx2.respBuckets)
        case (tx1:EntityTx, tx2:EntityTx) =>
          expectDownstreamEntityScan(scenario.pair.asRef, tx1.constraints, tx1.entities, tx2.entities)
      }
      expectDownstreamEntityStore(scenario.pair.asRef, Seq(updated.firstVsn), true)

      // Expect to see an event about the version being matched (since we told the datastore to report it as matched)
      listener.onMatch(VersionID(scenario.pair.asRef, updated.firstVsn.id), updated.firstVsn.vsn, TriggeredByScan)
    }

    replayAll

    policy.scanUpstream(scenario.pair.asRef, scenario.upstreamEp, None, writer, usMock, nullListener, feedbackHandle)
    policy.scanDownstream(scenario.pair.asRef, scenario.downstreamEp, None, writer, usMock, dsMock, listener, feedbackHandle)

    verifyAll
  }


  //
  // Helpers
  //

  protected def setupStubs(scenario:Scenario) {
    expect(systemConfigStore.getPair(scenario.pair.domain.name, scenario.pair.key)).andReturn(scenario.pair).anyTimes
  }

  protected def expectUpstreamAggregateScan(pair:DiffaPairRef, bucketing:Seq[CategoryFunction], constraints:Seq[ScanConstraint],
                                            partResp:Seq[Bucket], storeResp:Seq[Bucket]) {
    expect(usMock.scan(asUnorderedList(constraints), asUnorderedList(bucketing))).andReturn(participantDigestResponse(partResp))
    store.queryUpstreams(asUnorderedList(constraints), anyUnitF4)
      expectLastCall[Unit].andAnswer(UpstreamVersionAnswer(pair, storeResp))
  }
  protected def expectDownstreamAggregateScan(pair:DiffaPairRef, bucketing:Seq[CategoryFunction], constraints:Seq[ScanConstraint],
                                              partResp:Seq[Bucket], storeResp:Seq[Bucket]) {
    expect(dsMock.scan(asUnorderedList(constraints), asUnorderedList(bucketing))).andReturn(participantDigestResponse(partResp))
    store.queryDownstreams(asUnorderedList(constraints), anyUnitF5)
      expectLastCall[Unit].andAnswer(DownstreamVersionAnswer(pair, storeResp))
  }

  protected def expectUpstreamEntityScan(pair:DiffaPairRef, constraints:Seq[ScanConstraint], partResp:Seq[Vsn], storeResp:Seq[Vsn]) {
    expect(usMock.scan(asUnorderedList(constraints), EasyMock.eq(Seq()))).andReturn(participantEntityResponse(partResp))
    val correlations = storeResp.map(v=> {
      Correlation(id = v.id, upstreamAttributes = v.strAttrs, lastUpdate = v.lastUpdated, upstreamVsn = v.vsn)
    })

    expect(store.queryUpstreams(asUnorderedList(constraints))).andReturn(correlations)
  }
  protected def expectDownstreamEntityScan(pair:DiffaPairRef, constraints:Seq[ScanConstraint], partResp:Seq[Vsn], storeResp:Seq[Vsn]) {
    expect(dsMock.scan(asUnorderedList(constraints), EasyMock.eq(Seq()))).andReturn(participantEntityResponse(partResp))
    val correlations = storeResp.map(v=> {
      Correlation(id = v.id, downstreamAttributes = v.strAttrs, lastUpdate = v.lastUpdated, downstreamDVsn = v.vsn)
    })

    expect(store.queryDownstreams(asUnorderedList(constraints))).andReturn(correlations)
  }

  protected def expectUpstreamEntityStore(pair:DiffaPairRef, entities:Seq[Vsn], matched:Boolean) {
    entities.foreach(v => {
      val downstreamVsnToUse = if (matched) { v.vsn } else { null }   // If we're matched, make the vsn match

      expect(writer.storeUpstreamVersion(VersionID(pair, v.id), v.typedAttrs, v.lastUpdated, v.vsn)).
        andReturn(new Correlation(null, pair, v.id, v.strAttrs, null, v.lastUpdated, new DateTime, v.vsn, downstreamVsnToUse, downstreamVsnToUse, matched))
    })
  }
  protected def expectDownstreamEntityStore(pair:DiffaPairRef, entities:Seq[Vsn], matched:Boolean) {
    entities.foreach(v => {
      val upstreamVsnToUse = if (matched) { v.vsn } else { null }   // If we're matched, make the vsn match

      expect(writer.storeDownstreamVersion(VersionID(pair, v.id), v.typedAttrs, v.lastUpdated, v.vsn, v.vsn)).
        andReturn(new Correlation(null, pair, v.id, null, v.strAttrs, v.lastUpdated, new DateTime, upstreamVsnToUse, v.vsn, v.vsn, matched))
    })
  }

  protected def participantDigestResponse(buckets:Seq[Bucket]):Seq[ScanResultEntry] =
    buckets.map(b => ScanResultEntry.forAggregate(b.vsn, b.attrs))
  protected def participantEntityResponse(entities:Seq[Vsn]):Seq[ScanResultEntry] =
    entities.map(e => ScanResultEntry.forEntity(e.id, e.vsn, e.lastUpdated, e.strAttrs))

  protected abstract class VersionAnswer[T] extends IAnswer[Unit] {
    def res:Seq[Bucket]

    def answer {
      val args = EasyMock.getCurrentArguments
      val cb = args(1).asInstanceOf[T]

      // Answer with entities from each bucket's children
      answerEntities(res.flatMap(b => b.allVsns), cb)
    }

    def answerEntities(entities:Seq[Vsn], cb:T):Unit
  }

  protected case class UpstreamVersionAnswer(pair:DiffaPairRef, res:Seq[Bucket])
      extends VersionAnswer[Function4[VersionID, Map[String, String], DateTime, String, Unit]] {
    def answerEntities(entities:Seq[Vsn], cb:Function4[VersionID, Map[String, String], DateTime, String, Unit]) {
      entities.foreach(v => cb(VersionID(pair, v.id), v.strAttrs, v.lastUpdated, v.vsn))
    }
  }
  protected case class DownstreamVersionAnswer(pair:DiffaPairRef, res:Seq[Bucket])
      extends VersionAnswer[Function5[VersionID, Map[String, String], DateTime, String, String, Unit]] {
    def answerEntities(entities:Seq[Vsn], cb:Function5[VersionID, Map[String, String], DateTime, String, String, Unit]) {
      entities.foreach(v => cb(VersionID(pair, v.id), v.strAttrs, v.lastUpdated, v.vsn, v.vsn))
    }
  }

  def traverseFirstBranch(tx1:Tx, tx2:Tx)(cb:((Tx, Tx) => Unit)) {
      cb(tx1, tx2)

      (tx1, tx2) match {
        case (atx1:AggregateTx, atx2:AggregateTx) => traverseFirstBranch(atx1.respBuckets(0).nextTx, atx2.respBuckets(0).nextTx)(cb)
        case (atx1:AggregateTx, _) => traverseFirstBranch(atx1.respBuckets(0).nextTx, null)(cb)
        case (_, atx2:AggregateTx) => traverseFirstBranch(null, atx2.respBuckets(0).nextTx)(cb)
        case _ => 
      }
    }
}
object AbstractDataDrivenPolicyTest {

  //
  // Scenarios
  //

  val dateTimeCategoryDescriptor = new RangeCategoryDescriptor("datetime")
  val dateCategoryDescriptor = new RangeCategoryDescriptor("date")
  val intCategoryDescriptor = new RangeCategoryDescriptor("int")
  val stringCategoryDescriptor = new PrefixCategoryDescriptor(1, 3, 1)

  /**
   * This is a DateTime descriptor that is initialized using LocalDates
   */
  val localDatePrimedDescriptor = new RangeCategoryDescriptor("datetime", START_2023.toString, END_2023.toString)

  val domain = Domain(name="domain")

  @DataPoint def noCategoriesScenario = Scenario(
    Pair(key = "ab", domain = domain),
    Endpoint(categories = new HashMap[String, CategoryDescriptor]),
    Endpoint(categories = new HashMap[String, CategoryDescriptor]),
      EntityTx(Seq(),
        Vsn("id1", Map(), "vsn1"),
        Vsn("id2", Map(), "vsn2")
      )
    )

  /**
   * As part of #203, elements of a set are sent out individually by default.
   * For the sake of simplicity, the old behaviour (to send them out as a batch) can not be configured.
   * Should any body ask for this, this behavior be may re-instated at some point.
   */
  @DataPoint def setOnlyScenario = Scenario(
    Pair(key = "ab", domain = domain),
    Endpoint(categories = Map("someString" -> new SetCategoryDescriptor(Set("A","B","C")))),
    Endpoint(categories = Map("someString" -> new SetCategoryDescriptor(Set("A","B","C")))),
      EntityTx(Seq(new SetConstraint("someString", Set("A"))),
        Vsn("id1", Map("someString" -> "A"), "vsn1"),
        Vsn("id2", Map("someString" -> "A"), "vsn2")
      ),
      EntityTx(Seq(new SetConstraint("someString", Set("B"))),
        Vsn("id3", Map("someString" -> "B"), "vsn3"),
        Vsn("id4", Map("someString" -> "B"), "vsn4")
      ),
      EntityTx(Seq(new SetConstraint("someString", Set("C"))),
        Vsn("id5", Map("someString" -> "C"), "vsn5"),
        Vsn("id6", Map("someString" -> "C"), "vsn6")
      )
    )

  @DataPoint def dateTimesOnlyScenario = Scenario(
    Pair(key = "ab", domain = domain),
    Endpoint(categories = Map("bizDateTime" -> dateTimeCategoryDescriptor)),
    Endpoint(categories = Map("bizDateTime" -> dateTimeCategoryDescriptor)),
    AggregateTx(Seq(yearly("bizDateTime", TimeDataType)), Seq(),
      Bucket("2010", Map("bizDateTime" -> "2010"),
        AggregateTx(Seq(monthly("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", START_2010, END_2010)),
          Bucket("2010-07", Map("bizDateTime" -> "2010-07"),
            AggregateTx(Seq(daily("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", JUL_2010, END_JUL_2010)),
              Bucket("2010-07-08", Map("bizDateTime" -> "2010-07-08"),
                EntityTx(Seq(dateTimeRange("bizDateTime", JUL_8_2010, END_JUL_8_2010)),
                  Vsn("id1", Map("bizDateTime" -> JUL_8_2010_1), "vsn1"),
                  Vsn("id2", Map("bizDateTime" -> JUL_8_2010_2), "vsn2")
                )),
              Bucket("2010-07-09", Map("bizDateTime" -> "2010-07-09"),
                EntityTx(Seq(dateTimeRange("bizDateTime", JUL_9_2010, END_JUL_9_2010)),
                  Vsn("id3", Map("bizDateTime" -> JUL_9_2010_1), "vsn3")
                ))
            )),
          Bucket("2010-08", Map("bizDateTime" -> "2010-08"),
            AggregateTx(Seq(daily("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", AUG_2010, END_AUG_2010)),
              Bucket("2010-08-02", Map("bizDateTime" -> "2010-08-02"),
                EntityTx(Seq(dateTimeRange("bizDateTime", AUG_11_2010, END_AUG_11_2010)),
                  Vsn("id4", Map("bizDateTime" -> AUG_11_2010_1), "vsn4")
                ))
            ))
        )),
      Bucket("2011", Map("bizDateTime" -> "2011"),
        AggregateTx(Seq(monthly("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", START_2011, END_2011)),
          Bucket("2011-01", Map("bizDateTime" -> "2011-01"),
            AggregateTx(Seq(daily("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", JAN_2011, END_JAN_2011)),
              Bucket("2011-01-20", Map("bizDateTime" -> "2011-01-20"),
                EntityTx(Seq(dateTimeRange("bizDateTime", JAN_20_2011, END_JAN_20_2011)),
                  Vsn("id5", Map("bizDateTime" -> JAN_20_2011_1), "vsn5")
                ))
            ))
        ))
    ))



  @DataPoint def datesOnlyScenario = Scenario(
    Pair(key = "xy", domain = domain),
    Endpoint(categories = Map("bizDate" -> dateCategoryDescriptor)),
    Endpoint(categories = Map("bizDate" -> dateCategoryDescriptor)),
    AggregateTx(Seq(yearly("bizDate", DateDataType)), Seq(),
      Bucket("1995", Map("bizDate" -> "1995"),
        AggregateTx(Seq(monthly("bizDate", DateDataType)), Seq(dateRange("bizDate", START_1995, END_1995)),
          Bucket("1995-04", Map("bizDate" -> "1995-04"),
            AggregateTx(Seq(daily("bizDate", DateDataType)), Seq(dateRange("bizDate", APR_1_1995, APR_30_1995)),
              Bucket("1995-04-11", Map("bizDate" -> "1995-04-11"),
                EntityTx(Seq(dateRange("bizDate", APR_11_1995, APR_11_1995)),
                  Vsn("id1", Map("bizDate" -> APR_11_1995), "vsn1"),
                  Vsn("id2", Map("bizDate" -> APR_11_1995), "vsn2")
                )),
              Bucket("1995-04-12", Map("bizDate" -> "1995-04-12"),
                EntityTx(Seq(dateRange("bizDate", APR_12_1995, APR_12_1995)),
                  Vsn("id3", Map("bizDate" -> APR_12_1995), "vsn3")
                ))
            )),
          Bucket("1995-05", Map("bizDate" -> "1995-05"),
            AggregateTx(Seq(daily("bizDate", DateDataType)), Seq(dateRange("bizDate", MAY_1_1995, MAY_31_1995)),
              Bucket("1995-05-23", Map("bizDate" -> "1995-05-23"),
                EntityTx(Seq(dateRange("bizDate", MAY_23_1995, MAY_23_1995)),
                  Vsn("id4", Map("bizDate" -> MAY_23_1995), "vsn4")
                ))
            ))
        )),
      Bucket("1996", Map("bizDate" -> "1996"),
        AggregateTx(Seq(monthly("bizDate", DateDataType)), Seq(dateRange("bizDate", START_1996, END_1996)),
          Bucket("1996-03", Map("bizDate" -> "1996-03"),
            AggregateTx(Seq(daily("bizDate", DateDataType)), Seq(dateRange("bizDate", MAR_1_1996, MAR_31_1996)),
              Bucket("1996-03-15", Map("bizDate" -> "1996-03-15"),
                EntityTx(Seq(dateRange("bizDate", MAR_15_1996, MAR_15_1996)),
                  Vsn("id5", Map("bizDate" -> MAR_15_1996), "vsn5")
                ))
            ))
        ))
    ))

  /**
   *  This scenario uses a constrained descriptor that is initialized with LocalDate
   *  values but uses a full DateTime data type during its descent.
   */
  @DataPoint def yy_MM_dddd_dateTimesOnlyScenario = Scenario(
    Pair(key = "tf", domain = domain),
    Endpoint(categories = Map("bizDateTime" -> localDatePrimedDescriptor)),
    Endpoint(categories = Map("bizDateTime" -> localDatePrimedDescriptor)),
    AggregateTx(Seq(yearly("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", START_2023_FULL, END_2023_FULL)),
      Bucket("2023", Map("bizDateTime" -> "2023"),
        AggregateTx(Seq(monthly("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", START_2023_FULL, END_2023_FULL)),
          Bucket("2023-10", Map("bizDateTime" -> "2023-10"),
            AggregateTx(Seq(daily("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", OCT_1_2023, OCT_31_2023)),
              Bucket("2023-10-17", Map("bizDateTime" -> "2023-10-17"),
                EntityTx(Seq(dateTimeRange("bizDateTime", OCT_17_2023_START, OCT_17_2023_END)),
                  Vsn("id1", Map("bizDateTime" -> OCT_17_2023), "vsn1")
                ))
           ))
        ))
    ))

  @DataPoint def integersOnlyScenario = Scenario(
    Pair(key = "bc", domain = domain),
    Endpoint(categories = Map("someInt" -> intCategoryDescriptor)),
    Endpoint(categories = Map("someInt" -> intCategoryDescriptor)),
    AggregateTx(Seq(thousands("someInt")), Seq(),
      Bucket("1000", Map("someInt" -> "1000"),
        AggregateTx(Seq(hundreds("someInt")), Seq(intRange("someInt", 1000, 1999)),
          Bucket("1200", Map("someInt" -> "1200"),
            AggregateTx(Seq(tens("someInt")), Seq(intRange("someInt", 1200, 1299)),
              Bucket("1230", Map("someInt" -> "1230"),
                EntityTx(Seq(intRange("someInt", 1230, 1239)),
                  Vsn("id1", Map("someInt" -> 1234), "vsn1")
                )),
              Bucket("1240", Map("someInt" -> "1240"),
                EntityTx(Seq(intRange("someInt", 1240, 1249)),
                  Vsn("id2", Map("someInt" -> 1245), "vsn2")
                ))
            )),
          Bucket("1300", Map("someInt" -> "1300"),
            AggregateTx(Seq(tens("someInt")), Seq(intRange("someInt", 1300, 1399)),
              Bucket("1350", Map("someInt" -> "1350"),
                EntityTx(Seq(intRange("someInt", 1350, 1359)),
                  Vsn("id3", Map("someInt" -> 1357), "vsn3")
                ))
            ))
        )),
      Bucket("2000", Map("someInt" -> "2000"),
        AggregateTx(Seq(hundreds("someInt")), Seq(intRange("someInt", 2000, 2999)),
          Bucket("2300", Map("someInt" -> "2300"),
            AggregateTx(Seq(tens("someInt")), Seq(intRange("someInt", 2300, 2399)),
              Bucket("2340", Map("someInt" -> "2340"),
                EntityTx(Seq(intRange("someInt", 2340, 2349)),
                  Vsn("id4", Map("someInt" -> 2345), "vsn4")
                ))
            ))
        ))
    ))

  @DataPoint def stringsOnlyScenario = Scenario(
    Pair(key = "bc", domain = domain),
    Endpoint(categories = Map("someString" -> stringCategoryDescriptor)),
    Endpoint(categories = Map("someString" -> stringCategoryDescriptor)),
    AggregateTx(Seq(oneCharString("someString")), Seq(),
      Bucket("A", Map("someString" -> "A"),
        AggregateTx(Seq(twoCharString("someString")), Seq(prefix("someString", "A")),
          Bucket("AB", Map("someString" -> "AB"),
            AggregateTx(Seq(threeCharString("someString")), Seq(prefix("someString", "AB")),
              Bucket("ABC", Map("someString" -> "ABC"),
                EntityTx(Seq(prefix("someString", "ABC")),
                  Vsn("id1", Map("someString" -> "ABC"), "vsn1")
                )),
              Bucket("ABD", Map("someString" -> "ABD"),
                EntityTx(Seq(prefix("someString", "ABD")),
                  Vsn("id2", Map("someString" -> "ABDZ"), "vsn2")
                ))
            )),
          Bucket("AC", Map("someString" -> "AC"),
            AggregateTx(Seq(threeCharString("someString")), Seq(prefix("someString", "AC")),
              Bucket("ACD", Map("someString" -> "ACD"),
                EntityTx(Seq(prefix("someString", "ACD")),
                  Vsn("id3", Map("someString" -> "ACDC"), "vsn3")
                ))
            ))
        )),
      Bucket("Z", Map("someString" -> "Z"),
        AggregateTx(Seq(twoCharString("someString")), Seq(prefix("someString", "Z")),
          Bucket("ZY", Map("someString" -> "ZY"),
            AggregateTx(Seq(threeCharString("someString")), Seq(prefix("someString", "ZY")),
              Bucket("ZYX", Map("someString" -> "ZYX"),
                EntityTx(Seq(prefix("someString", "ZYX")),
                  Vsn("id4", Map("someString" -> "ZYXXY"), "vsn4")
                ))
            ))
        ))
    ))

  @DataPoint def integersAndDateTimesScenario = Scenario(
    Pair(key = "ab", domain = domain),
    Endpoint(categories = Map("bizDateTime" -> dateTimeCategoryDescriptor, "someInt" -> intCategoryDescriptor)),
    Endpoint(categories = Map("bizDateTime" -> dateTimeCategoryDescriptor, "someInt" -> intCategoryDescriptor)),
    AggregateTx(Seq(yearly("bizDateTime", TimeDataType), thousands("someInt")), Seq(),
      Bucket("2010_1000", Map("bizDateTime" -> "2010", "someInt" -> "1000"),
        AggregateTx(Seq(monthly("bizDateTime", TimeDataType), hundreds("someInt")), Seq(dateTimeRange("bizDateTime", START_2010, END_2010), intRange("someInt", 1000, 1999)),
          Bucket("2010-07_1200", Map("bizDateTime" -> "2010-07", "someInt" -> "1200"),
            AggregateTx(Seq(daily("bizDateTime", TimeDataType), tens("someInt")), Seq(dateTimeRange("bizDateTime", JUL_2010, END_JUL_2010), intRange("someInt", 1200, 1299)),
              Bucket("2010-07-08_1230", Map("bizDateTime" -> "2010-07-08", "someInt" -> "1230"),
                EntityTx(Seq(dateTimeRange("bizDateTime", JUL_8_2010, END_JUL_8_2010), intRange("someInt", 1230, 1239)),
                  Vsn("id1", Map("bizDateTime" -> JUL_8_2010_1, "someInt" -> 1234), "vsn1"),
                  Vsn("id2", Map("bizDateTime" -> JUL_8_2010_2, "someInt" -> 1235), "vsn2")
                )),
              Bucket("2010-07-09_1240", Map("bizDateTime" -> "2010-07-09", "someInt" -> "1240"),
                EntityTx(Seq(dateTimeRange("bizDateTime", JUL_9_2010, END_JUL_9_2010), intRange("someInt", 1240, 1249)),
                  Vsn("id3", Map("bizDateTime" -> JUL_9_2010_1, "someInt" -> 1245), "vsn3")
                ))
            )),
          Bucket("2010-08_1300", Map("bizDateTime" -> "2010-08", "someInt" -> "1300"),
            AggregateTx(Seq(daily("bizDateTime", TimeDataType), tens("someInt")), Seq(dateTimeRange("bizDateTime", AUG_2010, END_AUG_2010), intRange("someInt", 1300, 1399)),
              Bucket("2010-08-02_1350", Map("bizDateTime" -> "2010-08-02", "someInt" -> "1350"),
                EntityTx(Seq(dateTimeRange("bizDateTime", AUG_11_2010, END_AUG_11_2010), intRange("someInt", 1350, 1359)),
                  Vsn("id4", Map("bizDateTime" -> AUG_11_2010_1, "someInt" -> 1357), "vsn4")
                ))
            ))
        )),
      Bucket("2011_2000", Map("bizDateTime" -> "2011", "someInt" -> "2000"),
        AggregateTx(Seq(monthly("bizDateTime", TimeDataType), hundreds("someInt")), Seq(dateTimeRange("bizDateTime", START_2011, END_2011), intRange("someInt", 2000, 2999)),
          Bucket("2011-01_2300", Map("bizDateTime" -> "2011-01", "someInt" -> "2300"),
            AggregateTx(Seq(daily("bizDateTime", TimeDataType), tens("someInt")), Seq(dateTimeRange("bizDateTime", JAN_2011, END_JAN_2011), intRange("someInt", 2300, 2399)),
              Bucket("2011-01-20_2340", Map("bizDateTime" -> "2011-01-20", "someInt" -> "2340"),
                EntityTx(Seq(dateTimeRange("bizDateTime", JAN_20_2011, END_JAN_20_2011), intRange("someInt", 2340, 2349)),
                  Vsn("id5", Map("bizDateTime" -> JAN_20_2011_1, "someInt" -> 2345), "vsn5")
                ))
            ))
        ))
    ))

  /**
   * As part of #203, elements of a set are sent out individually by default.
   * For the sake of simplicity, the old behaviour (to send them out as a batch) can not be configured.
   * Should any body ask for this, this behavior be may re-instated at some point.
   */

  @DataPoint def setAndDateTimesScenario = Scenario(
    Pair(key = "gh", domain = domain),
    Endpoint(categories = Map("bizDateTime" -> dateTimeCategoryDescriptor, "someString" -> new SetCategoryDescriptor(Set("A","B")))),
    Endpoint(categories = Map("bizDateTime" -> dateTimeCategoryDescriptor, "someString" -> new SetCategoryDescriptor(Set("A","B")))),
    AggregateTx(Seq(yearly("bizDateTime", TimeDataType)), Seq(new SetConstraint("someString",Set("A"))),
      Bucket("2010_A", Map("bizDateTime" -> "2010", "someString" -> "A"),
        AggregateTx(Seq(monthly("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", START_2010, END_2010), new SetConstraint("someString",Set("A"))),
          Bucket("2010-07_A", Map("bizDateTime" -> "2010-07", "someString" -> "A"),
            AggregateTx(Seq(daily("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", JUL_2010, END_JUL_2010), new SetConstraint("someString",Set("A"))),
              Bucket("2010-07-08_A", Map("bizDateTime" -> "2010-07-08", "someString" -> "A"),
                EntityTx(Seq(dateTimeRange("bizDateTime", JUL_8_2010, END_JUL_8_2010), new SetConstraint("someString",Set("A"))),
                  Vsn("id1", Map("bizDateTime" -> JUL_8_2010_1, "someString" -> "A"), "vsn1"),
                  Vsn("id2", Map("bizDateTime" -> JUL_8_2010_2, "someString" -> "A"), "vsn2")
                )
              )
            )
          )
        )
      )
    ),
    AggregateTx(Seq(yearly("bizDateTime", TimeDataType)), Seq(new SetConstraint("someString",Set("B"))),
      Bucket("2011_B", Map("bizDateTime" -> "2011", "someString" -> "B"),
        AggregateTx(Seq(monthly("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", START_2011, END_2011), new SetConstraint("someString",Set("B"))),
          Bucket("2011-01_B", Map("bizDateTime" -> "2011-01", "someString" -> "B"),
            AggregateTx(Seq(daily("bizDateTime", TimeDataType)), Seq(dateTimeRange("bizDateTime", JAN_2011, END_JAN_2011), new SetConstraint("someString",Set("B"))),
              Bucket("2011-01-20_B", Map("bizDateTime" -> "2011-01-20", "someString" -> "B"),
                EntityTx(Seq(dateTimeRange("bizDateTime", JAN_20_2011, END_JAN_20_2011), new SetConstraint("someString",Set("B"))),
                  Vsn("id3", Map("bizDateTime" -> JAN_20_2011_1, "someString" -> "B"), "vsn3")
                )
              )
            )
          )
        )
      )
    )
  )

  //
  // Aliases
  //

  def yearly(attrName:String, dataType:DateCategoryDataType) = YearlyCategoryFunction(attrName, dataType)
  def monthly(attrName:String, dataType:DateCategoryDataType) = MonthlyCategoryFunction(attrName, dataType)
  def daily(attrName:String, dataType:DateCategoryDataType) = DailyCategoryFunction(attrName, dataType)
  
  def thousands(attrName:String) = IntegerCategoryFunction(attrName, 1000, 10)
  def hundreds(attrName:String) = IntegerCategoryFunction(attrName, 100, 10)
  def tens(attrName:String) = IntegerCategoryFunction(attrName, 10, 10)

  def oneCharString(attrName:String) = StringPrefixCategoryFunction(attrName, 1, 3, 1)
  def twoCharString(attrName:String) = StringPrefixCategoryFunction(attrName, 2, 3, 1)
  def threeCharString(attrName:String) = StringPrefixCategoryFunction(attrName, 3, 3, 1)

  def dateTimeRange(n:String, lower:DateTime, upper:DateTime) = new TimeRangeConstraint(n, lower, upper)
  def dateRange(n:String, lower:LocalDate, upper:LocalDate) = new DateRangeConstraint(n, lower, upper)
  def intRange(n:String, lower:Int, upper:Int) = new IntegerRangeConstraint(n, lower, upper)
  def prefix(n: String, prefix: String) = new StringPrefixConstraint(n, prefix)

  //
  // Type Definitions
  //

  case class Scenario(pair:DiffaPair, upstreamEp:Endpoint, downstreamEp:Endpoint, tx:Tx*)

  abstract class Tx {
    def constraints:Seq[ScanConstraint]
    def allVsns:Seq[Vsn]
    def alterFirstVsn(newVsn:String):Tx
    def firstVsn:Vsn
    def toString(indent:Int):String
  }

  /**
   * @param bucketing The bucketing policy to apply
   * @param constraints The value constraints being applied to this transaction
   * @param respBuckets The list of buckets expected in this transaction
   */
  case class AggregateTx(bucketing:Seq[CategoryFunction], constraints:Seq[ScanConstraint], respBuckets:Bucket*) extends Tx {
    lazy val allVsns = respBuckets.flatMap(b => b.allVsns)

    def alterFirstVsn(newVsn:String) =
      // This uses the prepend operator +: to alter the first the element of the list and then re-attach the remainder to create a new sequence
      AggregateTx(bucketing, constraints, (respBuckets(0).alterFirstVsn(newVsn) +: respBuckets.drop(1)):_*)
    def firstVsn = respBuckets(0).nextTx.firstVsn

    def toString(indent:Int) = (" " * indent) + "AggregateTx(" + bucketing + ", " + constraints + ")\n" + respBuckets.map(b => b.toString(indent + 2)).foldLeft("")(_ + _)
  }
  case class EntityTx(constraints:Seq[ScanConstraint], entities:Vsn*) extends Tx {
    lazy val allVsns = entities

    def alterFirstVsn(newVsn:String) = EntityTx(constraints, (entities(0).alterVsn(newVsn) +: entities.drop(1)):_*)
    def firstVsn = entities(0)

    def toString(indent:Int) = (" " * indent) + "EntityTx(" + constraints + ")\n" + entities.map(e => e.toString(indent + 2)).foldLeft("")(_ + _)
  }

  case class Bucket(name:String, attrs:Map[String, String], nextTx:Tx) {
    lazy val allVsns = nextTx.allVsns
    lazy val vsn = DigestUtils.md5Hex(allVsns.map(v => v.vsn).foldLeft("")(_ + _))

    def alterFirstVsn(newVsn:String):Bucket = Bucket(name, attrs, nextTx.alterFirstVsn(newVsn))

    def toString(indent:Int) = (" " * indent) + "Bucket(" + name + ", " + attrs + ", " + vsn + ")\n" + nextTx.toString(indent + 2)
  }
  case class Vsn(id:String, attrs:Map[String, Any], vsn:String) {
    def typedAttrs = attrs.map { case (k, v) => k -> toTyped(v) }.toMap
    def strAttrs = attrs.map { case (k, v) => k -> v.toString }.toMap
    lazy val lastUpdated = new DateTime

    def alterVsn(newVsn:String) = {
      Vsn(id, attrs, newVsn)
    }

    def toString(indent:Int) = (" " * indent) + "Vsn(" + id + ", " + attrs + ", " + vsn + ")\n"

    def toTyped(v:Any) = v match {
      case i:Int        => IntegerAttribute(i)
      case dt:DateTime  => DateTimeAttribute(dt)
      case dt:LocalDate => DateAttribute(dt)
      case _            => StringAttribute(v.toString)
    }
  }
}