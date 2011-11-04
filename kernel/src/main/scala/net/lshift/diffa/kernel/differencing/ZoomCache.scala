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

import collection.mutable.{HashMap,HashSet}
import net.lshift.diffa.kernel.config.DiffaPairRef
import scala.collection.JavaConversions._
import net.sf.ehcache.CacheManager
import java.io.Closeable
import net.lshift.diffa.kernel.util.CacheWrapper
import org.joda.time._

/**
 * This provides a cache of difference events that have been summarized into a tile shaped structure according
 * to various levels of zooming. This component is intended primarily as an internal component of the
 * HibernateDomainDifferenceStore as opposed to general usage, but has been extracted as a separate component
 * in order to make it more testable.
 *
 * The cache is scoped on a particular pair, so all operations on it are understood to execute within that context.
 */
trait ZoomCache extends Closeable {

  /**
   * Callback to notify the cache that it should synchronize a particular time span with the underlying difference store.
   *
   * @param detectionTime The detection time of the event that is causing the cache to invalidate itself
   */
  def onStoreUpdate(pair:DiffaPairRef, detectionTime:DateTime)

  /**
   * Retrieves a set of tiles for the current pair with a given time frame.
   *
   * @param zoomLevel The requested level of zoom
   * @param timespan The time range to retrieve tiles for
   */
  def retrieveTilesForZoomLevel(pair:DiffaPairRef, zoomLevel:Int, timestamp:DateTime) : Option[TileGroup]

  /**
   * Removes all elements from this cache
   */
  def clear
}

/**
 * This provider exploits the cache management functionality provided by the EhCacheManager, including the
 * ability to evict cached entries based on heap usage, access patterns and the number of elements cached across
 * the entire system.
 */
class ZoomCacheProvider(diffStore:DomainDifferenceStore,
                        cacheManager:CacheManager) extends ZoomCache {

  import ZoomCache._

  /**
   * A bit set of flags to mark each tile as dirty on a per-tile basis
   */
  private val dirtyTilesByLevel = new CacheWrapper[TileGroupKey, HashSet[DateTime]]("dirtytiles", cacheManager)

  /**
   * Cache of indexed tiles for each requested level
   */
  private val tileCachesByLevel = new CacheWrapper[TileGroupKey, HashMap[DateTime,Int]]("tilegroups", cacheManager)

  def clear = {
    dirtyTilesByLevel.clear()
    tileCachesByLevel.clear()
  }

  def close() = {
    dirtyTilesByLevel.close()
    tileCachesByLevel.close()
  }

  case class TileGroupKey(pair:DiffaPairRef, zoomLevel:Int, timestamp:DateTime)

  /**
   * Marks the tile (on each cached level) that corresponds to this version as dirty
   */
  def onStoreUpdate(pair:DiffaPairRef, detectionTime:DateTime) = {
    // TODO have a look to see if this traversal is expensive at some stage
    dirtyTilesByLevel.keys.filter(_.pair == pair).foreach(key => {
      val tileGroupInterval = containingTileGroupInterval(detectionTime, key.zoomLevel)
      val cacheKey = TileGroupKey(pair, key.zoomLevel, tileGroupInterval.getStart)
      val individualTileInterval = containingInterval(detectionTime, key.zoomLevel)
      dirtyTilesByLevel.get(cacheKey) match {
        case Some(d) => d += individualTileInterval.getStart
        case None    => // ignore
      }
    })
  }

  def retrieveTilesForZoomLevel(pair:DiffaPairRef, zoomLevel:Int, groupStart:DateTime) : Option[TileGroup] = {

    validateLevel(zoomLevel)

    val alignedTimespan = containingTileGroupInterval(groupStart, zoomLevel)
    val alignedGroupStart = alignedTimespan.getStart
    val cacheKey = TileGroupKey(pair, zoomLevel, alignedGroupStart)

    val tileCache = tileCachesByLevel.get(cacheKey) match {
      case Some(cached) => cached
      case None         =>
        val cache = new HashMap[DateTime,Int]
        tileCachesByLevel.put(cacheKey, cache)

        // Build up an initial cache - after the cache has been primed, it with be invalidated in an event
        // driven fashion. Iterate through the diff store to generate aggregate sums of the events in tile
        // aligned buckets

        diffStore.aggregateUnmatchedEvents(pair, alignedTimespan, zoomLevel).foreach(aggregate => {
          cache(aggregate.interval.getStart) = aggregate.count
        })

        // TODO Deliberately leave old code here until the new approach has proven itself in production, also
        // take down a ticket to clean this up and potentially delete the retrieveUnmatchedEvents/3 call

//        diffStore.retrieveUnmatchedEvents(pair, alignedTimespan, (event:ReportedDifferenceEvent) => {
//          val intervalStart = containingInterval(event.detectedAt, zoomLevel).getStart
//          cache.get(intervalStart) match {
//            case Some(n) => cache(intervalStart) += 1
//            case None    => cache(intervalStart)  = 1
//          }
//        })

        // Initialize a dirty flag set for this cache
        dirtyTilesByLevel.put(cacheKey, new HashSet[DateTime])

        cache
    }

    dirtyTilesByLevel.get(cacheKey) match {
      case None        => // The tile cache does not need to be preened
      case Some(flags) =>
        flags.map(startTime => {
          val interval = intervalFromStartTime(startTime, zoomLevel)
          diffStore.countUnmatchedEvents(pair, interval) match {
            case 0 => tileCache            -= startTime  // Remove the cache entry if there are no events
            case n => tileCache(startTime) = n
          }
        })
        flags.clear()
     }

    Some(TileGroup(alignedGroupStart, tileCache.toMap))
  }

}

/**
 * Provides generically testable definitions and functions for computing tile bounds
 */
object ZoomCache {

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
  val zoom = Map(
    DAILY -> 60 * 24,
    EIGHT_HOURLY -> 60 * 8,
    FOUR_HOURLY -> 60 * 4,
    TWO_HOURLY -> 60 * 2,
    HOURLY -> 60,
    HALF_HOURLY -> 30,
    QUARTER_HOURLY -> 15
  )

  /**
   * Returns a sequence of the start times for the groups of tiles required to build a projection
   * for the given interval at the specified level of zoom.
   */
  def containingTileGroupEdges(interval:Interval, zoomLevel:Int) : Seq[DateTime] = zoomLevel match {
    case QUARTER_HOURLY | HALF_HOURLY => alignToSubDayBoundary(interval, zoomLevel)
    case _                            => alignToDayBoundary(interval)
  }

  // TODO Unit test
  // TODO Refactor, make neater
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
   * Returns a flat list of the tile starting times in the given interval at the specified zoom level
   */
  def individualTileEdges(interval:Interval, zoomLevel:Int) : Seq[DateTime] = {
    val alignedInterval = alignInterval(interval, zoomLevel)
    slice(alignedInterval.toDuration, alignedInterval.getStart, zoomLevel)
  }

  // TODO document
  def alignInterval(interval:Interval, zoomLevel:Int) = {
    val alignedStart = containingInterval(interval.getStart, zoomLevel)
    val alignedEnd = containingInterval(interval.getEnd, zoomLevel)
    new Interval(alignedStart.getStart, alignedEnd.getStart)
  }

  private def slice(d:Duration, startTime:DateTime, zoomLevel:Int) : Seq[DateTime] = {
    val minutes = d.getStandardMinutes.intValue()
    val divisions = minutes / zoom(zoomLevel)
    0.to(divisions).map(d => startTime.plusMinutes(d * zoom(zoomLevel)))
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

  /**
   * Calculates what the tile aligned interval is having the specified starting timestamp and the given level of zoom
   */
  def intervalFromStartTime(start:DateTime, zoomLevel:Int) = {
    val end = start.withZone(DateTimeZone.UTC).plusMinutes(zoom(zoomLevel))
    new Interval(start,end)
  }

  def validateLevel(level:Int) = if (level < DAILY || level > QUARTER_HOURLY) {
    throw new InvalidZoomLevelException(level)
  }

}

class InvalidZoomLevelException(val level:Int) extends Exception("Zoom level: " + level)