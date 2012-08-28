/**
 * Copyright (C) 2012 LShift Ltd.
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

import org.junit.Assert._
import org.junit.Assume._
import org.hamcrest.CoreMatchers._

import net.lshift.diffa.kernel.config.PairRef
import org.junit.runner.RunWith
import org.junit.experimental.theories.{Theory, DataPoint, Theories}
import org.joda.time.{DateTimeZone, DateTime}

@RunWith(classOf[Theories])
class DifferenceAggregationCachePolicyTest {
  import DifferenceAggregationCachePolicyTest._

  @Theory
  def shouldGenerateSequenceKeyThatIncludesDetectionTime(as:AggregationScenario) {
    val s = assumeDetectionTimeScenario(as)

    val key = DifferenceAggregationCachePolicy.sequenceKeyForDetectionTime(p1, now, s.detected)

    if (key.start != null) assertTrue("%s not in range (%s -> %s)".format(s.detected, key.start, key.end), !s.detected.isBefore(key.start))
    if (key.end != null) assertTrue("%s not in range (%s -> %s)".format(s.detected, key.start, key.end), s.detected.isBefore(key.end))
  }

  @Theory
  def shouldGenerateAppropriateSequenceKeyForDetectionTime(as:AggregationScenario) {
    val s = assumeDetectionTimeScenario(as)

    assertEquals(
      SequenceCacheKey(p1, s.expectedStart, s.expectedEnd),
      DifferenceAggregationCachePolicy.sequenceKeyForDetectionTime(p1, now, s.detected)
    )
  }

  @Theory
  def shouldGenerateAppropriateSequenceKeysForInterval(as:AggregationScenario) {
    val s = assumeIntervalScenario(as)

    assertEquals(
      s.intervals.map { case (start, end) => SequenceCacheKey(p1, start, end) },
      DifferenceAggregationCachePolicy.sequenceKeysForDetectionTimeRange(p1, now, s.start, s.end)
    )
  }

  def assumeDetectionTimeScenario(s:AggregationScenario) = {
    assumeThat(s, is(instanceOf(classOf[KeyForDetectionTimeScenario])))
    s.asInstanceOf[KeyForDetectionTimeScenario]
  }
  def assumeIntervalScenario(s:AggregationScenario) = {
    assumeThat(s, is(instanceOf(classOf[KeysForIntervalScenario])))
    s.asInstanceOf[KeysForIntervalScenario]
  }
}
object DifferenceAggregationCachePolicyTest {
  trait AggregationScenario

  // Scenario for determining key for a single detection time
  case class KeyForDetectionTimeScenario(expectedStart:DateTime, expectedEnd:DateTime, detected:DateTime)
    extends AggregationScenario

  // Scenario for determining keys for a time range
  case class KeysForIntervalScenario(intervals:Seq[(DateTime, DateTime)], start:DateTime, end:DateTime)
    extends AggregationScenario

  val p1 = PairRef(space = 500L, name = "p1")

  val now = new DateTime(2012, 4, 23, 8, 30, 0, DateTimeZone.UTC)
  val currentHour = now.withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)

  def i(start:DateTime, end:DateTime):(DateTime, DateTime) = (start, end)

    //
    // Single detection time to key allocation tests
    //

  // After now should be within a global after bucket
  @DataPoint def afterNowShouldBeWithinGlobalAfter =
    KeyForDetectionTimeScenario(currentHour.plusHours(1), null, now.plusHours(1))

  // Within the first 4 hours should be single hour buckets
  @DataPoint def withinCurrentHourShouldBeSingleHour =
    KeyForDetectionTimeScenario(currentHour, currentHour.plusHours(1), now.minusMinutes(1))
  @DataPoint def startOfHourShouldBeWithinCurrentHour =
    KeyForDetectionTimeScenario(currentHour, currentHour.plusHours(1), currentHour)
  @DataPoint def twoHoursAgoShouldBeSingleHour =
    KeyForDetectionTimeScenario(currentHour.minusHours(2), currentHour.minusHours(1), now.minusHours(2))
  @DataPoint def threeHoursAgoShouldBeSingleHour =
    KeyForDetectionTimeScenario(currentHour.minusHours(3), currentHour.minusHours(2), now.minusHours(3))

  // Within the range 4-28 hours, should be stored within the same day value
  @DataPoint def fourHoursAgoShouldBeAWholeDay =
    KeyForDetectionTimeScenario(currentHour.minusHours(3).minusDays(1), currentHour.minusHours(3), now.minusHours(4))
  @DataPoint def sixHoursAgoShouldBeAWholeDay =
    KeyForDetectionTimeScenario(currentHour.minusHours(3).minusDays(1), currentHour.minusHours(3), now.minusHours(6))
  @DataPoint def twentySevenHoursAgoShouldBeAWholeDay =
    KeyForDetectionTimeScenario(currentHour.minusHours(3).minusDays(1), currentHour.minusHours(3), now.minusHours(27))

  // Within the range 28-52 hours, should be stored within the same day value
  @DataPoint def twentyEightHoursAgoShouldBeAWholeDay =
    KeyForDetectionTimeScenario(currentHour.minusHours(3).minusDays(2), currentHour.minusHours(3).minusDays(1), now.minusHours(28))
  @DataPoint def fiftyOneHoursAgoShouldBeAWholeDay =
    KeyForDetectionTimeScenario(currentHour.minusHours(3).minusDays(2), currentHour.minusHours(3).minusDays(1), now.minusHours(51))

  // Anything at or before fifty two hours should be stored in a single global cache
  @DataPoint def fiftyTwoHoursAgoShouldBeGlobalBefore =
    KeyForDetectionTimeScenario(null, currentHour.minusHours(3).minusDays(2), now.minusHours(52))
  @DataPoint def oneHundredHoursAgoShouldBeGlobalBefore =
    KeyForDetectionTimeScenario(null, currentHour.minusHours(3).minusDays(2), now.minusHours(100))

    //
    // Interval to key allocation tests
    //

  @DataPoint def openRangeShouldReturnAllKeys =
    KeysForIntervalScenario(
      Seq(
        i(currentHour.plusHours(1), null),
        i(currentHour, currentHour.plusHours(1)),
        i(currentHour.minusHours(1), currentHour),
        i(currentHour.minusHours(2), currentHour.minusHours(1)),
        i(currentHour.minusHours(3), currentHour.minusHours(2)),
        i(currentHour.minusHours(27), currentHour.minusHours(3)),
        i(currentHour.minusHours(51), currentHour.minusHours(27)),
        i(null, currentHour.minusHours(51))
      ),
      null, null)
  @DataPoint def withinCurrentHourShouldReturnSingleHour =
    KeysForIntervalScenario(
      Seq(
        i(currentHour, currentHour.plusHours(1))
      ),
      now.minusMinutes(5), now.plusMinutes(10))
  @DataPoint def boundsOfCurrentHourShouldReturnSingleHour =
    KeysForIntervalScenario(
      Seq(
        i(currentHour, currentHour.plusHours(1))
      ),
      currentHour, currentHour.plusHours(1))
  @DataPoint def previousHourToThisHourShouldReturnTwoHourEntries =
    KeysForIntervalScenario(
      Seq(
        i(currentHour, currentHour.plusHours(1)),
        i(currentHour.minusHours(1), currentHour)
      ),
      now.minusHours(1), now)
  @DataPoint def boundsOfTwoRecentHoursShouldReturnTwoHourEntries =
    KeysForIntervalScenario(
      Seq(
        i(currentHour.minusHours(2), currentHour.minusHours(1)),
        i(currentHour.minusHours(3), currentHour.minusHours(2))
      ),
      currentHour.minusHours(3), currentHour.minusHours(1))
  @DataPoint def dayOldHourShouldReturnDayEntry =
    KeysForIntervalScenario(
      Seq(
        i(currentHour.minusHours(27), currentHour.minusHours(3))
      ),
      currentHour.minusHours(24), currentHour.minusHours(23))
}
