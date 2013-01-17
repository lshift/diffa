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
import reflect.BeanProperty
import org.joda.time.{Interval, DateTime}
import net.lshift.diffa.kernel.config.{PairRef, DiffaPairRef}
import java.util.HashMap

/**
 * A DifferencesManager provides a stateful view of the differencing of pairs, and provides mechanisms for polling
 * for changes in the difference states of those pairs.
 */
trait DifferencesManager {
  /**
   * Creates a writer for recording differences. The writer can be optionally opened in overwrite mode. If this is done,
   * then when the writer is closed, any difference not seen within the scope of the writer will be marked as matched.
   */
  def createDifferenceWriter(space:Long, pair:String, overwrite:Boolean):DifferenceWriter

  /**
   * Retrieves a version for the given domain.
   */
  def retrieveDomainSequenceNum(space:Long):String

  /**
   * Ignores the given difference.
   */
  def ignoreDifference(space:Long, seqId:String):DifferenceEvent

  /**
   * Unignores the given difference.
   */
  def unignoreDifference(space:Long, seqId:String):DifferenceEvent

  /**
   * Retrieves the version number of the last correlation to get transferred to the diff store.
   * This version number is global to the pair definition and can be used as a synchonization checkpoint.
   */
  def lastRecordedVersion(pair:PairRef) : Option[Long]

  /**
   * Retrieves aggregated count for the events between the given start and end time, for the given pair. Optionally
   * subdivides the accounts at intervals, as specified by the aggregateMinutes parameter.
   */
  def retrieveAggregates(pair:PairRef, start:DateTime, end:DateTime, aggregation:Option[Int]):Seq[AggregateTile]

  /**
   *
   * Retrieves all events known to this domain. Will only include unmatched events.
   * This pages the results to only contain events that are contained with the specified interval
   * and returns a subset of the underlying data set that corresponds to the offset and length specified.
   * @throws MissingObjectException if the requested domain does not exist
   */
  def retrievePagedEvents(space:Long, pairKey:String, interval:Interval, offset:Int, length:Int, options:EventOptions) : Seq[DifferenceEvent]

  /**
   * Count the number of events for the given pair within the given interval.
   * @throws MissingObjectException if the requested domain does not exist
   */
  def countEvents(space:Long, pairKey:String, interval:Interval) : Int

  /**
   * Retrieves any additional information that the differences manager knows about an event (eg, mismatched hashes,
   * differing content bodies). This information may be retrieved by the manager on demand from remote sources, so
   * should generally only be called on explicit user request.
   * @throws MissingObjectException if the requested domain does not exist
   */
  //def retrieveEventDetail(space:Long, evtSeqId:String, t:ParticipantType.ParticipantType) : String

  /**
   * Informs the difference manager that a pair has been updated.
   */
  def onUpdatePair(pairRef: PairRef)

  /**
   * Informs the difference manager that a pair has been deleted.
   */
  def onDeletePair(pairRef: PairRef)

  /**
   * Informs the difference manager that a domain has been updated.
   */
  def onUpdateDomain(space:Long)

  /**
   * Informs the difference manager that a domain has been deleted.
   */
  def onDeleteDomain(space:Long)
}

/**
 * A Difference writer provides a stateful batch mechanism for ingesting a series of differences.
 */
trait DifferenceWriter {
  /**
   * Records a mismatch.
   */
  def writeMismatch(id:VersionID, lastUpdated:DateTime, upstreamVsn:String, downstreamVsn:String, origin:MatchOrigin, storeVersion:Long)

  /**
   * Indicates that the following correlations are tombstones and differences associated with them can be deleted
   */
  //def evictTombstones(tombstones:Iterable[Correlation])

  /**
   * Aborts this difference writer. Any locks held by this writer will be released (as per the close method), but no
   * overwrite behaviour will be performed. This allows a consumer that had opened a write session to handle an exception
   * case where it is no longer able to generate differences.
   */
  def abort()

  /**
   * Closes the writer. If the writer was opened in overwrite mode, then any difference within the owning manager that
   * hasn't been seen during the scope of this writer will be marked as matched.
   */
  def close()
}

class InvalidSequenceNumberException(val id:String) extends Exception(id)
class SequenceOutOfDateException extends Exception

case class DifferenceEvent(
  @BeanProperty var seqId:String = null,
  @BeanProperty var objId:VersionID = null,
  @BeanProperty var detectedAt:DateTime = null,
  @BeanProperty var state:MatchState = null,
  @BeanProperty var upstreamVsn:String = null,
  @BeanProperty var downstreamVsn:String = null,
  @BeanProperty var lastSeen:DateTime = null,
  @BeanProperty var nextEscalation:String = null,
  @BeanProperty var nextEscalationTime:DateTime = null) {

  def this() = this(seqId = null)

  def sequenceId = seqId.toLong

  /** Proxy to the object id. Added to allow escalation rules like "id LIKE 'id123*'" to be applied. **/
  def id = objId.id

  def toExternalFormat = ExternalDifferenceEvent(
    seqId = seqId,
    pair = objId.pair.name,
    entityId = objId.id,
    detectedAt = detectedAt,
    state = state,
    upstreamVsn = upstreamVsn,
    downstreamVsn = downstreamVsn
  )
}



/**
 * This is a simplified representation of a difference event sent to clients.
 */
case class ExternalDifferenceEvent(
    @BeanProperty var seqId:String = null,
    @BeanProperty var pair:String = null,
    @BeanProperty var entityId:String = null,
    @BeanProperty var detectedAt:DateTime = null,
    @BeanProperty var state:MatchState = null,
    @BeanProperty var upstreamVsn:String = null,
    @BeanProperty var downstreamVsn:String = null) {

  def this() = this(seqId = null)
}

  abstract class DifferenceEventStatus
case object NewUnmatchedEvent extends DifferenceEventStatus
case object ReturnedUnmatchedEvent extends DifferenceEventStatus
case object UpdatedUnmatchedEvent extends DifferenceEventStatus
case object UnchangedUnmatchedEvent extends DifferenceEventStatus
case object UpdatedIgnoredEvent extends DifferenceEventStatus
case object UnchangedIgnoredEvent extends DifferenceEventStatus

