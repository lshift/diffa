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

import net.lshift.diffa.kernel.events.VersionID
import org.joda.time.{Interval, DateTime}
import net.lshift.diffa.kernel.config.DiffaPairRef
import reflect.BeanProperty

/**
 * The domain cache provides facilities for storing difference events that occur, and managing the states of these
 * events. A domain cache instance should exist for each domain that has been created in the system.
 */
trait DomainDifferenceStore {
  /**
   * Indicates that the given domain has been removed, and that any differences stored against it should be removed.
   */
  def removeDomain(domain: String)

  /**
   * Indicates that the given pair has been removed, and that any differences stored against it should be removed.
   */
  def removePair(pair: DiffaPairRef)

  /**
   * Retrieves the current sequence id of the cache
   */
  def currentSequenceId(domain:String):String

  /**
   * Adds a pending event for the given version id into the cache.
   */
  def addPendingUnmatchedEvent(id:VersionID, lastUpdate:DateTime, upstreamVsn:String, downstreamVsn:String, seen:DateTime)

  /**
   * Adds a reportable unmatched event for the given version id into the cache. Returns the detail of the event
   * (including a sequence id). Any previous matched event for the same id will be removed.
   */
  def addReportableUnmatchedEvent(id:VersionID, lastUpdate:DateTime, upstreamVsn:String, downstreamVsn:String, seen:DateTime):DifferenceEvent

  /**
   * Upgrades the given pending event to a reportable event. Returns the detail of the event (including a sequence id).
   * Any previous matched event for the same id will be removed. If no event is available to upgrade with the given
   * id, then null will be returned.
   */
  def upgradePendingUnmatchedEvent(id:VersionID):DifferenceEvent

  /**
   * Indicates that a given pending event is no longer valid - we've received a match, so we won't be upgrading it.
   */
  def cancelPendingUnmatchedEvent(id:VersionID, vsn:String):Boolean

  /**
   * Adds a matched event to the cache. This will result in the removal of any earlier unmatched event for the same id.
   * The matched event will also be marked for expiry at some interval defined by the cache implementation, ensuring
   * that matched events do not result in the cache becoming full.
   */
  def addMatchedEvent(id:VersionID, vsn:String):DifferenceEvent

  /**
   * Removes the events identified by these versions from the store
   */
  def removeEvents(events:Iterable[VersionID])

  /**
   * Indicates that all differences in the cache older than the given date should be marked as matched. This is generally
   * invoked upon a scan being completed, and allows for events that have disappeared to be removed.
   */
  def matchEventsOlderThan(pair:DiffaPairRef, cutoff: DateTime)

  /**
   * Indicates that the given event should be ignored, and not returned in any query. Returns the regenerated object
   * that is marked as ignored.
   */
  def ignoreEvent(domain:String, seqId:String): DifferenceEvent

  /**
   * Indicates that the given event should no longer be ignored. The event with the given sequence id will be removed,
   * and a new event with the same details generated - this ensures that consumers that are monitoring for updates will
   * see a new sequence id appear.
   */
  def unignoreEvent(domain:String, seqId:String): DifferenceEvent

  /**
   * Returns the last correlation version that was transferred to the diffs store
   */
  def lastRecordedVersion(pair:DiffaPairRef) : Option[Long]

  /**
   * Removes the entry for the latest correlation store that was recorded with the diffs store.
   */
  def removeLatestRecordedVersion(pair:DiffaPairRef)

  /**
   * Registers the latest correlation store version with the diff store.
   */
  def recordLatestVersion(pair:DiffaPairRef, version:Long)

  /**
   * Retrieves all unmatched events in the domain that have been added to the cache where their detection timestamp
   * falls within the specified period
   */
  def retrieveUnmatchedEvents(domain:String, interval:Interval) : Seq[DifferenceEvent]

  /**
   * Streams all unmatched events for the given pair to a provided handler.
   */
  def streamUnmatchedEvents(pairRef:DiffaPairRef, handler:(ReportedDifferenceEvent) => Unit)

  /**
   * Applies a closure to all unmatched events for the given pair whose detection timestamp falls into the supplied time bound
   */
  @Deprecated
  def retrieveUnmatchedEvents(domain:DiffaPairRef, interval:Interval, f:ReportedDifferenceEvent => Unit)

  /**
   * Returns an aggregate of all unmatched events for the given pair whose detection timestamp falls into the supplied time bound.
   * The results are grouped by date intervals according to the desired zoom level.
   */
  def aggregateUnmatchedEvents(pair:DiffaPairRef, interval:Interval, zoomLevel:Int) : Seq[AggregateEvents]

  /**
   * Retrieves all unmatched events that have been added to the cache that have a detection time within the specified
   * interval. The result return a range of the underlying data set that corresponds to the offset and length
   * supplied.
   */
  def retrievePagedEvents(pair: DiffaPairRef, interval:Interval, offset:Int, length:Int, options:EventOptions = EventOptions()) : Seq[DifferenceEvent]

  /**
   * Count the number of unmatched events for the given pair within the given interval.
   */
  def countUnmatchedEvents(pair: DiffaPairRef, interval:Interval) : Int

  /**
   * Retrieves all events that have occurred within a domain since the provided sequence id.
   * @param evtSeqId the last known sequence id. All events occurring after (not including) this event will be returned.
   * @throws SequenceOutOfDateException if the provided sequence id is too old, and necessary scan information cannot be
   *    provided. A client will need to recover by calling retrieveAllEvents and re-process all events.
   */
  def retrieveEventsSince(domain:String, evtSeqId:String):Seq[DifferenceEvent]

  /**
   * Retrieves a single event by its id.
   * @param evtSeqId sequence id of the event to be retrieved.
   * @throws InvalidSequenceNumberException if the requested sequence id does not exist or has expired.
   */
  def getEvent(domain:String, evtSeqId:String) : DifferenceEvent

  /**
   * Retrieves pre-set group of tiles that is aligned on the requested timestamp.
   * @pair The pair to retrieve tiles for
   * @zoomLevel The level that the tiles should be zoomed into
   * @timestamp The (aligned) start time of the requested group of tiles
   */
  def retrieveEventTiles(pair:DiffaPairRef, zoomLevel:Int, timestamp:DateTime) : Option[TileGroup]

  /**
   * Indicates that matches older than the given cutoff (based on their seen timestamp) should be removed.
   */
  def expireMatches(cutoff:DateTime)

}

case class TileGroup(
  lowerBound:DateTime,
  tiles:Map[DateTime,Int]
)

case class EventOptions(
  includeIgnored:Boolean = false    // Whether ignored events should be included in the response
)

case class AggregateEvents(
  interval:Interval,
  count:Int
)

case class ReportedDifferenceEvent(
  @BeanProperty var seqId:java.lang.Integer = null,
  @BeanProperty var objId:VersionID = null,
  @BeanProperty var detectedAt:DateTime = null,
  @BeanProperty var isMatch:Boolean = false,
  @BeanProperty var upstreamVsn:String = null,
  @BeanProperty var downstreamVsn:String = null,
  @BeanProperty var lastSeen:DateTime = null,
  @BeanProperty var ignored:Boolean = false
) {

  def this() = this(seqId = null)

  def asDifferenceEvent = DifferenceEvent(seqId.toString, objId, detectedAt, state, upstreamVsn, downstreamVsn, lastSeen)
  def state = if (isMatch) {
      MatchState.MATCHED
    } else {
      if (ignored) {
        MatchState.IGNORED
      } else {
        MatchState.UNMATCHED
      }

    }
}
