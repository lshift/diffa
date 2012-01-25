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
import net.lshift.diffa.kernel.alerting.Alerter
import org.slf4j.LoggerFactory
import concurrent.SyncVar
import net.lshift.diffa.participant.scanning.{ScanConstraint, DigestBuilder, ScanResultEntry}
import net.lshift.diffa.kernel.diag.{DiagnosticLevel, DiagnosticsManager}
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import org.joda.time.{Interval, DateTime}
import net.lshift.diffa.participant.common.JSONHelper
import java.io.PrintWriter
import collection.JavaConversions._
import net.lshift.diffa.kernel.config.{DomainConfigStore, DiffaPairRef, Endpoint}

/**
 * Standard behaviours supported by scanning version policies.
 */
abstract class BaseScanningVersionPolicy(val stores:VersionCorrelationStoreFactory,
                                         listener:DifferencingListener,
                                         systemConfigStore:SystemConfigStore,
                                         diagnostics:DiagnosticsManager)
    extends VersionPolicy {
  protected val alerter = Alerter.forClass(getClass)

  val logger = LoggerFactory.getLogger(getClass)

  /**
   * Handles a participant change. Due to the need to later correlate data, event information is cached to the
   * version correlation store.
   */
  def onChange(writer: LimitedVersionCorrelationWriter, evt: PairChangeEvent) = {
    val pair = systemConfigStore.getPair(evt.id.pair.domain, evt.id.pair.key)

    val corr = evt match {
      case UpstreamPairChangeEvent(id, _, lastUpdate, vsn) => vsn match {
        case null => writer.clearUpstreamVersion(id)
        case _    => writer.storeUpstreamVersion(id, evt.attributes, maybe(lastUpdate), vsn)
      }
      case DownstreamPairChangeEvent(id, _, lastUpdate, vsn) => vsn match {
        case null => writer.clearDownstreamVersion(id)
        case _    => writer.storeDownstreamVersion(id, evt.attributes, maybe(lastUpdate), vsn, vsn)
      }
      case DownstreamCorrelatedPairChangeEvent(id, _, lastUpdate, uvsn, dvsn) => (uvsn, dvsn) match {
        case (null, null) => writer.clearDownstreamVersion(id)
        case _            => writer.storeDownstreamVersion(id, evt.attributes, maybe(lastUpdate), uvsn, dvsn)
      }
    }

    if (corr.isMatched.booleanValue) {
      listener.onMatch(evt.id, corr.upstreamVsn, LiveWindow)
    } else {
      listener.onMismatch(evt.id, corr.lastUpdate, corr.upstreamVsn, corr.downstreamUVsn, LiveWindow, Unfiltered)
    }
  }

  def maybe(lastUpdate:DateTime) = {
    lastUpdate match {
      case null => new DateTime
      case d    => d
    }
  }

  def benchmark[T](pairRef:DiffaPairRef, label:String, f:() => T) {
    val start = new DateTime()
    val result = f()
    val stop = new DateTime()
    val interval = new Interval(start,stop)
    val period = interval.toPeriod
    logger.debug("[%s]: Benchmarking operation %s: %s -> %s".format(pairRef.identifier, label, period , interval ) )
    result
  }

  def scanUpstream(pairRef:DiffaPairRef, upstream:Endpoint, view:Option[String], writer: LimitedVersionCorrelationWriter, participant:UpstreamParticipant,
                   listener:DifferencingListener, handle:FeedbackHandle) = {
    benchmark(pairRef, "upstream scan", () => {
      val upstreamConstraints = upstream.groupedConstraints(view)
      constraintsOrEmpty(upstreamConstraints).foreach((new UpstreamScanStrategy)
        .scanParticipant(pairRef, writer, upstream, upstream.initialBucketing(view), _, participant, listener, handle))
    })
  }

  def scanDownstream(pairRef:DiffaPairRef, downstream:Endpoint, view:Option[String], writer: LimitedVersionCorrelationWriter, us:UpstreamParticipant,
                     ds:DownstreamParticipant, listener:DifferencingListener, handle:FeedbackHandle) = {
    benchmark(pairRef, "downstream scan", () => {
      val downstreamConstraints = downstream.groupedConstraints(view)
      constraintsOrEmpty(downstreamConstraints).foreach(downstreamStrategy(us,ds)
        .scanParticipant(pairRef, writer, downstream, downstream.initialBucketing(view), _, ds, listener, handle))
    })
  }

  private def constraintsOrEmpty(grouped:Seq[Seq[ScanConstraint]]):Seq[Seq[ScanConstraint]] =
    if (grouped.length > 0)
      grouped
    else
      Seq(Seq())

  /**
   * Allows an implementing policy to define what kind of downstream scanning policy it requires
   */
  def downstreamStrategy(us:UpstreamParticipant, ds:DownstreamParticipant) : ScanStrategy

  /**
   * The basic functionality for a scanning strategy.
   */
  protected abstract class ScanStrategy {
    val log = LoggerFactory.getLogger(getClass)

    def name:String

    def scanParticipant(pair:DiffaPairRef,
                        writer:LimitedVersionCorrelationWriter,
                        endpoint:Endpoint,
                        bucketing:Seq[CategoryFunction],
                        constraints:Seq[ScanConstraint],
                        participant:Participant,
                        listener:DifferencingListener,
                        handle:FeedbackHandle) {

      if (bucketing.size == 0) {
        scanEntities(pair, writer, endpoint, constraints, participant, listener, handle)
      } else {
        scanAggregates(pair, writer, endpoint, bucketing, constraints, participant, listener, handle)
      }
    }

    def scanAggregates(pair:DiffaPairRef,
                       writer:LimitedVersionCorrelationWriter,
                       endpoint:Endpoint,
                       bucketing:Seq[CategoryFunction],
                       constraints:Seq[ScanConstraint],
                       participant:Participant,
                       listener:DifferencingListener,
                       handle:FeedbackHandle) {
      
      checkForCancellation(handle, pair)
      diagnostics.logPairEvent(DiagnosticLevel.TRACE, pair, "Scanning aggregates for %s with (constraints=%s, bucketing=%s)".format(endpoint.name, constraints, bucketing))

      val remoteDigests = participant.scan(constraints, bucketing)
      val localDigests = getAggregates(pair, bucketing, constraints)

      // Generate a diagnostic object detailing the response provided by the participant
      diagnostics.writePairExplanationObject(pair, "Version Policy", name + "-Aggregates-" + System.currentTimeMillis() + ".json", os => {
        val pw = new PrintWriter(os)
        pw.println("Bucketing: %s".format(bucketing))
        pw.println("Constraints: %s".format(constraints))
        pw.println("------------------------")
        pw.flush()

        JSONHelper.formatQueryResult(os, remoteDigests)
      })

      if (log.isTraceEnabled) {
        log.trace("Bucketing: %s".format(bucketing))
        log.trace("Constraints: %s".format(constraints))
        log.trace("Remote digests: %s".format(remoteDigests))
        log.trace("Local digests: %s".format(localDigests))
      }

      DigestDifferencingUtils.differenceAggregates(remoteDigests, localDigests, bucketing, constraints).foreach(o => o match {
        case AggregateQueryAction(narrowBuckets, narrowConstraints) =>
          scanAggregates(pair, writer, endpoint, narrowBuckets, narrowConstraints, participant, listener, handle)
        case EntityQueryAction(narrowed)    =>
          scanEntities(pair, writer, endpoint, narrowed, participant, listener, handle)
      })
    }

    def scanEntities(pair:DiffaPairRef,
                     writer:LimitedVersionCorrelationWriter,
                     endpoint:Endpoint,
                     constraints:Seq[ScanConstraint],
                     participant:Participant,
                     listener:DifferencingListener,
                     handle:FeedbackHandle) {
      checkForCancellation(handle, pair)
      diagnostics.logPairEvent(DiagnosticLevel.TRACE, pair, "Scanning entities for %s with (constraints=%s)".format(endpoint.name, constraints))

      val remoteVersions = participant.scan(constraints, Seq())
      val cachedVersions = getEntities(pair, constraints)

      // Generate a diagnostic object detailing the response provided by the participant
      diagnostics.writePairExplanationObject(pair, "Version Policy", name + "-Entities-" + System.currentTimeMillis() + ".json", os => {
        val pw = new PrintWriter(os)
        pw.println("Constraints: %s".format(constraints))
        pw.println("------------------------")
        pw.flush()

        JSONHelper.formatQueryResult(os, remoteVersions)
      })

      if (log.isTraceEnabled) {
        log.trace("Remote versions: %s".format(remoteVersions))
        log.trace("Local versions: %s".format(cachedVersions))
      }

      // Validate that the entities provided meet the constraints of the endpoint
      val endpointCategories = endpoint.categories.toMap
      val validRemoteVersions = remoteVersions.filter(entry => {
        val attrsMap = entry.getAttributes.toMap
        val typedAttrsMap = AttributesUtil.toTypedMap(endpointCategories, attrsMap)
        val issues = AttributesUtil.detectMissingAttributes(endpointCategories, attrsMap) ++
          AttributesUtil.detectOutsideConstraints(constraints, typedAttrsMap)

        if (issues.size == 0) {
          true
        } else {
          log.warn("Dropping invalid scan result entry " + entry + " due to issues " + issues)
          diagnostics.logPairExplanation(pair, "Version Policy",
            "The result %s was dropped since it didn't meet the request constraints. Identified issues were (%s)".format(
              entry, issues.map { case (k, v) => k + ": " + v }.mkString(", ")))

          false
        }
      })

      DigestDifferencingUtils.differenceEntities(endpointCategories, validRemoteVersions, cachedVersions, constraints)
        .foreach(handleMismatch(pair, writer, _, listener))
    }

    def checkForCancellation(handle:FeedbackHandle, pair:DiffaPairRef) = {
      if (handle.isCancelled) {
        throw new ScanCancelledException(pair)
      }
    }

    /**
     * Should be invoked by the child scan strategies each time they modify a correlation (eg, store an upstream or
     * downstream version). This allows for any necessary eventing to be performed.
     */
    protected def handleUpdatedCorrelation(corr:Correlation) {
      // Unmatched versions will be evented at the end of the scan. Matched versions should be evented immediately, as
      // we won't know what went from unmatched -> matched later.
      if (corr.isMatched.booleanValue) {
        listener.onMatch(corr.asVersionID, corr.upstreamVsn, TriggeredByScan)
      }
    }

    def getAggregates(pair:DiffaPairRef, bucketing:Seq[CategoryFunction], constraints:Seq[ScanConstraint]) : Seq[ScanResultEntry]
    def getEntities(pair:DiffaPairRef, constraints:Seq[ScanConstraint]) : Seq[ScanResultEntry]
    def handleMismatch(pair:DiffaPairRef, writer: LimitedVersionCorrelationWriter, vm:VersionMismatch, listener:DifferencingListener)
  }

  protected class UpstreamScanStrategy extends ScanStrategy {
    val name = "Upstream"

    def getAggregates(pair:DiffaPairRef, bucketing:Seq[CategoryFunction], constraints:Seq[ScanConstraint]) = {
      val aggregator = new Aggregator(bucketing)
      stores(pair).queryUpstreams(constraints, aggregator.collectUpstream)
      aggregator.digests
    }

    def getEntities(pair:DiffaPairRef, constraints:Seq[ScanConstraint]) = {
      stores(pair).queryUpstreams(constraints).map(x => {
        ScanResultEntry.forEntity(x.id, x.upstreamVsn, x.lastUpdate, mapAsJavaMap(x.upstreamAttributes))
      })
    }

    def handleMismatch(pair:DiffaPairRef, writer: LimitedVersionCorrelationWriter, vm:VersionMismatch, listener:DifferencingListener) = {
      vm match {
        case VersionMismatch(id, attributes, lastUpdate,  usVsn, _) =>
          if (usVsn != null) {
            handleUpdatedCorrelation(writer.storeUpstreamVersion(VersionID(pair, id), attributes, lastUpdate, usVsn))
          } else {
            handleUpdatedCorrelation(writer.clearUpstreamVersion(VersionID(pair, id)))
          }
      }
    }
  }

  protected class Aggregator(bucketing:Seq[CategoryFunction]) {
    val builder = new DigestBuilder(bucketing)

    def collectUpstream(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, vsn:String) =
      builder.add(id.id, attributes, vsn)
    def collectDownstream(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, uvsn:String, dvsn:String) =
      builder.add(id.id, attributes, dvsn)

    def digests:Seq[ScanResultEntry] = builder.toDigests
  }
}