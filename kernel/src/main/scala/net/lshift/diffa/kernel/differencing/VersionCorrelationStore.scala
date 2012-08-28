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

import java.io.Closeable
import net.lshift.diffa.kernel.events.VersionID
import org.joda.time.{LocalDate, DateTimeZone, DateTime}
import org.slf4j.LoggerFactory
import net.lshift.diffa.participant.scanning.ScanConstraint
import net.lshift.diffa.kernel.config.PairRef
import net.lshift.diffa.kernel.util.{CategoryChange, EndpointSide}

/**
 * Store used for caching version correlation information between a pair of participants.
 */
trait VersionCorrelationStore extends Closeable {
  val logger = LoggerFactory.getLogger(classOf[VersionCorrelationStore])

  type UpstreamVersionHandler = (VersionID, Map[String, String], DateTime, String) => Unit
  type DownstreamVersionHandler = (VersionID, Map[String, String], DateTime, String, String) => Unit

  /**
   * The unique key for the upstream and downstream participant pair
   */
  val pair: PairRef

  /**
   * Opens a new writer, giving access to write operations on the store.
   */
  def openWriter(): ExtendedVersionCorrelationWriter

  /**
   * Retrieves all of the unmatched version that have been stored.
   * @param usConstraints constraints on the upstream participant's entities
   * @param dsConstraints constraints on the downstream participant's entities
   */
  def unmatchedVersions(usConstraints:Seq[ScanConstraint], dsConstraints:Seq[ScanConstraint], fromVersion:Option[Long]) : Iterable[Correlation]

  /*
   * Retrieves a list of versions that have been turned into tombstones since the given store version.
   */
  def tombstoneVersions(fromVersion:Option[Long]) : Iterable[Correlation]

  /**
   * Retrieves the current pairing information for the given pairKey/id.
   */
  def retrieveCurrentCorrelation(id:VersionID):Option[Correlation]

  /**
   * Queries for all upstream versions for the given pair based on the given constraints.
   */
  def queryUpstreams(constraints:Seq[ScanConstraint], handler:UpstreamVersionHandler):Unit = {
    queryUpstreams(constraints).foreach(c => {
      val version = VersionID(PairRef(c.pairing, c.space), c.id)
      val attributes = c.upstreamAttributes.toMap
      if (logger.isTraceEnabled) {
        logger.trace("US: version = %s; attributes = %s; lastUpdate = %s; uvsn = %s".format(version, attributes, c.lastUpdate, c.upstreamVsn))
      }
      handler(version, attributes, c.lastUpdate, c.upstreamVsn)
    })
  }

  /**
   * Queries for all downstream versions for the given pair based on the given constraints.
   */
  def queryDownstreams(constraints:Seq[ScanConstraint], handler:DownstreamVersionHandler) : Unit = {
    queryDownstreams(constraints).foreach(c => {
      val version = VersionID(PairRef(c.pairing, c.space), c.id)
      val attributes = c.downstreamAttributes.toMap
      if (logger.isTraceEnabled) {
        logger.trace("DS: version = %s; attributes = %s; lastUpdate = %s; uvsn = %s; dvsn = %s".format(version, attributes, c.lastUpdate, c.upstreamVsn, c.downstreamDVsn))
      }
      handler(version, c.downstreamAttributes.toMap, c.lastUpdate, c.downstreamUVsn, c.downstreamDVsn)
    })
  }

  /**
   * Queries for all upstream versions for the given pair based on the given constraints.
   */
  def queryUpstreams(constraints:Seq[ScanConstraint]) : Seq[Correlation]

  /**
   * Queries for all downstream versions for the given pair based on the given constraints.
   */
  def queryDownstreams(constraints:Seq[ScanConstraint]) : Seq[Correlation]

  /**
   * Ensures that if a given list of category changes were made for a given endpoint side, then the datastore
   * would remain in a valid state.
   * @param side the side (upstream or downstream) that the changes are proposed for;
   * @param changes a sequence of name/change pairs. A change is defined as an optional before and optional after value.
   *  A before or after being missing would mean an addition or removal, respectively.
   */
  def ensureUpgradeable(side:EndpointSide, changes:Seq[CategoryChange])
}

object VersionCorrelationStore {
  val schemaVersionKey = "correlationStore.schemaVersion"
  val currentSchemaVersion = 1
}

/**
 * Issues write commands to an underlying correlation store.
 */
trait LimitedVersionCorrelationWriter {

  /**
   *   Stores the details of an upstream version.
   */
  def storeUpstreamVersion(id:VersionID, attributes:Map[String,TypedAttribute], lastUpdated:DateTime, vsn:String, scanId:Option[Long]):Correlation

  /**
   * Stores the details of a downstream version.
   */
  def storeDownstreamVersion(id:VersionID, attributes:Map[String,TypedAttribute], lastUpdated:DateTime, uvsn:String, dvsn:String, scanId:Option[Long]):Correlation

  /**
   * Clears the upstream version for the given pairKey/id.
   */
  def clearUpstreamVersion(id:VersionID, scanId:Option[Long]):Correlation

  /**
   * Clears the downstream version for the given pairKey/id.
   */
  def clearDownstreamVersion(id:VersionID, scanId:Option[Long]):Correlation
}

/**
 * Provides the ability to batch writes to the underlying store.
 * Extends the high level <code>LimitedVersionCorrelationWriter</code> interface by adding low level commands
 * to commit queued up write commands into the store.
 */
trait ExtendedVersionCorrelationWriter extends LimitedVersionCorrelationWriter with Closeable {
  /**
   * Indicates whether there are any pending changes to be flushed to the store.
   */
  def isDirty: Boolean

  /**
   * Flushes any pending changes to the store, making them permanent.
   */
  def flush(): Unit

  /**
   * Removes all correlations from the store.
   */
  def reset()

  /**
   * Rolls back any pending changes to the store and deletes any temporary data.
   */
  def rollback() : Unit

  /**
   * Clears all tombstones from the store. This method will internally perform a <code>flush</code>, so any
   * pending updates will also be written as part of this operation.
   */
  def clearTombstones()
}

/**
 * Creates a specific implementation of a VersionCorrelationStore for a given pair key.
 */
trait VersionCorrelationStoreFactory extends Closeable {

  def apply(pair: PairRef): VersionCorrelationStore

  /**
   * Closes the correlation store associated with the given, and removes all persistent resources. This method should
   * only be called when a pair is no longer valid within the system, and the data associated with it is no longer
   * required.
   */
  def remove(pair: PairRef): Unit

  /**
   * Closes the correlation store associated with the given pair, releasing in-memory resources associated with it.
   * Does not remove any persistent resources.
   */
  def close(pair: PairRef)
}

abstract class TypedAttribute { def value:String }
case class StringAttribute(value:String) extends TypedAttribute
case class DateTimeAttribute(date:DateTime) extends TypedAttribute {
  def value = date.withZone(DateTimeZone.UTC).toString()
}
case class DateAttribute(date:LocalDate) extends TypedAttribute {
  def value = date.toString()
}
case class IntegerAttribute(int:Int) extends TypedAttribute {
  def value = int.toString
}

class IncompatibleCategoryChangeException(name:String, description:String)
  extends RuntimeException("Change to category %s is not allowed: %s".format(name, description))