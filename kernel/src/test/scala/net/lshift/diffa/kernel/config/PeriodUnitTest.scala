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
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.Interval
import org.junit.Assert
import org.junit.experimental.theories.DataPoint
import org.junit.experimental.theories.Theory
import org.junit.runner.RunWith
import org.junit.experimental.theories.Theories

@RunWith(classOf[Theories])
class PeriodUnitTest {
  val now = DateTime.now.withZone(DateTimeZone.UTC)
  val dateTimeParser = ISODateTimeFormat.dateOptionalTimeParser.withZoneUTC

  import PeriodUnitTest._

  @Theory
  def intervalShouldCoverDayOrNot(scenario: Scenario) {
    Assert.assertEquals("Interval should cover a day", scenario.coversDay,
      PeriodUnit.DAILY.isCovering(scenario.interval))
  }

  @Theory
  def intervalShouldCoverMonthOrNot(scenario: Scenario) {
    Assert.assertEquals("Interval should cover a calendar month", scenario.coversMonth,
      PeriodUnit.MONTHLY.isCovering(scenario.interval))
  }

  @Theory
  def intervalShouldCoverYearOrNot(scenario: Scenario) {
    Assert.assertEquals("Interval should cover a calendar year", scenario.coversYear,
      PeriodUnit.YEARLY.isCovering(scenario.interval))
  }
}

object PeriodUnitTest {
  case class Scenario(interval: Interval, coversDay: Boolean, coversMonth: Boolean, coversYear: Boolean)

  private[PeriodUnitTest] implicit def stringPairToInterval(pair: (String, String)): Interval =
    new BoundedTimeInterval(pair._1, pair._2).toJodaInterval

  @DataPoint def midnightToMidnight = Scenario(("2000-01-01T00Z", "2000-01-02T00Z"), true, false, false)
  @DataPoint def oneAmTo1am = Scenario(("2000-01-01T01Z", "2000-01-02T01Z"), false, false, false)
  @DataPoint def monthStartToMonthEnd = Scenario(("2000-01-01T00Z", "2000-02-01T00Z"), true, true, false)
  @DataPoint def midMonthToMidMonth = Scenario(("2000-01-11T00Z", "2000-02-11T00Z"), true, false, false)
  @DataPoint def yearStartToYearEnd = Scenario(("2000-01-01T00Z", "2001-01-01T00Z"), true, true, true)
  @DataPoint def midYearToMidYear = Scenario(("2000-01-01T01Z", "2001-01-01T01Z"), true, true, false)
}