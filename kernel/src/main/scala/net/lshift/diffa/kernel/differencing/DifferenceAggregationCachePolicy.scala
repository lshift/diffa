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

import net.lshift.diffa.kernel.config.DiffaPairRef
import org.joda.time.{Hours, DateTime}

/**
 * Policy for calculating the SequenceCacheKeys relevant for various detection times.
 */
object DifferenceAggregationCachePolicy {
  // The aggregation hour ranges. These values are offsets in hours backwards from now. -1 indicates that there is no
  // bound, and it should be treated as open ended.
  val aggregationHours = Seq(
    (-1, 0),                          // There will be a single global 'after now' cache
    (0, 1), (1, 2), (2, 3), (3, 4),   // 4 x 1 hour windows
    (4, 28), (28, 52),                // 2 x 24 hour windows
    (52, -1)                          // There will be a single global 'before' cache
  )
    // Work out how many hours before now the open-ended before cache starts
  val startOfBefore = aggregationHours.last._2

  def sequenceKeyForDetectionTime(pair:DiffaPairRef, now:DateTime, detectedAt:DateTime):SequenceCacheKey = {
    val nowHour = floorHour(now)
    val rangeIdx = aggregationRangeIdxForDetectionTime(nowHour, detectedAt)

    buildKey(pair, nowHour, aggregationHours(rangeIdx))
  }

  def sequenceKeysForDetectionTimeRange(pair:DiffaPairRef, now:DateTime, rangeStart:DateTime, rangeEnd:DateTime):Seq[SequenceCacheKey] = {
    val nowHour = floorHour(now)

    // Work out the start index in our list of aggregation hours
    val startIdx = rangeEnd match {
      case null => 0
      case s    => aggregationRangeIdxForDetectionTime(nowHour, s, upperBound = true)
    }
    val endIdx = rangeStart match {
      case null => aggregationHours.length - 1
      case e    => aggregationRangeIdxForDetectionTime(nowHour, e)
    }

    (startIdx to endIdx).map(idx => buildKey(pair, nowHour, aggregationHours(idx)))
  }

  /** Works out which aggregation hour item should be used to cover the given detection time **/
  private def aggregationRangeIdxForDetectionTime(nowHour:DateTime, detectedAt:DateTime, upperBound:Boolean = false):Int = {
    // If we're looking for an upper bound, then round up to the nearest hour, then take an hour. This is almost the
    // same as taking the floorHour, except that if the value is exactly on the hour boundary, it will be taken to the
    // previous hour - which is desired since our upper bounds are treated as non-inclusive.
    val detectionHour = if (upperBound) ceilHour(detectedAt).minusHours(1) else floorHour(detectedAt)

    if (detectionHour.isAfter(nowHour)) {
      0
    } else {
      val offsetHours = Hours.hoursBetween(detectionHour, nowHour).getHours
      val idx = aggregationHours.indexWhere { case (sah, eah) => eah > offsetHours }
      if (idx == -1) {
        aggregationHours.length - 1
      } else {
        idx
      }
    }
  }

  private def buildKey(pair:DiffaPairRef, nowHour:DateTime, aggregationRange:(Int, Int)) = {
    aggregationRange match {
      case (-1, end) =>
        SequenceCacheKey(pair, nowHour.minusHours(end - 1), null)
      case (start, -1) =>
        SequenceCacheKey(pair, null, nowHour.minusHours(start - 1))
      case (startOffset, endOffset) =>
        SequenceCacheKey(pair, nowHour.minusHours(endOffset - 1), nowHour.minusHours(startOffset - 1))
    }
  }

  /** Round a time up to its ending hour */
  private def ceilHour(t:DateTime) = {
    val hourStart = t.withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
    if (hourStart == t) {
      t   // The time was already on an hour boundary. Don't touch it.
    } else {
      hourStart.plusHours(1)  // Push forward to the next hour, since we removed some time when we removed the mins/secs/millis
    }
  }
  private def floorHour(t:DateTime) = {
    if (t == null) null
    else t.withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
  }
}