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

package net.lshift.diffa.kernel.config

import org.junit.Test
import org.junit.Assert
import org.junit.runner.RunWith
import org.junit.experimental.theories.Theories
import org.junit.experimental.theories.DataPoints
import scala.collection.JavaConversions.asJavaList
import org.junit.experimental.theories.Theory
import org.junit.experimental.theories.DataPoint
import net.lshift.diffa.config._

@RunWith(classOf[Theories])
class TimeIntervalTest {
  import TimeIntervalTest.Scenario

  @Test
  def intervalFromRangeByNullShouldEqualIntervalFromRangeByEmptyString() {
    val (openByEmpty, openByNull) = (
      TimeIntervalFactory.fromRange("", ""),
      TimeIntervalFactory.fromRange(null, null))
    Assert.assertEquals("The empty string should have the same meaning as null in TimeInterval creation",
      openByEmpty, openByNull)
  }

  @Theory
  def overlapShouldBeEqualToIntersectionOfIntervals(scenario: Scenario) {
    Assert.assertEquals(scenario.expectedOverlap, scenario.i1.overlap(scenario.i2))
  }

  @Theory
  def overlapShouldBeReflexive(scenario: Scenario) {
    Assert.assertEquals("TimeInterval.overlap should be reflexive",
      scenario.i2.overlap(scenario.i1),
      scenario.i1.overlap(scenario.i2))
  }

  @Theory
  def intervalsShouldOverlapIfTheyIntersect(scenario: Scenario) {
    Assert.assertEquals(scenario.expectedOverlapping, scenario.i1.overlaps(scenario.i2))
  }

  @Theory
  def overlapsShouldBeReflexive(scenario: Scenario) {
    Assert.assertEquals("TimeInterval.overlaps should be reflexive",
      scenario.i2.overlaps(scenario.i1),
      scenario.i1.overlaps(scenario.i2))
  }
}

object TimeIntervalTest {
  implicit private def pairToRange(pair: (String, String)) = TimeIntervalFactory.fromRange(pair._1, pair._2)

  case class Scenario(i1: TimeInterval, i2: TimeInterval, expectedOverlap: TimeInterval, expectedOverlapping: Boolean)
  private val unbounded = UnboundedTimeInterval.getInstance()
  private val nullInterval: TimeInterval = NullTimeInterval.getInstance()

  private val halfClosedUpperShort: TimeInterval = ("", "2020-04-01")
  private val halfClosedUpperLong: TimeInterval = ("", "2020-10-01")
  private val halfClosedLowerShort: TimeInterval = ("2020-07-01", "")
  private val halfClosedLowerLong: TimeInterval = ("2020-01-01", "")
  private val closedShortEarly: TimeInterval = ("2020-02-01", "2020-03-01")
  private val closedShortLate: TimeInterval = ("2020-08-01", "2020-09-01")
  private val closedLong: TimeInterval = ("2020-01-01", "2021-01-01")
  private val closedLongEarly: TimeInterval = ("2019-07-01", "2020-08-01")
  private val closedLongLate: TimeInterval = ("2020-06-01", "2021-07-01")
  private val halfClosedLongIntersection: TimeInterval = ("2020-01-01", "2020-10-01")

  /*
   * fully open interval: (--)
   * closed interval: [--]
   * half open interval (lower end open): (--]
   * half open interval (upper end open): [--)
   */

  // (), () => (), true
  @DataPoint def openOpen = Scenario(unbounded, unbounded, unbounded, true)

  // (), (] => (], true
  @DataPoint def openHalfClosedUpper = Scenario(unbounded, halfClosedUpperShort, halfClosedUpperShort, true)

  // (), [) => [), true
  @DataPoint def openHalfClosedLower = Scenario(unbounded, halfClosedLowerShort, halfClosedLowerShort, true)

  // (), [] => [], true
  @DataPoint def openClosed = Scenario(unbounded, closedShortEarly, closedShortEarly, true)

  /*
   *      [----)
   *    (----]
   *      [--]
   */
  @DataPoint def halfClosedLowerHalfClosedUpper = Scenario(
    halfClosedLowerLong,
    halfClosedUpperLong,
    halfClosedLongIntersection, true)

  /*
   *   (---]
   *         [----)
   * NullTimeInterval
   */
  @DataPoint def disjointHalfClosed = Scenario(
    halfClosedUpperShort,
    halfClosedLowerShort,
    nullInterval, false)

  /*
   *    [-----)
   *  [-------)
   *    [-----)
   */
  @DataPoint def bothHalfClosedLower = Scenario(
    halfClosedLowerShort,
    halfClosedLowerLong,
    halfClosedLowerShort, true)

  /*
   *  (----]
   *  (------]
   *  (----]
   */
  @DataPoint def bothHalfClosedUpper = Scenario(
    halfClosedUpperShort,
    halfClosedUpperLong,
    halfClosedUpperShort, true)

  /*
     * [--]
     *      [--]
     * NullTimeInterval
     */
  @DataPoint def disjointClosed = Scenario(
    closedShortEarly,
    closedShortLate,
    nullInterval, false)

  /*
   *  (------]
   *   [---]
   *   [---]
   */
  @DataPoint def closedContainedInHalfClosedUpper = Scenario(
    halfClosedUpperLong,
    closedShortEarly,
    (closedShortEarly.getStartAs(DateTimeType.DATE), closedShortEarly.getEndAs(DateTimeType.DATE)), true)

  /*
   *  [------)
   *    [--]
   *    [--]
   */
  @DataPoint def closedContainedInHalfClosedLower = Scenario(
    halfClosedLowerLong,
    closedShortLate,
    (closedShortLate.getStartAs(DateTimeType.DATE), closedShortLate.getEndAs(DateTimeType.DATE)), true)

  /*
   *   [------)
   * [----]
   *   [--]
   */
  @DataPoint def closedOverlapsHalfClosedLower = Scenario(
    halfClosedLowerLong,
    closedLongEarly,
    (halfClosedLowerLong.getStartAs(DateTimeType.DATE), closedLongEarly.getEndAs(DateTimeType.DATE)), true)

  /*
   *   (------]
   *     [--]
   *     [--]
   */
  @DataPoint def halfClosedUpperClosedLongEarly = Scenario(
    halfClosedUpperShort,
    closedLong,
    (closedLong.getStartAs(DateTimeType.DATE), halfClosedUpperShort.getEndAs(DateTimeType.DATE)), true)

  /*
   *  (------]
   *      [----]
   *      [--]
   */
  @DataPoint def closedOverlapsHalfClosedUpper = Scenario(
    halfClosedUpperLong,
    closedLongLate,
    (closedLongLate.getStartAs(DateTimeType.DATE), halfClosedUpperLong.getEndAs(DateTimeType.DATE)), true)

  /*
   * [----]
   *   [----]
   *   [--]
   */
  @DataPoint def closedClosedOverlapping = Scenario(
    closedLongEarly,
    closedLongLate,
    (closedLongLate.getStartAs(DateTimeType.DATE), closedLongEarly.getEndAs(DateTimeType.DATE)), true)

  /*
   * [--]
   *     [--]
   * NullTimeInterval
   */
  @DataPoint def closedClosedDisjoint = Scenario(
    closedShortEarly,
    closedShortLate,
    nullInterval, false)
}