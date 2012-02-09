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

package net.lshift.diffa.kernel.frontend

import org.junit.Test
import org.junit.Assert._
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.config._
import org.junit.experimental.theories.{Theories, Theory, DataPoint}
import org.junit.runner.RunWith

@RunWith(classOf[Theories])
class ConfigValidationTest {

  @Test
  def shouldRejectEndpointWithScanUrlThatIsTooLong = {
    validateError(
      EndpointDef(name = "a", scanUrl = "*" * 1025),
      "config/endpoint[name=a]: scanUrl is too long. Limit is 1024, value " + ("*" * 1025) + " is 1025"
    )
  }

  @Test
  def shouldRejectEndpointWithContentRetrievalUrlThatIsTooLong = {
    validateError(
      EndpointDef(name = "a", contentRetrievalUrl = "*" * 1025),
      "config/endpoint[name=a]: contentRetrievalUrl is too long. Limit is 1024, value " + ("*" * 1025) + " is 1025"
    )
  }

  @Test
  def shouldRejectEndpointWithVersionGenerationUrlThatIsTooLong = {
    validateError(
      EndpointDef(name = "a", versionGenerationUrl = "*" * 1025),
      "config/endpoint[name=a]: versionGenerationUrl is too long. Limit is 1024, value " + ("*" * 1025) + " is 1025"
    )
  }

  @Test
  def shouldRejectEndpointWithInboundUrlThatIsTooLong = {
    validateError(
      EndpointDef(name = "a", inboundUrl = "*" * 1025),
      "config/endpoint[name=a]: inboundUrl is too long. Limit is 1024, value " + ("*" * 1025) + " is 1025"
    )
  }

  @Test
  def shouldRejectEndpointWithoutName() {
    validateError(new EndpointDef(name = null), "config/endpoint[name=null]: name cannot be null or empty")
  }

  @Test
  def shouldRejectEndpointViewWithoutName() {
    validateError(
      new EndpointDef(name = "a", views = List(EndpointViewDef())),
      "config/endpoint[name=a]/views[name=null]: name cannot be null or empty"
    )
  }

  @Test
  def shouldRejectEndpointViewWithNonUniqueName() {
    validateError(
      new EndpointDef(name = "a", views = List(EndpointViewDef(name = "a"), EndpointViewDef(name = "a"))),
      "config/endpoint[name=a]/views[name=a]: 'a' is not a unique name"
    )
  }
  
  @Test
  def shouldRejectPairWithoutKey() {
    validateError(
      PairDef(key = null),
      Set(),
      "config/pair[key=null]: key cannot be null or empty"
    )
  }

  @Test
  def shouldRejectPairWithScanCronSpecThatIsntACronSpec() {
    validateError(
      PairDef(key = "p", upstreamName = "a", downstreamName = "b", scanCronSpec = "1 2 3"),
      Set(EndpointDef(name = "a"), EndpointDef(name = "b")),
      "config/pair[key=p]: Schedule '1 2 3' is not a valid: Unexpected end of expression."
    )
  }

  @Test
  def shouldRejectPairThatUsesAnUpstreamEndpointThatDoesntExist() {
    validateError(
      PairDef(key = "p", upstreamName = "c", downstreamName = "b"),
      Set(EndpointDef(name = "a"), EndpointDef(name = "b")),
      "config/pair[key=p]: Upstream endpoint 'c' is not defined"
    )
  }

  @Test
  def shouldRejectPairThatUsesADownstreamEndpointThatDoesntExist() {
    validateError(
      PairDef(key = "p", upstreamName = "a", downstreamName = "c"),
      Set(EndpointDef(name = "a"), EndpointDef(name = "b")),
      "config/pair[key=p]: Downstream endpoint 'c' is not defined"
    )
  }

  @Test
  def shouldRejectViewsWithCategoriesNotPresentOnParent() {
    validateError(
      EndpointDef(name = "endpointA",
        categories = Map("someString" -> new SetCategoryDescriptor(Set("a", "b"))),
        views = List(EndpointViewDef(name = "invalid",
          categories = Map("otherString" -> new SetCategoryDescriptor(Set("c", "d")))))),
      "config/endpoint[name=endpointA]/views[name=invalid]: View category 'otherString' does not derive from an endpoint category"
    )
  }

  @Test
  def shouldRejectViewsWithCategoriesThatArentWithinBoundsOfParent() {
    validateError(
      EndpointDef(name = "endpointA",
        categories = Map("someString" -> new SetCategoryDescriptor(Set("a", "b"))),
        views = List(EndpointViewDef(name = "invalid",
          categories = Map("someString" -> new SetCategoryDescriptor(Set("c", "d")))))),
      "config/endpoint[name=endpointA]/views[name=invalid]: View category 'someString' (SetCategoryDescriptor{values=[c, d]}) does not refine endpoint category (SetCategoryDescriptor{values=[a, b]})"
    )
  }

  @Test
  def shouldAcceptAnyPrefixCategoryRefinementsThatArePrefixCategories() {
    val base = new PrefixCategoryDescriptor(5, 12, 1)

    assertTrue(base.isRefinement(new PrefixCategoryDescriptor(2, 12, 1)))
    assertTrue(base.isRefinement(new PrefixCategoryDescriptor(5, 10, 2)))
  }

  @Test
  def shouldRejectAnyPrefixCategoryRefinementsThatAreNotPrefixCategories() {
    val base = new PrefixCategoryDescriptor(5, 12, 1)

    assertFalse(base.isRefinement(new SetCategoryDescriptor(Set("a", "b"))))
    assertFalse(base.isRefinement(new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31")))
  }

  @Test
  def shouldAcceptSetCategoriesThatAreEqualOrSubset() {
    val base = new SetCategoryDescriptor(Set("a", "b", "c"))

    assertTrue(base.isRefinement(new SetCategoryDescriptor(Set("c", "b", "a"))))
    assertTrue(base.isRefinement(new SetCategoryDescriptor(Set("a", "c"))))
    assertTrue(base.isRefinement(new SetCategoryDescriptor(Set[String]())))
  }

  @Test
  def shouldRejectSetCategoriesThatAreNotEqualOrSubset() {
    val base = new SetCategoryDescriptor(Set("a", "b", "c"))

    assertFalse(base.isRefinement(new SetCategoryDescriptor(Set("c", "b", "a", "d"))))
    assertFalse(base.isRefinement(new SetCategoryDescriptor(Set("a", "d"))))
  }

  @Test
  def shouldRejectAnySetCategoryRefinementsThatAreNotSetCategories() {
    val base = new SetCategoryDescriptor(Set("a", "b", "c"))

    assertFalse(base.isRefinement(new PrefixCategoryDescriptor(5, 12, 1)))
    assertFalse(base.isRefinement(new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31")))
  }

  @Theory
  def shouldAcceptRangeCategoriesThatAreEqualOrSubset(scenario:RangeScenario) {
    val base = new RangeCategoryDescriptor(scenario.dataType, scenario.lower, scenario.upper)
    val unboundedBase = new RangeCategoryDescriptor(scenario.dataType, null, null)

    assertTrue(base.isRefinement(new RangeCategoryDescriptor(scenario.dataType, scenario.lower, scenario.upper)))
    assertTrue(base.isRefinement(new RangeCategoryDescriptor(scenario.dataType, scenario.lowerMid, scenario.upperMid)))

    assertTrue(unboundedBase.isRefinement(new RangeCategoryDescriptor(scenario.dataType, scenario.lowerMid, scenario.upperMid)))
    assertTrue(unboundedBase.isRefinement(new RangeCategoryDescriptor(scenario.dataType, scenario.lowerMid, null)))
    assertTrue(unboundedBase.isRefinement(new RangeCategoryDescriptor(scenario.dataType, null, scenario.wayAfter)))
    assertTrue(unboundedBase.isRefinement(new RangeCategoryDescriptor(scenario.dataType, null, null)))
  }

  @Theory
  def shouldAcceptRangeCategoriesThatRefineUnboundedUpperLimitBase(scenario:RangeScenario) {
    val unboundedUpperLimit = new RangeCategoryDescriptor(scenario.dataType, scenario.lower, null)
    assertTrue(new RangeCategoryDescriptor(scenario.dataType, null, scenario.upper).isRefinement(unboundedUpperLimit))
  }

  @Theory
  def shouldAcceptRangeCategoriesThatRefineUnboundedLowerLimitBase(scenario:RangeScenario) {
    val unboundedLowerLimit = new RangeCategoryDescriptor(scenario.dataType, null, scenario.upper)
    assertTrue(new RangeCategoryDescriptor(scenario.dataType, scenario.lower, null).isRefinement(unboundedLowerLimit))
  }

  @Theory
  def shouldRejectRangeCategoriesThatAreNotWithinOuterRange(scenario:RangeScenario) {
    val base = new RangeCategoryDescriptor(scenario.dataType, scenario.lower, scenario.upper)

    assertFalse(base.isRefinement(new RangeCategoryDescriptor(scenario.dataType, scenario.justBefore, scenario.lowerMid)))
    assertFalse(base.isRefinement(new RangeCategoryDescriptor(scenario.dataType, scenario.lowerMid, scenario.justAfter)))
  }

  @Theory
  def shouldOverrideNullLowerRangeWithParentRange(scenario:RangeScenario) {
    val base = new RangeCategoryDescriptor(scenario.dataType, scenario.lower, scenario.upper)

    assertEquals(new RangeCategoryDescriptor(scenario.dataType, scenario.lowerMid, scenario.upper),
      base.applyRefinement(new RangeCategoryDescriptor(scenario.dataType, scenario.lowerMid, null)))
  }

  @Theory
  def shouldOverrideNullUpperRangeWithParentRange(scenario:RangeScenario) {
    val base = new RangeCategoryDescriptor(scenario.dataType, scenario.lower, scenario.upper)

    assertEquals(new RangeCategoryDescriptor(scenario.dataType, scenario.lowerMid, scenario.upper),
      base.applyRefinement(new RangeCategoryDescriptor(scenario.dataType, scenario.lowerMid, null)))
  }

  @Theory
  def shouldOverrideNullParentLowerRangeWithRefinementLower(scenario:RangeScenario) {
    val base = new RangeCategoryDescriptor(scenario.dataType, null, scenario.upper)

    assertEquals(new RangeCategoryDescriptor(scenario.dataType, scenario.lower, scenario.upperMid),
      base.applyRefinement(new RangeCategoryDescriptor(scenario.dataType, scenario.lower, scenario.upperMid)))
  }

  @Theory
  def shouldOverrideNullParentUpperRangeWithRefinmentUpper(scenario:RangeScenario) {
    val base = new RangeCategoryDescriptor(scenario.dataType, scenario.lower, null)

    assertEquals(new RangeCategoryDescriptor(scenario.dataType, scenario.lowerMid, scenario.upper),
      base.applyRefinement(new RangeCategoryDescriptor(scenario.dataType, scenario.lowerMid, scenario.upper)))
  }

  @Test
  def shouldRejectAnyDateRangeCategoryRefinementsThatAreNotDateRangeCategories() {
    val base = new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31")

    assertFalse(base.isRefinement(new SetCategoryDescriptor(Set("c", "b", "a", "d"))))
    assertFalse(base.isRefinement(new PrefixCategoryDescriptor(5, 12, 1)))
    assertFalse(base.isRefinement(new RangeCategoryDescriptor("datetime", "2011-01-01T00:00:00.000Z", "2011-12-31T23:59:59.999Z")))
    assertFalse(base.isRefinement(new RangeCategoryDescriptor("integer", "52", "104")))
  }

  @Test
  def shouldRejectAnyTimeRangeCategoryRefinementsThatAreNotTimeRangeCategories() {
    val base = new RangeCategoryDescriptor("datetime", "2011-01-01T00:00:00.000Z", "2011-12-31T00:00:00.000Z")

    assertFalse(base.isRefinement(new SetCategoryDescriptor(Set("c", "b", "a", "d"))))
    assertFalse(base.isRefinement(new PrefixCategoryDescriptor(5, 12, 1)))
    assertFalse(base.isRefinement(new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31")))
    assertFalse(base.isRefinement(new RangeCategoryDescriptor("integer", "52", "104")))
  }

  @Test
  def shouldRejectAnyIntegerRangeCategoryRefinementsThatAreNotIntegerRangeCategories() {
    val base = new RangeCategoryDescriptor("integer", "52", "104")

    assertFalse(base.isRefinement(new SetCategoryDescriptor(Set("c", "b", "a", "d"))))
    assertFalse(base.isRefinement(new PrefixCategoryDescriptor(5, 12, 1)))
    assertFalse(base.isRefinement(new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31")))
    assertFalse(base.isRefinement(new RangeCategoryDescriptor("datetime", "2011-01-01T00:00:00.000Z", "2011-12-31T00:00:00.000Z")))
  }

  @Test
  def shouldAcceptPairViewThatUsesViewsThatExistOnBothEndpoints() {
    val pairDef = PairDef(key = "p", upstreamName = "a", downstreamName = "b", views = List(PairViewDef("abc")))
    val endpoints = Set(
      EndpointDef(name = "a", views = List(EndpointViewDef(name = "abc"))),
      EndpointDef(name = "b", views = List(EndpointViewDef(name = "abc"))))

    pairDef.validate("config", endpoints)
  }

  @Test
  def shouldRejectPairViewThatUsesAnUpstreamViewThatDoesntExist() {
    validateError(
      PairDef(key = "p", upstreamName = "a", downstreamName = "b", views = List(PairViewDef("abc"))),
      Set(EndpointDef(name = "a"), EndpointDef(name = "b", views = List(EndpointViewDef(name = "abc")))),
      "config/pair[key=p]/views[name=abc]: The upstream endpoint does not define the view 'abc'"
    )
  }

  @Test
  def shouldRejectPairViewThatUsesADownstreamViewThatDoesntExist() {
    validateError(
      PairDef(key = "p", upstreamName = "a", downstreamName = "b", views = List(PairViewDef("abc"))),
      Set(EndpointDef(name = "a", views = List(EndpointViewDef(name = "abc"))), EndpointDef(name = "b")),
      "config/pair[key=p]/views[name=abc]: The downstream endpoint does not define the view 'abc'"
    )
  }

  @Test
  def shouldAcceptPairViewThatUsesValidCronExpression() {
    val pairDef = PairDef(key = "p", upstreamName = "a", downstreamName = "b",
      views = List(PairViewDef("abc", scanCronSpec = "0 * * * * ?")))
    val endpoints = Set(
      EndpointDef(name = "a", views = List(EndpointViewDef(name = "abc"))),
      EndpointDef(name = "b", views = List(EndpointViewDef(name = "abc"))))

    pairDef.validate("config", endpoints)
  }

  @Test
  def shouldRejectPairViewThatUsesInvalidCronExpression() {
    validateError(
      PairDef(key = "p", upstreamName = "a", downstreamName = "b",
              views = List(PairViewDef("abc", scanCronSpec = "1 2 3"))),
      Set(
        EndpointDef(name = "a", views = List(EndpointViewDef(name = "abc"))),
        EndpointDef(name = "b", views = List(EndpointViewDef(name = "abc")))),
      "config/pair[key=p]/views[name=abc]: Schedule '1 2 3' is not a valid: Unexpected end of expression."
    )
  }

  @Test
  def shouldAcceptReportWithValidReportType() {
    val reportDef = PairReportDef(name = "Process Differences", pair ="p", reportType = "differences",
                                  target = "http://someapp.com/handle_report")
    reportDef.validate("config")
  }

  @Test
  def shouldRejectReportWithInvalidReportType() {
    validateError(
      PairReportDef(name = "Process Differences", pair ="p", reportType = "blah-blah",
                    target = "http://someapp.com/handle_report"),
      "config/pair[key=p]/report[name=Process Differences]: Invalid report type: blah-blah"
    )
  }

  @Test
  def shouldRejectReportWithMissingTarget() {
    validateError(
      PairReportDef(name = "Process Differences", pair ="p", reportType = "differences"),
      "config/pair[key=p]/report[name=Process Differences]: Missing target"
    )
  }

  @Test
  def shouldRejectReportWithInvalidTarget() {
    validateError(
      PairReportDef(name = "Process Differences", pair ="p", reportType = "differences",
                    target = "random-target"),
      "config/pair[key=p]/report[name=Process Differences]: Invalid target (not a URL): random-target"
    )
  }

  @Test
  def shouldRejectUserWithoutName() {
    validateError(
      UserDef(email = "user@domain.com", password = "password"),
      "config/user[name=null]: name cannot be null or empty"
    )
  }

  @Test
  def shouldRejectUserWithoutEmail() {
    validateError(
      UserDef(name = "some.user", password = "password"),
      "config/user[name=some.user]: email cannot be null or empty"
    )
  }

  @Test
  def shouldRejectUserWithoutPasswordWhenNotExternal() {
    validateError(
      UserDef(name = "some.user", email = "user@domain.com"),
      "config/user[name=some.user]: password cannot be null or empty"
    )
  }

  @Test
  def shouldAcceptExternalUserWithoutPassword() {
    val userDef = UserDef(name = "some.user", email = "user@domain.com", external = true)
    userDef.validate("config")
  }

  type Validatable = {
    def validate(path:String)
  }

  def validateError(v:Validatable, msg:String) {
    try {
      v.validate("config")
      fail("Should have thrown ConfigValidationException")
    } catch {
      case e:ConfigValidationException =>
        assertEquals(msg, e.getMessage)
    }
  }
  def validateError(v:PairDef, endpoints:Set[EndpointDef], msg:String) {
    try {
      v.validate("config", endpoints)
      fail("Should have thrown ConfigValidationException")
    } catch {
      case e:ConfigValidationException =>
        assertEquals(msg, e.getMessage)
    }
  }
}

case class RangeScenario(dataType:String, lower:String, upper:String, justBefore:String, justAfter:String, wayAfter:String, lowerMid:String, upperMid:String)
object ConfigValidationTest {
  @DataPoint def dateRange = RangeScenario(
    dataType = "date",
    lower = "2011-01-01", upper = "2011-12-31",
    justBefore = "2010-12-31", justAfter ="2012-01-01",
    wayAfter = "2014-11-01",
    lowerMid = "2011-05-05", upperMid = "2011-11-01")

  @DataPoint def timeRange = RangeScenario(
    dataType = "datetime",
    lower = "2011-01-01T12:52:12.123Z", upper = "2011-12-31T05:12:13.876Z",
    justBefore = "2011-01-01T12:52:12.122Z", justAfter ="2012-01-01T00:00:00.000Z",
    wayAfter = "2014-11-01T12:52:12.123Z",
    lowerMid = "2011-05-05T01:02:03.000Z", upperMid = "2011-11-01T12:13:14.123Z")

  @DataPoint def integerRange = RangeScenario(
    dataType = "int",
    lower = "52", upper = "104",
    justBefore = "51", justAfter ="105",
    wayAfter = "150",
    lowerMid = "58", upperMid = "101")
}