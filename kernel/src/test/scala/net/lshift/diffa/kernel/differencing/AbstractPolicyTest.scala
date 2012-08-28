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

import scala.collection.mutable.{HashMap}
import scala.collection.JavaConversions._
import scala.concurrent.SyncVar

import org.easymock.EasyMock._
import org.easymock.{IAnswer, EasyMock}
import org.joda.time.DateTime
import org.apache.commons.codec.digest.DigestUtils
import org.junit.Test

import net.lshift.diffa.kernel.util.EasyMockScalaUtils._
import net.lshift.diffa.kernel.participants._
import net.lshift.diffa.kernel.util.FullDateTimes._
import net.lshift.diffa.kernel.util.DateUtils._
import net.lshift.diffa.kernel.events._
import net.lshift.diffa.kernel.participants.IntegerCategoryFunction._
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.kernel.util.NonCancellingFeedbackHandle
import net.lshift.diffa.participant.scanning._
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.frontend.DomainPairDef

/**
 * Base class for the various policy tests.
 */
abstract class AbstractPolicyTest {
  // The policy instance under test
  protected def policy:VersionPolicy

  // A method for generating a downstream version based on an upstream version
  protected def downstreamVersionFor(v:String):String

  // The various mocks for listeners and participants
  val usMock = createStrictMock("us", classOf[UpstreamParticipant])
  val dsMock = createStrictMock("ds", classOf[DownstreamParticipant])
  val nullListener = new NullDifferencingListener
  val nullDiffWriter = createMock(classOf[DifferenceWriter])
  val diagnostics = createStrictMock("diagnostics", classOf[DiagnosticsManager])

  val writer = createMock("writer", classOf[LimitedVersionCorrelationWriter])
  val extendedWriter = createMock("extendedWriter", classOf[ExtendedVersionCorrelationWriter])
  val store = createMock("versionStore", classOf[VersionCorrelationStore])

  val stores = new VersionCorrelationStoreFactory {
    def apply(pairKey: PairRef) = store
    def remove(pairKey: PairRef) {}
    def close(pairKey: PairRef) {}
    def close {}
  }

  val feedbackHandle = new NonCancellingFeedbackHandle

  val listener = createStrictMock("listener", classOf[DifferencingListener])
  val diffWriter = createStrictMock("diffWriter", classOf[DifferenceWriter])

  val systemConfigStore = createStrictMock("systemConfigStore", classOf[SystemConfigStore])
  val domainConfigStore = createStrictMock("domainConfigStore", classOf[DomainConfigStore])

  val space = System.currentTimeMillis()
  val pairKey = "A-B"

  val emptyAttributes:Map[String,TypedAttribute] = Map()
  val emptyStrAttributes:Map[String,String] = Map()

  val dateCategoryDescriptor = new RangeCategoryDescriptor("datetime")
  val intCategoryDescriptor = new RangeCategoryDescriptor("int")

  val upstream = new Endpoint(categories=Map("bizDate" -> dateCategoryDescriptor))
  val downstream = new Endpoint(categories=Map("bizDate" -> dateCategoryDescriptor))
  val pair = new DomainPairDef(key=pairKey, space=space, upstreamName=upstream.name, downstreamName=downstream.name)

  expect(domainConfigStore.getPairDef(space, pairKey)).andReturn(pair).anyTimes
  replay(systemConfigStore)

  protected def replayAll = replay(usMock, dsMock, store, writer, listener, diffWriter)
  protected def verifyAll = verify(usMock, dsMock, store, writer, listener, systemConfigStore, diffWriter)

  // Make declaring of sequences of specific types clearer
  def DigestsFromParticipant[T](vals:T*) = Seq[T](vals:_*)
  def VersionsFromStore[T](vals:T*) = Seq[T](vals:_*)

  def yearly(attrName:String, dataType:DateCategoryDataType) = YearlyCategoryFunction(attrName, dataType)
  def monthly(attrName:String, dataType:DateCategoryDataType) = MonthlyCategoryFunction(attrName, dataType)
  def daily(attrName:String, dataType:DateCategoryDataType) = DailyCategoryFunction(attrName, dataType)

  def byName(attrName:String) = ByNameCategoryFunction(attrName)

  def thousands(attrName:String) = IntegerCategoryFunction(attrName, 1000, 10)
  def hundreds(attrName:String) = IntegerCategoryFunction(attrName, 100, 10)
  def tens(attrName:String) = IntegerCategoryFunction(attrName, 10, 10)

  def Up(id: String, o:Map[String, String], s: String): UpstreamVersion = Up(VersionID(pair.asRef, id), o, s)
  def Up(v:VersionID, o:Map[String, String], s:String): UpstreamVersion = UpstreamVersion(v, o, new DateTime, s)
  def Down(id: String, o:Map[String, String], s1: String, s2: String): DownstreamVersion = Down(VersionID(pair.asRef, id), o, s1, s2)
  def Down(v:VersionID, o:Map[String, String], s1:String, s2:String): DownstreamVersion = DownstreamVersion(v, o, new DateTime, s1, s2)

  def bizDateMap(d:DateTime) = Map("bizDate" -> DateTimeAttribute(d))
  def bizDateSeq(d:DateTime) = Seq(d.toString())
  def bizDateStrMap(d:DateTime) = Map("bizDate" -> d.toString())

  case class PolicyTestData(
    upstreamCategories: Map[String, CategoryDescriptor],
    downstreamCategories: Map[String, CategoryDescriptor],
    bucketing:Seq[Seq[CategoryFunction]],
    constraints: Seq[Seq[ScanConstraint]],
    attributes: Seq[Map[String, String]],
    downstreamAttributes: Seq[Map[String, TypedAttribute]],
    values: Seq[Map[String, String]]
  ) {
    def upstreamAttributes = downstreamAttributes
  }

  val dateCategoryData = PolicyTestData(
    upstreamCategories = Map("bizDate" -> dateCategoryDescriptor),
    downstreamCategories = Map("bizDate" -> dateCategoryDescriptor),
    bucketing = Seq(Seq(YearlyCategoryFunction("bizDate", DateDataType)),
                    Seq(MonthlyCategoryFunction("bizDate", DateDataType)),
                    Seq(DailyCategoryFunction("bizDate", DateDataType)),
                    Seq()),
    constraints = Seq(Seq(),
                      Seq(new TimeRangeConstraint("bizDate", START_2010, END_2010)),
                      Seq(new TimeRangeConstraint("bizDate", JUL_2010, END_JUL_2010)),
                      Seq(new TimeRangeConstraint("bizDate", JUL_8_2010, endOfDay(JUL_8_2010)))),
    attributes = Seq(Map("bizDate" -> "2009"), Map("bizDate" -> "2010"), Map("bizDate" -> "2010-07"), Map("bizDate" -> "2010-07-08")),
    downstreamAttributes = Seq(bizDateMap(JUN_6_2009_1), bizDateMap(JUL_8_2010_1)),
    values = Seq(Map("bizDate" -> JUN_6_2009_1.toString), Map("bizDate" -> JUL_8_2010_1.toString))
  )

  val integerCategoryData = PolicyTestData(
    upstreamCategories = Map("someInt" -> intCategoryDescriptor),
    downstreamCategories = Map("someInt" -> intCategoryDescriptor),
    bucketing = Seq(Seq(thousands("someInt")),
                    Seq(hundreds("someInt")),
                    Seq(tens("someInt")),
                    Seq()),
    constraints = Seq(Seq(),
                      Seq(new IntegerRangeConstraint("someInt", 2000, 2999)),
                      Seq(new IntegerRangeConstraint("someInt", 2300, 2399)),
                      Seq(new IntegerRangeConstraint("someInt", 2340, 2349))),
    attributes = Seq(Map("someInt" -> "1000"), Map("someInt" -> "2000"), Map("someInt" -> "2300"), Map("someInt" -> "2340")),
    downstreamAttributes = Seq(Map("someInt" -> IntegerAttribute(1234)), Map("someInt" -> IntegerAttribute(2345))),
    values = Seq(Map("someInt" -> "1234"), Map("someInt" -> "2345"))
  )

  @Test
  def shouldStoreUpstreamChangesToCorrelationStoreAndNotifyDifferencesManagerForQuasiLiveDate {
    val lastUpdate = Some(JUL_8_2010_2)
    storeUpstreamChanges(emptyAttributes, lastUpdate)
  }

  @Test
  def shouldStoreUpstreamChangesToCorrelationStoreAndNotifyDifferencesManagerWithoutLastUpdate {
    val lastUpdate = None
    storeUpstreamChanges(emptyAttributes, lastUpdate)
  }

  @Test
  def shouldStoreDownstreamChangesToCorrelationStoreAndNotifyDifferencesManagerForDateCategories =
    shouldStoreDownstreamChangesToCorrelationStoreAndNotifyDifferencesManager(
      upstreamCategories = Map("bizDate" -> dateCategoryDescriptor),
      downstreamCategories = Map("bizDate" -> dateCategoryDescriptor),
      downstreamAttributes = bizDateMap(JUL_8_2010_2))

  @Test
  def shouldStoreDownstreamChangesToCorrelationStoreAndNotifyDifferencesManagerForIntegerCategories =
    shouldStoreDownstreamChangesToCorrelationStoreAndNotifyDifferencesManager(
      upstreamCategories = Map("someInt" -> intCategoryDescriptor),
      downstreamCategories = Map("someInt" -> intCategoryDescriptor),
      downstreamAttributes = Map("someInt" -> IntegerAttribute(1234)))

  @Test
  def shouldStoreDownstreamCorrelatedChangesToCorrelationStoreAndNotifyDifferencesManagerForDateCategories =
    shouldStoreDownstreamCorrelatedChangesToCorrelationStoreAndNotifyDifferencesManager(
      upstreamCategories = Map("bizDate" -> dateCategoryDescriptor),
      downstreamCategories = Map("bizDate" -> dateCategoryDescriptor),
      downstreamAttributes = bizDateMap(JUL_8_2010_2))

  @Test
  def shouldStoreDownstreamCorrelatedChangesToCorrelationStoreAndNotifyDifferencesManagerForIntegerCategories =
    shouldStoreDownstreamCorrelatedChangesToCorrelationStoreAndNotifyDifferencesManager(
      upstreamCategories = Map("someInt" -> intCategoryDescriptor),
      downstreamCategories = Map("someInt" -> intCategoryDescriptor),
      downstreamAttributes = Map("someInt" -> IntegerAttribute(1234)))

  @Test
  def shouldRaiseMatchEventWhenDownstreamCausesMatchOfUpstreamForDateCategories =
    shouldRaiseMatchEventWhenDownstreamCausesMatchOfUpstream(
      upstreamCategories = Map("bizDate" -> dateCategoryDescriptor),
      downstreamCategories = Map("bizDate" -> dateCategoryDescriptor),
      downstreamAttributes = bizDateMap(JUL_8_2010_2))

  @Test
  def shouldRaiseMatchEventWhenDownstreamCausesMatchOfUpstreamForIntegerCategories =
    shouldRaiseMatchEventWhenDownstreamCausesMatchOfUpstream(
      upstreamCategories = Map("someInt" -> intCategoryDescriptor),
      downstreamCategories = Map("someInt" -> intCategoryDescriptor),
      downstreamAttributes = Map("someInt" -> IntegerAttribute(1234)))

  /**
   * This is a utility function that allows a kind of virtual date mode for testing
   * historical submissions
   */
  def storeUpstreamChanges(attrs:Map[String,TypedAttribute], lastUpdate:Option[DateTime]) {
    val timestamp = new DateTime
    val (update, observationDate, f) = lastUpdate match {
      case None     => (timestamp, null, () =>
        writer.storeUpstreamVersion(EasyMock.eq(VersionID(pair.asRef, "id1")), EasyMock.eq(attrs),
                                     between(timestamp, timestamp.plusMillis(200)), EasyMock.eq("vsn1"), EasyMock.anyObject[Option[Long]]()))
      case Some(x)  => (x, x, () => writer.storeUpstreamVersion(VersionID(pair.asRef, "id1"), attrs, x, "vsn1", None))
    }
    expect(f()).andReturn(new Correlation(null, pair.asRef, "id1", toStrMap(attrs), null, update, timestamp, "vsn1", null, null, false))
    listener.onMismatch(VersionID(pair.asRef, "id1"), update, "vsn1", null, LiveWindow, Unfiltered); expectLastCall
    replayAll

    policy.onChange(writer, UpstreamPairChangeEvent(VersionID(pair.asRef, "id1"), attrs, observationDate, "vsn1"))
    verifyAll
  }

  protected def shouldStoreDownstreamChangesToCorrelationStoreAndNotifyDifferencesManager(
    upstreamCategories: Map[String, CategoryDescriptor],
    downstreamCategories: Map[String, CategoryDescriptor],
    downstreamAttributes: Map[String, TypedAttribute]
  ) {
    upstream.categories = upstreamCategories
    downstream.categories = downstreamCategories
    val timestamp = new DateTime

    expect(writer.storeDownstreamVersion(VersionID(pair.asRef, "id1"), downstreamAttributes, JUL_8_2010_2, "vsn1", "vsn1", None)).
      andReturn(new Correlation(null, pair.asRef, "id1", Map[String,String](), Map[String,String](), JUL_8_2010_2, timestamp, null, "vsn1", "vsn1", false))
    listener.onMismatch(VersionID(pair.asRef, "id1"), JUL_8_2010_2, null, "vsn1", LiveWindow, Unfiltered); expectLastCall
    replayAll

    policy.onChange(writer, DownstreamPairChangeEvent(VersionID(pair.asRef, "id1"), downstreamAttributes, JUL_8_2010_2, "vsn1"))
    verifyAll
  }

  protected def shouldStoreDownstreamCorrelatedChangesToCorrelationStoreAndNotifyDifferencesManager(
    upstreamCategories: Map[String, CategoryDescriptor],
    downstreamCategories: Map[String, CategoryDescriptor],
    downstreamAttributes: Map[String, TypedAttribute]
  ) {
    upstream.categories = upstreamCategories
    downstream.categories = downstreamCategories
    val timestamp = new DateTime
    expect(writer.storeDownstreamVersion(VersionID(pair.asRef, "id1"), downstreamAttributes, JUL_8_2010_2, "vsn1", "vsn2", None)).
      andReturn(new Correlation(null, pair.asRef, "id1", null, toStrMap(downstreamAttributes), JUL_8_2010_2, timestamp, null, "vsn1", "vsn1", false))
    listener.onMismatch(VersionID(pair.asRef, "id1"), JUL_8_2010_2, null, "vsn1", LiveWindow, Unfiltered); expectLastCall
    replayAll

    policy.onChange(writer, DownstreamCorrelatedPairChangeEvent(VersionID(pair.asRef, "id1"), downstreamAttributes, JUL_8_2010_2, "vsn1", "vsn2"))
    verifyAll
  }

  protected def shouldRaiseMatchEventWhenDownstreamCausesMatchOfUpstream(
    upstreamCategories: Map[String, CategoryDescriptor],
    downstreamCategories: Map[String, CategoryDescriptor],
    downstreamAttributes: Map[String, TypedAttribute]
  ) {
    upstream.categories = upstreamCategories
    downstream.categories = downstreamCategories
    val timestamp = new DateTime
    expect(writer.storeDownstreamVersion(VersionID(pair.asRef, "id1"), downstreamAttributes, JUL_8_2010_2, "vsn1", "vsn2", None)).
      andReturn(new Correlation(null, pair.asRef, "id1", null, toStrMap(downstreamAttributes), JUL_8_2010_2, timestamp, "vsn1", "vsn1", "vsn2", true))
    listener.onMatch(VersionID(pair.asRef, "id1"), "vsn1", LiveWindow); expectLastCall
    replayAll

    policy.onChange(writer, DownstreamCorrelatedPairChangeEvent(VersionID(pair.asRef, "id1"), downstreamAttributes, JUL_8_2010_2, "vsn1", "vsn2"))
    verifyAll
  }

  //
  // Standard Types
  //

  protected case class UpstreamVersion(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, vsn:String)
  protected case class UpstreamVersionAnswer(hs:Seq[UpstreamVersion]) extends IAnswer[Unit] {
    def answer {
      val args = EasyMock.getCurrentArguments
      val cb = args(1).asInstanceOf[Function4[VersionID, Map[String, String], DateTime, String, Unit]]

      hs.foreach { case UpstreamVersion(id, attributes, lastUpdate, vsn) =>
        cb(id, attributes, lastUpdate, vsn)
      }
    }
  }
  protected case class DownstreamVersion(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, usvn:String, dsvn:String)
  protected case class DownstreamVersionAnswer(hs:Seq[DownstreamVersion]) extends IAnswer[Unit] {
    def answer {
      val args = EasyMock.getCurrentArguments
      val cb = args(1).asInstanceOf[Function5[VersionID, Map[String, String], DateTime, String, String, Unit]]

      hs.foreach { case DownstreamVersion(id, attributes, lastUpdate, uvsn, dvsn) =>
        cb(id, attributes, lastUpdate, uvsn, dvsn)
      }
    }
  }

  protected def expectUpstreamAggregateScan(bucketing:Seq[CategoryFunction], constraints: Seq[ScanConstraint], partResp: Seq[ScanResultEntry],
                                            storeResp: Seq[UpstreamVersion]) {
    expectUpstreamAggregateScan(pair, bucketing, constraints, partResp, storeResp: Seq[UpstreamVersion])
  }

  protected def expectUpstreamAggregateScan(pair:DomainPairDef, bucketing:Seq[CategoryFunction], constraints:Seq[ScanConstraint], partResp:Seq[ScanResultEntry], storeResp:Seq[UpstreamVersion]) {
    expect(usMock.scan(constraints, bucketing)).andReturn(partResp)
    store.queryUpstreams(EasyMock.eq(constraints), anyUnitF4)
      expectLastCall[Unit].andAnswer(UpstreamVersionAnswer(storeResp))
  }

  protected def expectDownstreamAggregateScan(bucketing:Seq[CategoryFunction], constraints: Seq[ScanConstraint], partResp: Seq[ScanResultEntry],
                                              storeResp: Seq[DownstreamVersion]) {
    expectDownstreamAggregateScan(pair, bucketing, constraints, partResp, storeResp)
  }

  protected def expectDownstreamAggregateScan(pair:DomainPairDef, bucketing:Seq[CategoryFunction], constraints:Seq[ScanConstraint], partResp:Seq[ScanResultEntry], storeResp:Seq[DownstreamVersion]) {
    expect(dsMock.scan(constraints, bucketing)).andReturn(partResp)
    store.queryDownstreams(EasyMock.eq(constraints), anyUnitF5)
      expectLastCall[Unit].andAnswer(DownstreamVersionAnswer(storeResp))
  }

  protected def expectUpstreamEntityScan(constraints:Seq[ScanConstraint], partResp:Seq[ScanResultEntry], storeResp:Seq[UpstreamVersion]) {
    expect(usMock.scan(constraints, Seq())).andReturn(partResp)
    val correlations = storeResp.map(r => {
      val c = new Correlation()
      c.id = r.id.id
      c.upstreamAttributes = r.attributes
      c.lastUpdate = r.lastUpdate
      c.upstreamVsn = r.vsn
      c
    })

    expect(store.queryUpstreams(EasyMock.eq(constraints))).andReturn(correlations)
  }
  protected def expectDownstreamEntityScan2(constraints:Seq[ScanConstraint], partResp:Seq[ScanResultEntry], storeResp:Seq[DownstreamVersion]) {
    expect(dsMock.scan(constraints, Seq())).andReturn(partResp)
    val correlations = storeResp.map(r => {
      val c = new Correlation      
      c.id = r.id.id
      c.downstreamAttributes = r.attributes
      c.lastUpdate = r.lastUpdate
      c.downstreamDVsn = r.dsvn
      c
    })

    expect(store.queryDownstreams(EasyMock.eq(constraints))).andReturn(correlations)
  }

  protected def toStrMap(attrs:Map[String, TypedAttribute]) = attrs.map { case (k, v) => k -> v.value }.toMap
}