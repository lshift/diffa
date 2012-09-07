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
import net.lshift.diffa.kernel.config.PairRef
import reflect.BeanProperty

/**
 * The domain cache provides facilities for storing difference events that occur, and managing the states of these
 * events. A domain cache instance should exist for each domain that has been created in the system.
 */
trait DomainDifferenceStore {
  /**
   * Indicates that the given domain has been removed, and that any differences stored against it should be removed.
   */
  def removeDomain(space:Long)

  /**
   * Indicates that the given pair has been removed, and that any differences stored against it should be removed.
   */
  def removePair(pair: PairRef)

  /**
   * Retrieves the current sequence id of the cache
   */
  def currentSequenceId(space:Long):String

  /**
   * Retrieves the maximum sequence id of all events within the current time range. If no events are available within
   * the given range, 0 is returned.
   */
  def maxSequenceId(pair: PairRef, start:DateTime, end:DateTime) : Long

  /**
   * Adds a pending event for the given version id into the cache.
   */
  def addPendingUnmatchedEvent(id:VersionID, lastUpdate:DateTime, upstreamVsn:String, downstreamVsn:String, seen:DateTime)

  /**
   * Adds a reportable unmatched event for the given version id into the cache. Returns the detail of the event
   * (including a sequence id). Any previous matched event for the same id will be removed.
   */
  def addReportableUnmatchedEvent(id:VersionID, lastUpdate:DateTime, upstreamVsn:String, downstreamVsn:String, seen:DateTime):(DifferenceEventStatus,DifferenceEvent)

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
   * Indicates that the given event should be ignored, and not returned in any query. Returns the regenerated object
   * that is marked as ignored.
   */
  def ignoreEvent(space:Long, seqId:String): DifferenceEvent

  /**
   * Indicates that the given event should no longer be ignored. The event with the given sequence id will be removed,
   * and a new event with the same details generated - this ensures that consumers that are monitoring for updates will
   * see a new sequence id appear.
   */
  def unignoreEvent(space:Long, seqId:String): DifferenceEvent

  /**
   * Returns the last correlation version that was transferred to the diffs store
   */
  def lastRecordedVersion(pair:PairRef) : Option[Long]

  /**
   * Registers the latest correlation store version with the diff store.
   */
  def recordLatestVersion(pair:PairRef, version:Long)

  /**
   * Retrieves all unmatched events in the domain that have been added to the cache where their detection timestamp
   * falls within the specified period
   */
  def retrieveUnmatchedEvents(space:Long, interval:Interval) : Seq[DifferenceEvent]

  /**
   * Streams all unmatched events for the given pair to a provided handler.
   */
  def streamUnmatchedEvents(pairRef:PairRef, handler:(ReportedDifferenceEvent) => Unit)

  /**
   * Retrieves all unmatched events that have been added to the cache that have a detection time within the specified
   * interval. The result return a range of the underlying data set that corresponds to the offset and length
   * supplied.
   */
  def retrievePagedEvents(pair: PairRef, interval:Interval, offset:Int, length:Int, options:EventOptions = EventOptions()) : Seq[DifferenceEvent]

  /**
   * Count the number of unmatched events for the given pair within the given interval.
   */
  def countUnmatchedEvents(pair: PairRef, start:DateTime, end:DateTime) : Int

  /**
   * Retrieves a single event by its id.
   * @param evtSeqId sequence id of the event to be retrieved.
   * @throws InvalidSequenceNumberException if the requested sequence id does not exist or has expired.
   */
  def getEvent(space:Long, evtSeqId:String) : DifferenceEvent

  /**
   * Retrieves aggregated count for the events between the given start and end time, for the given pair. Optionally
   * subdivides the accounts at intervals, as specified by the aggregateMinutes parameter.
   */
  def retrieveAggregates(pair:PairRef, start:DateTime, end:DateTime, aggregateMinutes:Option[Int]):Seq[AggregateTile]

  /**
   * Indicates that matches older than the given cutoff (based on their seen timestamp) should be removed.
   */
  def expireMatches(cutoff:DateTime)

  /**
   * Retrieves a list of difference events that should be escalated.
   */
  def pendingEscalatees(cutoff:DateTime, callback:(DifferenceEvent) => Unit)

  /**
   * Schedules the given difference event to have the given escalation applied at the given time. Escalation for
   * the difference can be terminated by providing a null name/time.
   */
  def scheduleEscalation(diff:DifferenceEvent, escalationName:String, escalationTime:DateTime)

  /**
   * Clears any scheduled escalations for all differences in the given pair.
   */
  def unscheduleEscalations(pair:PairRef)

  /**
   * Removes any differences that have been orphaned due to pair vacuuming or deletions.
   */
  def purgeOrphanedEvents : Int
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
  @BeanProperty var seqId:Long = -1,
  @BeanProperty var extent:Long = -1,
  @BeanProperty var objId:VersionID = null,
  @BeanProperty var detectedAt:DateTime = null,
  @BeanProperty var isMatch:Boolean = false,
  @BeanProperty var upstreamVsn:String = null,
  @BeanProperty var downstreamVsn:String = null,
  @BeanProperty var lastSeen:DateTime = null,
  @BeanProperty var ignored:Boolean = false,
  @BeanProperty var nextEscalationId:Long = -1,
  @BeanProperty var nextEscalationTime:DateTime = null
) {

  def this() = this(seqId = -1)


}
