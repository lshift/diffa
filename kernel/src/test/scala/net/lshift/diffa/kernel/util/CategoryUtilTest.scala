/**
 * Copyright (C) 2010-2012 LShift Ltd.
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

package net.lshift.diffa.kernel.util

import org.junit.Test
import org.junit.Assert._
import scala.collection.JavaConversions._
import org.springframework.mock.web.MockHttpServletRequest
import net.lshift.diffa.participant.scanning._
import net.lshift.diffa.kernel.config._
import org.joda.time.{DateTimeZone, DateTime}
import org.joda.time.format.ISODateTimeFormat

class CategoryUtilTest {
  val baseStringCategory = new SetCategoryDescriptor(Set("a", "b"))
  val baseIntCategory = new RangeCategoryDescriptor("integer", "5", "12")
  val baseDateCategory = new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31", "individual")
  val stringOverrideCategory = new SetCategoryDescriptor(Set("a"))

  val dateFormatter = ISODateTimeFormat.date.withZoneUTC
  val timeFormatter = ISODateTimeFormat.dateTime.withZoneUTC
  val now = DateTime.now().withZone(DateTimeZone.UTC)
  val tomorrow = now.plusDays(1).dayOfYear().roundFloorCopy().toString(dateFormatter)
  val tomorrowMorning = now.plusDays(1).dayOfYear().roundFloorCopy().toString(timeFormatter)
  val today = now.dayOfYear().roundFloorCopy().toString(dateFormatter)
  val thisMorning = now.dayOfYear().roundFloorCopy().plusHours(1).toString(timeFormatter)
  val threeDaysAgo = now.minusDays(3).dayOfYear().roundFloorCopy().toString(dateFormatter)


  val endpointCategories =
    Map("someString" -> baseStringCategory, "someInt" -> baseIntCategory, "someDate" -> baseDateCategory)
  val stringOverrideCategories = Map("someString" -> stringOverrideCategory)
  val dateOverrideCategories = Map("someDate" -> new RangeCategoryDescriptor("date", "2011-05-05", "2011-06-01"))
  val dateOverrideWithGranCategories = Map("someDate" -> new RangeCategoryDescriptor("date", "2011-05-05", "2011-06-01", "monthly"))
  val views = Seq(
    EndpointView(name = "lessStrings", categories = stringOverrideCategories),
    EndpointView(name = "lessDates", categories = dateOverrideCategories),
    EndpointView(name = "lessDatesMoreGranularity", categories = dateOverrideWithGranCategories)
  )

  @Test
  def shouldReturnEndpointCategoriesWhenViewIsNone() {
    val fused = CategoryUtil.fuseViewCategories(endpointCategories, views, None)
    assertEquals(endpointCategories, fused)
  }

  @Test
  def shouldApplySetCategoryOverride() {
    val fused = CategoryUtil.fuseViewCategories(endpointCategories, views, Some("lessStrings"))
    assertEquals(
      Map("someString" -> stringOverrideCategory, "someInt" -> baseIntCategory, "someDate" -> baseDateCategory),
      fused)
  }

  @Test
  def shouldApplyDateCategoryOverrideAndInheritGranularitySettings() {
    val fused = CategoryUtil.fuseViewCategories(endpointCategories, views, Some("lessDates"))
    val fusedDateCategory = new RangeCategoryDescriptor("date", "2011-05-05", "2011-06-01", "individual")
    assertEquals(
      Map("someString" -> baseStringCategory, "someInt" -> baseIntCategory, "someDate" -> fusedDateCategory),
      fused)
  }

  @Test
  def shouldRefineDateRangeCategoryWithRollingWindow() {
    val viewWithWindow = Seq(EndpointView(name = "threeDayRoller", categories = Map("someDateRange" -> new RollingWindowFilter("P3D", "PT1H"))))
    val baseRangeCategory = Map("someDateRange" -> new RangeCategoryDescriptor("date", "2000-01-01", tomorrow, "individual"))

    assertEquals("A date range should be constrained by a rolling window",
      Map("someDateRange" -> new RangeCategoryDescriptor("date", threeDaysAgo, today, "individual")),
      CategoryUtil.fuseViewCategories(baseRangeCategory, viewWithWindow, Some("threeDayRoller")))
  }

  @Test
  def shouldRefineDateTimeRangeCategoryWithRollingWindow() {
    val viewWithWindow = Seq(EndpointView(name = "threeDayRoller", categories = Map("someTimeRange" -> new RollingWindowFilter("P3D", "PT1H"))))
    val baseRangeCategory = Map("someTimeRange" -> new RangeCategoryDescriptor("datetime", "2000-01-01T00:00:00Z", tomorrowMorning, "individual"))

    assertEquals("A date range should be constrained by a rolling window",
      Map("someTimeRange" -> new RangeCategoryDescriptor("datetime",
        now.minusDays(3).dayOfYear().roundFloorCopy().plusHours(1).toString(timeFormatter),
        thisMorning,
        "individual")),
      CategoryUtil.fuseViewCategories(baseRangeCategory, viewWithWindow, Some("threeDayRoller")))
  }

  @Test
  def shouldApplyDateCategoryOverrideAndOverrideGranularitySettings() {
    val fused = CategoryUtil.fuseViewCategories(endpointCategories, views, Some("lessDatesMoreGranularity"))
    val fusedDateCategory = new RangeCategoryDescriptor("date", "2011-05-05", "2011-06-01", "monthly")
    assertEquals(
      Map("someString" -> baseStringCategory, "someInt" -> baseIntCategory, "someDate" -> fusedDateCategory),
      fused)
  }

  @Test
  def shouldInferDateBoundsFromUpperToLower() {
    val unboundedUpperDateCategory = Map("someDate" -> new RangeCategoryDescriptor("date", "2011-01-01", null, "individual"))
    val unboundedLowerDateCategory = Map("someDate" -> new RangeCategoryDescriptor("date", null, "2011-12-31", "individual"))

    val unboundedLowerView = Seq(EndpointView(name = "dates", categories = unboundedLowerDateCategory))
    val unboundedUpperView = Seq(EndpointView(name = "dates", categories = unboundedUpperDateCategory))

    val fusedForwards = CategoryUtil.fuseViewCategories(unboundedUpperDateCategory, unboundedLowerView, Some("dates"))
    val fusedBackwards = CategoryUtil.fuseViewCategories(unboundedLowerDateCategory, unboundedUpperView, Some("dates"))

    assertEquals(
      Map("someDate" -> new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31", "individual")),
      fusedForwards)

    assertEquals(
      Map("someDate" -> new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31", "individual")),
      fusedBackwards)
  }

  @Test
  def shouldApplyASetConstraintToAConstraintsBuilder() {
    val req = new MockHttpServletRequest
    req.addParameter("someString", "aaa")
    req.addParameter("someString", "bbb")
    val builder = new ConstraintsBuilder(req)

    CategoryUtil.buildConstraints(builder, Map("someString" -> new SetCategoryDescriptor(Set("aaa", "bbb"))))

    assertEquals(
      Seq(new SetConstraint("someString", Set("aaa", "bbb"))),
      builder.toList.toSeq)
  }

  @Test
  def shouldApplyAPrefixCategoryToAConstraintsBuilder() {
    val req = new MockHttpServletRequest
    req.addParameter("someString-prefix", "abc")
    val builder = new ConstraintsBuilder(req)

    CategoryUtil.buildConstraints(builder, Map("someString" -> new PrefixCategoryDescriptor(1, 10, 1)))

    assertEquals(
      Seq(new StringPrefixConstraint("someString", "abc")),
      builder.toList.toSeq)
  }

  @Test
  def shouldApplyADateCategoryToAConstraintsBuilder() {
    val req = new MockHttpServletRequest
    req.addParameter("bizDate-start", "2011-06-01")
    req.addParameter("bizDate-end", "2011-06-30")
    val builder = new ConstraintsBuilder(req)

    CategoryUtil.buildConstraints(builder, Map("bizDate" -> new RangeCategoryDescriptor("date")))

    assertEquals(
      Seq(new DateRangeConstraint("bizDate", "2011-06-01", "2011-06-30")),
      builder.toList.toSeq)
  }

  @Test
  def shouldApplyATimeCategoryToAConstraintsBuilder() {
    val req = new MockHttpServletRequest
    req.addParameter("bizTime-start", "2011-06-01T15:14:13.000Z")
    req.addParameter("bizTime-end", "2011-06-30T12:31:00.000Z")
    val builder = new ConstraintsBuilder(req)

    CategoryUtil.buildConstraints(builder, Map("bizTime" -> new RangeCategoryDescriptor("datetime")))

    assertEquals(
      Seq(new TimeRangeConstraint("bizTime", "2011-06-01T15:14:13.000Z", "2011-06-30T12:31:00.000Z")),
      builder.toList.toSeq)
  }

  @Test
  def shouldApplyAnIntegerCategoryToAConstraintsBuilder() {
    val req = new MockHttpServletRequest
    req.addParameter("someInt-start", "15")
    req.addParameter("someInt-end", "32")
    val builder = new ConstraintsBuilder(req)

    CategoryUtil.buildConstraints(builder, Map("someInt" -> new RangeCategoryDescriptor("int")))

    assertEquals(
      Seq(new IntegerRangeConstraint("someInt", "15", "32")),
      builder.toList.toSeq)
  }

  @Test
  def shouldAcceptValidConstraintsAgainstTheirCategories() {
    CategoryUtil.mergeAndValidateConstraints(Map(
        "someString"  -> new SetCategoryDescriptor(Set("aaa", "bbb")),
        "somePString" -> new PrefixCategoryDescriptor(1, 10, 1),
        "bizDate"     -> new RangeCategoryDescriptor("date", "2011-06-01", "2011-06-30"),
        "bizTime"     -> new RangeCategoryDescriptor("datetime", "2011-06-01T15:14:13.000Z", "2011-06-30T12:31:00.000Z"),
        "someInt"     -> new RangeCategoryDescriptor("int", "5", "52")
      ),
      Seq(
        new SetConstraint("someString", Set("aaa")),
        new StringPrefixConstraint("somePString", "abc"),
        new DateRangeConstraint("bizDate", "2011-06-15", "2011-06-30"),
        new TimeRangeConstraint("bizTime", "2011-06-01T15:14:13.000Z", "2011-06-18T00:21:15.000Z"),
        new IntegerRangeConstraint("someInt", "10", "15")
      )
    )
  }

  @Test
  def shouldRejectAnInvalidSetConstraint() {
    expectConstraintException(
      Map("someString"  -> new SetCategoryDescriptor(Set("aaa", "bbb"))),
      Seq(new SetConstraint("someString", Set("abc"))),
      "someString: Not all of the values [abc] are supported by category [aaa, bbb]"
    )
  }

  @Test
  def shouldRejectATooShortInvalidPrefixConstraint() {
    expectConstraintException(
      Map("somePString" -> new PrefixCategoryDescriptor(5, 10, 1)),
      Seq(new StringPrefixConstraint("somePString", "abc")),
      "somePString: Prefix abc is shorter than configured start length 5")
  }

  @Test
  def shouldRejectATooLongInvalidPrefixConstraint() {
    expectConstraintException(
      Map("somePString" -> new PrefixCategoryDescriptor(5, 10, 1)),
      Seq(new StringPrefixConstraint("somePString", "abcdefabcdef")),
      "somePString: Prefix abcdefabcdef is longer than configured max length 10")
  }

  @Test
  def shouldRejectAnOutOfRangeDateConstraint() {
    expectConstraintException(
      Map("bizDate" -> new RangeCategoryDescriptor("date", "2011-06-01", "2011-06-30")),
      Seq(new DateRangeConstraint("bizDate", "2011-05-15", "2011-06-30")),
      "bizDate: DateRangeConstraint{name=bizDate, start=2011-05-15, end=2011-06-30} isn't contained within DateRangeConstraint{name=bizDate, start=2011-06-01, end=2011-06-30}")
  }

  @Test
  def shouldRejectAnOutOfRangeTimeConstraint() {
    expectConstraintException(
      Map("bizTime" -> new RangeCategoryDescriptor("datetime", "2011-06-01T15:14:13.000Z", "2011-06-30T12:31:00.000Z")),
      Seq(new TimeRangeConstraint("bizTime", "2011-05-01T15:14:13.000Z", "2011-06-18T00:21:15.000Z")),
      "bizTime: TimeRangeConstraint{name=bizTime, start=2011-05-01T15:14:13.000Z, end=2011-06-18T00:21:15.000Z} isn't contained within TimeRangeConstraint{name=bizTime, start=2011-06-01T15:14:13.000Z, end=2011-06-30T12:31:00.000Z}")
  }

  @Test
  def shouldDifferenceEmptyListsOfCategories() {
    assertEquals(Seq(), CategoryUtil.differenceCategories(Map(), Map()))
  }

  @Test
  def shouldDetectAddedCategoryDescriptor() {
    val sc = new SetCategoryDescriptor(Set("aaa", "bbb"))
    assertEquals(Seq(CategoryChange("someSet", None, Some(sc))),
      CategoryUtil.differenceCategories(Map(), Map("someSet" -> sc)))
  }

  @Test
  def shouldDetectRemovedCategoryDescriptor() {
    val sc = new SetCategoryDescriptor(Set("aaa", "bbb"))
    assertEquals(Seq(CategoryChange("someSet", Some(sc), None)),
      CategoryUtil.differenceCategories(Map("someSet" -> sc), Map()))
  }

  @Test
  def shouldNotDetectUnchangedCategoryDescriptor() {
    val sc = new SetCategoryDescriptor(Set("aaa", "bbb"))
    assertEquals(Seq(), CategoryUtil.differenceCategories(Map("someSet" -> sc), Map("someSet" -> sc)))
  }

  @Test
  def shouldDetectChangedCategoryDescriptor() {
    val sc = new SetCategoryDescriptor(Set("aaa", "bbb"))
    val sc2 = new SetCategoryDescriptor(Set("aaa", "bbb", "ccc"))
    assertEquals(Seq(CategoryChange("someSet", Some(sc), Some(sc2))),
      CategoryUtil.differenceCategories(Map("someSet" -> sc), Map("someSet" -> sc2)))
  }

  private def expectConstraintException(categories:Map[String, AggregatingCategoryDescriptor], constraints:Seq[ScanConstraint], message:String) {
    try {
      CategoryUtil.mergeAndValidateConstraints(categories, constraints)
    } catch {
      case e:InvalidConstraintException => assertEquals(message, e.getMessage)
    }
  }
}