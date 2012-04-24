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

import org.joda.time.{Duration, DateTimeZone, Interval, DateTime}

/**
 * Mapping between numeric zoom levels and time ranges.
 */
object ZoomLevels {
  /**
   * A bunch of enums that define what factor of zoom is understood by each level
   */
  val QUARTER_HOURLY = 6
  val HALF_HOURLY = 5
  val HOURLY = 4
  val TWO_HOURLY = 3
  val FOUR_HOURLY = 2
  val EIGHT_HOURLY = 1
  val DAILY = 0

  val levels = DAILY.to(QUARTER_HOURLY)

  /**
   * A lookup table that indicates how many minutes are in a tile at a particular level of zoom.
   */
  private val zoom = Map(
    DAILY -> 60 * 24,
    EIGHT_HOURLY -> 60 * 8,
    FOUR_HOURLY -> 60 * 4,
    TWO_HOURLY -> 60 * 2,
    HOURLY -> 60,
    HALF_HOURLY -> 30,
    QUARTER_HOURLY -> 15
  )

  def lookupZoomLevel(level:Int) = {
    if (level < DAILY || level > QUARTER_HOURLY) {
      throw new InvalidZoomLevelException(level)
    }

    zoom(level)
  }

  def containingTileGroupInterval(timestamp:DateTime, zoomLevel:Int) : Interval = {
    val utc = timestamp.withZone(DateTimeZone.UTC)
    zoomLevel match {
      case QUARTER_HOURLY =>
        val hour = utc.getHourOfDay
        if (hour < 8) {
          val start = utc.withTimeAtStartOfDay()
          val end = utc.withHourOfDay(8).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
          new Interval(start, end)
        } else if (hour < 16) {
            val start = utc.withHourOfDay(8).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
            val end = utc.withHourOfDay(16).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
            new Interval(start, end)
        } else {
            val start = utc.withHourOfDay(16).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
            val end = utc.dayOfYear().roundCeilingCopy()
            new Interval(start, end)
        }
     case HALF_HOURLY =>
        val hour = utc.getHourOfDay
        if (hour < 12) {
          val start = utc.withTimeAtStartOfDay()
          val end = utc.withHourOfDay(12).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
          new Interval(start, end)
        } else {
            val start = utc.withHourOfDay(12).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
            val end = utc.dayOfYear().roundCeilingCopy()
            new Interval(start, end)
        }
      case _  =>
        val start = utc.withTimeAtStartOfDay()
        val end = start.plusDays(1)
        new Interval(start, end)
    }
  }

  def containingTileGroupEdges(interval:Interval, zoomLevel:Int) : Seq[DateTime] = zoomLevel match {
    case QUARTER_HOURLY | HALF_HOURLY => alignToSubDayBoundary(interval, zoomLevel)
    case _                            => alignToDayBoundary(interval)
  }

  def alignInterval(interval:Interval, zoomLevel:Int) = {
    val alignedStart = containingInterval(interval.getStart, zoomLevel)
    val alignedEnd = containingInterval(interval.getEnd, zoomLevel)
    new Interval(alignedStart.getStart, alignedEnd.getStart)
  }

  /**
   * Returns a flat list of the tile starting times in the given interval at the specified zoom level
   */
  def individualTileEdges(interval:Interval, zoomLevel:Int) : Seq[DateTime] = {
    val alignedInterval = alignInterval(interval, zoomLevel)
    slice(alignedInterval.toDuration, alignedInterval.getStart, zoomLevel)
  }

  private def alignToSubDayBoundary(interval:Interval, zoomLevel:Int) = {
    val multiple = zoomLevel match {
      case QUARTER_HOURLY => 8
      case HALF_HOURLY    => 12
    }
    val start = subDayAlignedTimestamp(interval.getStart, multiple).withZone(DateTimeZone.UTC)
    val end = subDayAlignedTimestamp(interval.getEnd, multiple).withZone(DateTimeZone.UTC)
    val hours = new Duration(start,end).getStandardHours.intValue()
    val divisions = (hours / multiple)
    0.to(divisions).map(d => start.plusHours(d * multiple).withZone(DateTimeZone.UTC))
  }

  private def alignToDayBoundary(interval:Interval) = {
    val startTile = interval.getStart.withTimeAtStartOfDay()
    val endTile = interval.getEnd.dayOfYear().roundCeilingCopy()
    val days = new Duration(startTile,endTile).getStandardDays.intValue()
    0.until(days).map(d => startTile.plusDays(d).withZone(DateTimeZone.UTC))
  }

  /**
   * Calculates what the tile aligned interval contains the given timestamp at the given level of zoom
   */
  def containingInterval(timestamp:DateTime, zoomLevel:Int) = {
    val utc = timestamp.withZone(DateTimeZone.UTC)
    zoomLevel match {
      case DAILY => {
        val start = utc.withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
        new Interval(start, start.plusDays(1))
      }
      case EIGHT_HOURLY => multipleHourlyStart(utc, 8)
      case FOUR_HOURLY => multipleHourlyStart(utc, 4)
      case TWO_HOURLY => multipleHourlyStart(utc, 2)
      case HOURLY => multipleHourlyStart(utc, 1)
      case HALF_HOURLY => subHourly(utc, 30)
      case QUARTER_HOURLY => subHourly(utc, 15)
    }
  }

  private def subHourly(timestamp:DateTime, minutes:Int) = {
    val bottomOfHour = timestamp.withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
    val increments = timestamp.getMinuteOfHour / minutes
    val start = bottomOfHour.plusMinutes( increments * minutes )
    new Interval(start, start.plusMinutes(minutes))
  }

  private def multipleHourlyStart(timestamp:DateTime, multiple:Int) = {
    val start = subDayAlignedTimestamp(timestamp, multiple)
    new Interval(start, start.plusHours(multiple))
  }

  private def subDayAlignedTimestamp(timestamp:DateTime, multiple:Int) = {
    val utc = timestamp.withZone(DateTimeZone.UTC)
    val hourOfDay = utc.getHourOfDay
    val alignedHour = hourOfDay - (hourOfDay % multiple)
    utc.withHourOfDay(alignedHour).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
  }

  private def slice(d:Duration, startTime:DateTime, zoomLevel:Int) : Seq[DateTime] = {
    val minutes = d.getStandardMinutes.intValue()
    val divisions = minutes / zoom(zoomLevel)
    0.to(divisions).map(d => startTime.plusMinutes(d * zoom(zoomLevel)))
  }
}

class InvalidZoomLevelException(val level:Int) extends Exception("Zoom level: " + level)