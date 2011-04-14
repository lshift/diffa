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

import net.lshift.diffa.kernel.events._
import net.lshift.diffa.kernel.participants._
import org.joda.time.DateTime
import net.lshift.diffa.kernel.alerting.Alerter
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.config.{Endpoint, Pair, ConfigStore}
import org.slf4j.LoggerFactory

/**
 * Standard behaviours supported by synchronising version policies.
 */
abstract class BaseSynchingVersionPolicy(val stores:VersionCorrelationStoreFactory,
                                         listener:DifferencingListener,
                                         configStore:ConfigStore)
    extends VersionPolicy {
  protected val alerter = Alerter.forClass(getClass)

  /**
   * Handles a participant change. Due to the need to later correlate data, event information is cached to the
   * version correlation store.
   */
  def onChange(writer: VersionCorrelationWriter, evt: PairChangeEvent) = {

    val pair = configStore.getPair(evt.id.pairKey)

    val corr = evt match {
      case UpstreamPairChangeEvent(id, _, lastUpdate, vsn) => vsn match {
        case null => writer.clearUpstreamVersion(id)
        case _    => writer.storeUpstreamVersion(id, pair.upstream.schematize(evt.attributes), maybe(lastUpdate), vsn)
      }
      case DownstreamPairChangeEvent(id, _, lastUpdate, vsn) => vsn match {
        case null => writer.clearDownstreamVersion(id)
        case _    => writer.storeDownstreamVersion(id, pair.downstream.schematize(evt.attributes), maybe(lastUpdate), vsn, vsn)
      }
      case DownstreamCorrelatedPairChangeEvent(id, _, lastUpdate, uvsn, dvsn) => (uvsn, dvsn) match {
        case (null, null) => writer.clearDownstreamVersion(id)
        case _            => writer.storeDownstreamVersion(id, pair.downstream.schematize(evt.attributes), maybe(lastUpdate), uvsn, dvsn)
      }
    }

    if (corr.isMatched.booleanValue) {
      listener.onMatch(evt.id, corr.upstreamVsn)
    } else {
      listener.onMismatch(evt.id, corr.lastUpdate, corr.upstreamVsn, corr.downstreamUVsn)
    }
  }

  def maybe(lastUpdate:DateTime) = {
    lastUpdate match {
      case null => new DateTime
      case d    => d
    }
  }

  def difference(pairKey: String, writer: VersionCorrelationWriter, us: UpstreamParticipant, ds: DownstreamParticipant, l:DifferencingListener) = {
    val pair = configStore.getPair(pairKey)

    synchronizeParticipants(pair, writer, us, ds, l)
    writer.flush()

    // Run a query for mismatched versions, and report each one
    stores(pairKey).unmatchedVersions(pair.upstream.defaultConstraints, pair.downstream.defaultConstraints).foreach(
      corr => l.onMismatch(VersionID(corr.pairing, corr.id), corr.lastUpdate, corr.upstreamVsn, corr.downstreamUVsn))

    true
  }

  /**
   * Performs a synchronization between participants.
   */
  protected def synchronizeParticipants(pair: Pair, writer: VersionCorrelationWriter, us: UpstreamParticipant, ds: DownstreamParticipant, l:DifferencingListener) = {
    val upstreamConstraints = pair.upstream.groupedConstraints
    val downstreamConstraints = pair.downstream.groupedConstraints

    upstreamConstraints.foreach((new UpstreamSyncStrategy).syncHalf(pair, writer, pair.upstream, pair.upstream.defaultBucketing, _, us))
    downstreamConstraints.foreach(downstreamStrategy(us,ds,l).syncHalf(pair, writer, pair.downstream, pair.downstream.defaultBucketing, _, ds))
  }

  /**
   * Allows an implementing policy to define what kind of downstream syncing policy it requires
   */
  def downstreamStrategy(us:UpstreamParticipant, ds:DownstreamParticipant, l:DifferencingListener) : SyncStrategy

  /**
   * The basic functionality for a synchronisation strategy.
   */
  protected abstract class SyncStrategy {

    val log = LoggerFactory.getLogger(getClass)

    def syncHalf(pair:Pair, writer: VersionCorrelationWriter, endpoint:Endpoint, bucketing:Map[String, CategoryFunction], constraints:Seq[QueryConstraint], p:Participant) {
      val remoteDigests = p.queryAggregateDigests(bucketing, constraints)
      val localDigests = getAggregates(pair.key, bucketing, constraints)

      if (log.isTraceEnabled()) {
        log.trace("Remote digests: %s".format(remoteDigests))
        log.trace("Local digests: %s".format(localDigests))
      }

      DigestDifferencingUtils.differenceAggregates(remoteDigests, localDigests, bucketing, constraints).foreach(o => o match {
        case AggregateQueryAction(narrowBuckets, narrowConstraints) =>
          syncHalf(pair, writer, endpoint, narrowBuckets, narrowConstraints, p)
        case EntityQueryAction(narrowed)    => {
          val remoteVersions = p.queryEntityVersions(narrowed)
          val cachedVersions = getEntities(pair.key, narrowed)
          DigestDifferencingUtils.differenceEntities(endpoint.categories.toMap, remoteVersions, cachedVersions, narrowed)
            .foreach(handleMismatch(pair.key, writer, _))
        }
      })
    }

    /**
     * Should be invoked by the child sync strategies each time they modify a correlation (eg, store an upstream or
     * downstream version). This allows for any necessary eventing to be performed.
     */
    protected def handleUpdatedCorrelation(corr:Correlation) {
      // Unmatched versions will be evented at the end of the sync. Matched versions should be evented immediately, as
      // we won't know what went from unmatched -> matched later.
      if (corr.isMatched.booleanValue) {
        listener.onMatch(VersionID(corr.pairing, corr.id), corr.upstreamVsn)
      }
    }

    def getAggregates(pairKey:String, bucketing:Map[String, CategoryFunction], constraints:Seq[QueryConstraint]) : Seq[AggregateDigest]
    def getEntities(pairKey:String, constraints:Seq[QueryConstraint]) : Seq[EntityVersion]
    def handleMismatch(pairKey:String, writer: VersionCorrelationWriter, vm:VersionMismatch)
  }

  protected class UpstreamSyncStrategy extends SyncStrategy {

    def getAggregates(pairKey:String, bucketing:Map[String, CategoryFunction], constraints:Seq[QueryConstraint]) = {
      val aggregator = new Aggregator(bucketing)
      stores(pairKey).queryUpstreams(constraints, aggregator.collectUpstream)
      aggregator.digests
    }

    def getEntities(pairKey:String, constraints:Seq[QueryConstraint]) = {
      stores(pairKey).queryUpstreams(constraints).map(x => {
        EntityVersion(x.id, AttributesUtil.toSeq(x.upstreamAttributes.toMap), x.lastUpdate, x.upstreamVsn)
      })
    }

    def handleMismatch(pairKey: String, writer: VersionCorrelationWriter, vm:VersionMismatch) = {
      vm match {
        case VersionMismatch(id, attributes, lastUpdate,  usVsn, _) =>
          if (usVsn != null) {
            handleUpdatedCorrelation(writer.storeUpstreamVersion(VersionID(pairKey, id), attributes, lastUpdate, usVsn))
          } else {
            handleUpdatedCorrelation(writer.clearUpstreamVersion(VersionID(pairKey, id)))
          }
      }
    }
  }

  protected class Aggregator(bucketing:Map[String, CategoryFunction]) {
    val builder = new DigestBuilder(bucketing)

    def collectUpstream(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, vsn:String) =
      builder.add(id, attributes, lastUpdate, vsn)
    def collectDownstream(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, uvsn:String, dvsn:String) =
      builder.add(id, attributes, lastUpdate, dvsn)

    def digests:Seq[AggregateDigest] = builder.digests
  }
}