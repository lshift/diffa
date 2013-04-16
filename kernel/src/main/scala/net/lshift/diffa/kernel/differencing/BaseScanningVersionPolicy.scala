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
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.participant.common.JSONHelper
import net.lshift.diffa.kernel.config.{DomainConfigStore, DiffaPairRef, Endpoint}
import org.joda.time.{DateTimeZone, DateTime, Interval}
import org.joda.time.format.DateTimeFormat
import java.io.{OutputStream, PrintWriter}
import net.lshift.diffa.kernel.diag.{DiagnosticsManager, DiagnosticLevel}
import net.lshift.diffa.participant.scanning._
import collection.JavaConversions._
import net.lshift.diffa.kernel.util.{CategoryUtil, DownstreamEndpoint, EndpointSide, UpstreamEndpoint}

/**
 * Standard behaviours supported by scanning version policies.
 */
abstract class BaseScanningVersionPolicy(val stores:VersionCorrelationStoreFactory,
                                         listener:DifferencingListener,
                                         diagnostics:DiagnosticsManager)
    extends VersionPolicy {
  protected val alerter = Alerter.forClass(getClass)

  val logger = LoggerFactory.getLogger(getClass)

  val fileNameFormatter = DateTimeFormat.forPattern(DiagnosticsManager.fileSystemFriendlyDateFormat)

  /**
   * Handles a participant change. Due to the need to later correlate data, event information is cached to the
   * version correlation store.
   */
  def onChange(writer: LimitedVersionCorrelationWriter, evt: PairChangeEvent) = {


    val corr = evt match {
      case UpstreamPairChangeEvent(id, _, lastUpdate, vsn) => vsn match {
        case null => writer.clearUpstreamVersion(id, None)
        case _    => writer.storeUpstreamVersion(id, evt.attributes, maybe(lastUpdate), vsn, None)
      }
      case DownstreamPairChangeEvent(id, _, lastUpdate, vsn) => vsn match {
        case null => writer.clearDownstreamVersion(id, None)
        case _    => writer.storeDownstreamVersion(id, evt.attributes, maybe(lastUpdate), vsn, vsn, None)
      }
      case DownstreamCorrelatedPairChangeEvent(id, _, lastUpdate, uvsn, dvsn) => (uvsn, dvsn) match {
        case (null, null) => writer.clearDownstreamVersion(id, None)
        case _            => writer.storeDownstreamVersion(id, evt.attributes, maybe(lastUpdate), uvsn, dvsn, None)
      }
    }

    if (corr.isMatched.booleanValue) {
      listener.onMatch(evt.id, corr.upstreamVsn, LiveWindow)
    } else {
      listener.onMismatch(evt.id, corr.lastUpdate, corr.upstreamVsn, corr.downstreamUVsn, LiveWindow, Unfiltered)
    }
  }

  def startInventory(pairRef:DiffaPairRef, endpoint:Endpoint, view:Option[String], writer: LimitedVersionCorrelationWriter, side:EndpointSide) = {
    val strategy = side match {
      case UpstreamEndpoint   => new UpstreamScanStrategy(endpoint.lookupCollation)
      case DownstreamEndpoint => downstreamStrategy(null, null, endpoint.lookupCollation)
    }

    strategy.startInventory(pairRef, endpoint, view, writer)
  }

  /**
   * Handles an inventory arriving from a participant.
   */
  def processInventory(pairRef:DiffaPairRef, endpoint:Endpoint, writer: LimitedVersionCorrelationWriter, side:EndpointSide,
                       constraints:Seq[ScanConstraint], aggregations:Seq[ScanAggregation], entries:Seq[ScanResultEntry]) = {
    val strategy = side match {
      case UpstreamEndpoint   => new UpstreamScanStrategy(endpoint.lookupCollation)
      case DownstreamEndpoint => downstreamStrategy(null, null, endpoint.lookupCollation)
    }

    strategy.processInventory(pairRef, endpoint, writer, constraints, aggregations, entries, listener)
  }

  def maybe(lastUpdate:DateTime) = {
    lastUpdate match {
      case null => new DateTime().withZone(DateTimeZone.UTC)
      case d    => d.withZone(DateTimeZone.UTC)
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

  def scanUpstream(scanId:Long, pairRef:DiffaPairRef, upstream:Endpoint, view:Option[String], writer: LimitedVersionCorrelationWriter, participant:UpstreamParticipant,
                   listener:DifferencingListener, handle:FeedbackHandle) = {
    benchmark(pairRef, "upstream scan", () => {
      val upstreamConstraints = upstream.groupedConstraints(view)
      constraintsOrEmpty(upstreamConstraints).foreach((new UpstreamScanStrategy(upstream.lookupCollation)
        .scanParticipant(scanId, pairRef, writer, upstream, upstream.initialBucketing(view), _, participant, listener, handle)))
    })
  }

  def scanDownstream(scanId:Long, pairRef:DiffaPairRef, downstream:Endpoint, view:Option[String], writer: LimitedVersionCorrelationWriter, us:UpstreamParticipant,
                     ds:DownstreamParticipant, listener:DifferencingListener, handle:FeedbackHandle) = {
    benchmark(pairRef, "downstream scan", () => {
      val downstreamConstraints = downstream.groupedConstraints(view)
      constraintsOrEmpty(downstreamConstraints).foreach(downstreamStrategy(us,ds, downstream.lookupCollation)
        .scanParticipant(scanId, pairRef, writer, downstream, downstream.initialBucketing(view), _, ds, listener, handle))
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
  def downstreamStrategy(us:UpstreamParticipant, ds:DownstreamParticipant, collation: Collation) : ScanStrategy

  /**
   * The basic functionality for a scanning strategy.
   */
  protected abstract class ScanStrategy {
    val log = LoggerFactory.getLogger(getClass)

    def name:String

    def scanParticipant(scanId:Long,
                        pair:DiffaPairRef,
                        writer:LimitedVersionCorrelationWriter,
                        endpoint:Endpoint,
                        bucketing:Seq[CategoryFunction],
                        constraints:Seq[ScanConstraint],
                        participant:Participant,
                        listener:DifferencingListener,
                        handle:FeedbackHandle) {

      if (bucketing.size == 0) {
        scanEntities(scanId, pair, writer, endpoint, constraints, participant, listener, handle)
      } else {
        scanAggregates(scanId, pair, writer, endpoint, bucketing, constraints, participant, listener, handle)
      }
    }

    def scanAggregates(scanId:Long,
                       pair:DiffaPairRef,
                       writer:LimitedVersionCorrelationWriter,
                       endpoint:Endpoint,
                       bucketing:Seq[CategoryFunction],
                       constraints:Seq[ScanConstraint],
                       participant:Participant,
                       listener:DifferencingListener,
                       handle:FeedbackHandle) {
      
      checkForCancellation(handle, pair)
      diagnostics.logPairEvent(Some(scanId), pair, DiagnosticLevel.TRACE, "Scanning aggregates for %s with (constraints=%s, bucketing=%s)".format(endpoint.name, constraints, bucketing))

      val requestTimestamp = new DateTime
      val remoteDigests = participant.scan(constraints, bucketing)
      val responseTimestamp = new DateTime

      val localDigests = getAggregates(pair, bucketing, constraints)

      // Generate a diagnostic object detailing the response provided by the participant
      diagnostics.writePairExplanationObject(Some(scanId), pair, "Version Policy", name + "-Aggregates-" + fileNameFormatter.print(requestTimestamp)  + ".json", os => {
        val pw = new PrintWriter(os)
        writeCommonHeader(pw, pair, endpoint, requestTimestamp, responseTimestamp)
        pw.println("Bucketing: %s".format(bucketing))
        pw.println("Constraints: %s".format(constraints))
        pw.println("------------------------")
        pw.flush()

        JSONHelper.formatQueryResult(os, remoteDigests)
      })

      DigestDifferencingUtils.differenceAggregates(remoteDigests, localDigests, bucketing, constraints).foreach(o => o match {
        case AggregateQueryAction(narrowBuckets, narrowConstraints) =>
          scanAggregates(scanId, pair, writer, endpoint, narrowBuckets, narrowConstraints, participant, listener, handle)
        case EntityQueryAction(narrowed)    =>
          scanEntities(scanId, pair, writer, endpoint, narrowed, participant, listener, handle)
      })
    }

    def scanEntities(scanId:Long,
                     pair:DiffaPairRef,
                     writer:LimitedVersionCorrelationWriter,
                     endpoint:Endpoint,
                     constraints:Seq[ScanConstraint],
                     participant:Participant,
                     listener:DifferencingListener,
                     handle:FeedbackHandle) {
      checkForCancellation(handle, pair)
      diagnostics.logPairEvent(Some(scanId),  pair, DiagnosticLevel.TRACE, "Scanning entities for %s with (constraints=%s)".format(endpoint.name, constraints))

      val requestTimestamp = new DateTime
      val remoteVersions = participant.scan(constraints, Seq())
      val responseTimestamp = new DateTime

      val cachedVersions = getEntities(pair, constraints)

      // Generate a diagnostic object detailing the response provided by the participant
      diagnostics.writePairExplanationObject(Some(scanId), pair, "Version Policy", name + "-Entities-" + fileNameFormatter.print(requestTimestamp) + ".json", os => {
        val pw = new PrintWriter(os)
        writeCommonHeader(pw, pair, endpoint, requestTimestamp, responseTimestamp)
        pw.println("Constraints: %s".format(constraints))
        pw.println("------------------------")
        pw.flush()

        JSONHelper.formatQueryResult(os, remoteVersions)
      })

      // Validate that the entities provided meet the constraints of the endpoint
      val endpointCategories = endpoint.categories.toMap
      val validRemoteVersions = remoteVersions.filter(entry => {
        val issues = AttributesUtil.detectAttributeIssues(endpointCategories, constraints, entry.getAttributes.toMap)

        if (issues.size == 0) {
          true
        } else {
          log.warn("Dropping invalid scan result entry " + entry + " due to issues " + issues)
          diagnostics.logPairEvent(Some(scanId), pair, DiagnosticLevel.ERROR,
            "The result %s was dropped since it didn't meet the request constraints.  Identified issues were (%s)".format(
              entry, issues.map { case (k, v) => k + ": " + v }.mkString(", ")))
          diagnostics.logPairExplanation(Some(scanId), pair, "Version Policy",
          "The result %s was dropped since it didn't meet the request constraints. Identified issues were (%s)".format(
            entry, issues.map { case (k, v) => k + ": " + v }.mkString(", ")))

          false
        }
      })

      DigestDifferencingUtils.differenceEntities(endpointCategories, validRemoteVersions, cachedVersions, constraints)
        .foreach(handleMismatch(Some(scanId), pair, writer, _, listener))
    }

    private def writeCommonHeader(pw:PrintWriter, pair:DiffaPairRef, endpoint:Endpoint, requestTimestamp:DateTime, responseTimestamp:DateTime) = {
      pw.println("Pair: %s".format(pair))
      pw.println("Endpoint at: %s".format(endpoint))
      pw.println("Requested at: %s".format(requestTimestamp))
      pw.println("Response received at: %s".format(responseTimestamp))
      val timeTaken = new Interval(requestTimestamp,responseTimestamp).toPeriod()
      pw.println("Time taken : %s".format(timeTaken))
    }

    def startInventory(pair: DiffaPairRef, endpoint: Endpoint, view:Option[String], writer: LimitedVersionCorrelationWriter): Seq[ScanRequest] = {
      val constraintGroups = endpoint.groupedConstraints(view)
      constraintsOrEmpty(constraintGroups).map(g => {
        new ScanRequest(g.toSet[ScanConstraint], endpoint.initialBucketing(view).toSet[ScanAggregation])
      }).toSeq
    }

    def processInventory(pair:DiffaPairRef, endpoint:Endpoint, writer:LimitedVersionCorrelationWriter,
                         constraints:Seq[ScanConstraint], aggregations:Seq[ScanAggregation],
                         inventoryEntries:Seq[ScanResultEntry], listener: DifferencingListener):Seq[ScanRequest] = {
      val endpointCategories = endpoint.categories.toMap

      if (aggregations.length == 0) {
        val cachedVersions = getEntities(pair, constraints)

        DigestDifferencingUtils.differenceEntities(endpointCategories, inventoryEntries, cachedVersions, constraints)
          .foreach(handleMismatch(None, pair, writer, _, listener))

        Seq()
      } else {
        val localDigests = getAggregates(pair, aggregations, constraints)
        val bucketing = CategoryUtil.categoryFunctionsFor(aggregations, endpointCategories)

        DigestDifferencingUtils.differenceAggregates(inventoryEntries, localDigests, bucketing, constraints).map(o => o match {
          case AggregateQueryAction(narrowBuckets, narrowConstraints) =>
            new ScanRequest(narrowConstraints.toSet[ScanConstraint], narrowBuckets.toSet[ScanAggregation])
          case EntityQueryAction(narrowed)    =>
            new ScanRequest(narrowed.toSet[ScanConstraint], Set[ScanAggregation]())
        })
      }
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

    def getAggregates(pair:DiffaPairRef, bucketing:Seq[ScanAggregation], constraints:Seq[ScanConstraint]) : Seq[ScanResultEntry]
    def getEntities(pair:DiffaPairRef, constraints:Seq[ScanConstraint]) : Seq[ScanResultEntry]
    def handleMismatch(scanId:Option[Long], pair:DiffaPairRef, writer: LimitedVersionCorrelationWriter, vm:VersionMismatch, listener:DifferencingListener)
  }

  protected class UpstreamScanStrategy (collation: Collation) extends ScanStrategy {
    val name = "Upstream"

    def getAggregates(pair:DiffaPairRef, bucketing:Seq[ScanAggregation], constraints:Seq[ScanConstraint]) = {
      val aggregator = new Aggregator(bucketing, collation)
      stores(pair).queryUpstreams(constraints, aggregator.collectUpstream)
      aggregator.digests
    }

    def getEntities(pair:DiffaPairRef, constraints:Seq[ScanConstraint]) = {
      stores(pair).queryUpstreams(constraints).map(x => {
        ScanResultEntry.forEntity(x.id, x.upstreamVsn, x.lastUpdate, mapAsJavaMap(x.upstreamAttributes))
      })
    }

    def handleMismatch(scanId:Option[Long], pair:DiffaPairRef, writer: LimitedVersionCorrelationWriter, vm:VersionMismatch, listener:DifferencingListener) = {
      vm match {
        case VersionMismatch(id, attributes, lastUpdate,  usVsn, _) =>

          val corrrelation = writer.synchronized {
            if (usVsn != null) {
              writer.storeUpstreamVersion(VersionID(pair, id), attributes, lastUpdate, usVsn, scanId)
            } else {
              writer.clearUpstreamVersion(VersionID(pair, id), scanId)
            }
          }

          handleUpdatedCorrelation(corrrelation)
      }
    }
  }

  protected class Aggregator(bucketing:Seq[ScanAggregation], collation: Collation) {
    val builder = new DigestBuilder(bucketing, collation)

    def collectUpstream(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, vsn:String) =
      builder.add(id.id, attributes, vsn)
    def collectDownstream(id:VersionID, attributes:Map[String, String], lastUpdate:DateTime, uvsn:String, dvsn:String) =
      builder.add(id.id, attributes, dvsn)

    def digests:Seq[ScanResultEntry] = builder.toDigests
  }
}
