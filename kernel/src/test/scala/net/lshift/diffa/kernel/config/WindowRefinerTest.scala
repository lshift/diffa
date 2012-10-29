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

import org.junit.{ Assert, Test }
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTime
import org.joda.time.format.ISOPeriodFormat
import org.joda.time.DateTimeZone

class WindowRefinerTest {
  val dateTimeFormatter = ISODateTimeFormat.dateTime()
  val periodFormatter = ISOPeriodFormat.standard()
  val now = DateTime.now().withZone(DateTimeZone.UTC)
  val startOfDay = now.toDateMidnight().toDateTime()

  implicit def dateToString(date: DateTime): String = date.toString(dateTimeFormatter)
  implicit def pairToTimeInterval(pair: (String, String)) = TimeIntervalFactory.fromRange(pair._1, pair._2)

  @Test(expected = classOf[IllegalArgumentException])
  def emptyPeriodExpressionIsInvalid() {
    WindowRefiner.forPeriodExpression("")
  }

  @Test
  def shouldAcceptEmptyOffset() {
    try {
      WindowRefiner.forPeriodExpression("P1D").withOffset("")
    } catch {
      case ex: IllegalArgumentException =>
        Assert.fail("Empty offset was incorrectly rejected")
    }
  }

  @Test
  def shouldAcceptDateOnlyRanges() {
    WindowRefiner.forPeriodExpression("P1D").refineInterval(null, "2012-10-10")
  }

  @Test
  def shouldAcceptDateTimeRanges() {
    WindowRefiner.forPeriodExpression("P1D").refineInterval(null, "2012-10-10T12:00:01Z")
  }

  @Test
  def windowOverlappingRangeShouldBeARefinement() {
    val (start, end) = (timeAgo("P7M"), timeAgo("P5M"))

    val refiner = WindowRefiner.forPeriodExpression("P6M")

    Assert.assertTrue("Expected window overlapping range to be a refinement",
      refiner.isRefinementOf(start, end))
  }

  @Test
  def windowNotOverlappingRangeShouldNotBeARefinement() {
    val (start, end) = (timeAgo("P8M"), timeAgo("P6M"))

    val refiner = WindowRefiner.forPeriodExpression("P6M")

    Assert.assertFalse("Non-overlapping window should not be a refinement",
      refiner.isRefinementOf(start, end))
  }

  @Test
  def offsetWindowOverlappingRangeShouldBeARefinement() {
    val (start, end) = (timeAgo("P3D"), timeAgo("P2D"))

    val refiner = WindowRefiner.forPeriodExpression("P2D").withOffset("PT0H")

    Assert.assertTrue("Expected window overlapping range to be a refinement",
      refiner.isRefinementOf(start, end))
  }

  @Test
  def offsetWindowNotOverlappingRangeShouldNotBeARefinement() {
    val (start, end) = (timeAgo("P4D"), timeAgo("P3D"))

    val refiner = WindowRefiner.forPeriodExpression("P3D").withOffset("PT24H")

    Assert.assertFalse("Non-overlapping window should not be a refinement",
      refiner.isRefinementOf(start, end))
  }

  @Test
  def refinementOfFiniteRangeToWindowShouldBeTheirIntersection() {
    val mockTime = startOfDay.plusHours(1)
    val (start, end) = (startOfDay.minusDays(2), startOfDay.minusDays(1))
    val intersection: TimeInterval = TimeIntervalFactory.fromRange(start.plusHours(1), end)

    val refiner = WindowRefiner.forPeriodExpression("P2D").usingTime(mockTime)
    val refinement = refiner.refineInterval(start, end)

    Assert.assertEquals("Refinement of range to window was not equal to their intersection",
      intersection, refinement)
  }

  /*
   * [(-2,0),(-1)]
   * P2D + 2H,1S
   * [(-2, +2H1S), -)
   */
  @Test
  def refinementOfFiniteRangeToOffsetWindowShouldBeTheirIntersection() {
    val (start, end) = (startOfDay.minusDays(2), startOfDay.minusDays(1))
    val intersection: TimeInterval = TimeIntervalFactory.fromRange(start.plusHours(2).plusSeconds(1), end)

    val refiner = WindowRefiner.forPeriodExpression("P2D").
      withOffset("PT2H1S")
    val refinement = refiner.refineInterval(start, end)

    Assert.assertEquals("Refinement of range to offset window was not equal to their intersection",
      intersection, refinement)
  }

  @Test
  def refinementOfLowerUnboundedRangeToWindowShouldBeTheirIntersection() {
    val mockTime = startOfDay.plusHours(3)
    val (start, end) = (null, startOfDay.minusDays(1))
    val intersection: (String, String) = (startOfDay.minusDays(2).plusHours(3), end)

    val refiner = WindowRefiner.forPeriodExpression("P2D").usingTime(mockTime)
    val refinement = refiner.refineInterval(start, end)

    Assert.assertEquals("Refinement of lower unbounded range to window was not equal to their intersection",
      intersection, (refinement.getStartAs(DateTimeType.DATETIME), refinement.getEndAs(DateTimeType.DATETIME)))
  }

  @Test
  def refinementOfUpperUnboundedRangeToWindowShouldBeTheirIntersection() {
    val mockTime = startOfDay.plusHours(3)
    val (start, end) = (startOfDay.minusDays(2), null)
    val intersection: (String, String) = (startOfDay.minusDays(2).plusHours(3), mockTime)

    val refiner = WindowRefiner.forPeriodExpression("P2D").usingTime(mockTime)
    val refinement = refiner.refineInterval(start, end)

    Assert.assertEquals("Refinement of upper unbounded range to window was not equal to their intersection",
      intersection, (refinement.getStartAs(DateTimeType.DATETIME), refinement.getEndAs(DateTimeType.DATETIME)))
  }

  private def daysAgo(days: Int) =
    now.toDateMidnight().minusDays(days).toDateTime().toString(dateTimeFormatter)

  private def timeAgo(periodSpec: String, from: DateTime = now) =
    from.minus(periodFormatter.parsePeriod(periodSpec))
}
