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

package net.lshift.diffa.kernel.frontend

import net.lshift.diffa.kernel.matching.MatchingManager
import org.slf4j.{Logger, LoggerFactory}
import net.lshift.diffa.kernel.events._
import net.lshift.diffa.kernel.config.DomainConfigStore
import net.lshift.diffa.kernel.differencing.AttributesUtil
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.participant.changes.ChangeEvent
import net.lshift.diffa.kernel.actors.PairPolicyClient
import net.lshift.diffa.participant.scanning.{ScanAggregation, ScanRequest, ScanResultEntry, ScanConstraint}
import net.lshift.diffa.kernel.util.{MissingObjectException, DownstreamEndpoint, UpstreamEndpoint, CategoryUtil}

/**
 * Front-end for reporting changes.
 */
class Changes(val domainConfig:DomainConfigStore,
              val changeEventClient:PairPolicyClient,
              val mm:MatchingManager,
              val diagnostics:DiagnosticsManager) {
  private val log:Logger = LoggerFactory.getLogger(getClass)

  /**
   * Indicates that a change has occurred within a participant. Locates the appropriate policy for the pair the
   * event is targeted for, and provides the event to the policy.
   */
  def onChange(domain:String, endpoint:String, evt:ChangeEvent) {
    log.debug("Received change event for %s %s: %s".format(domain, endpoint, evt))

    evt.ensureContainsMandatoryFields();

    val targetEndpoint = domainConfig.getEndpoint(domain, endpoint)
    val evtAttributes:Map[String, String] = if (evt.getAttributes != null) evt.getAttributes.toMap else Map()
    val typedAttributes = targetEndpoint.schematize(evtAttributes)

    domainConfig.listPairsForEndpoint(domain, endpoint).foreach(pair => {
      val pairEvt = if (pair.upstream == endpoint) {
        UpstreamPairChangeEvent(VersionID(pair.asRef, evt.getId), typedAttributes, evt.getLastUpdated, evt.getVersion)
      } else {
        if (pair.versionPolicyName == "same" || evt.getParentVersion == null) {
          DownstreamPairChangeEvent(VersionID(pair.asRef, evt.getId), typedAttributes, evt.getLastUpdated, evt.getVersion)
        } else {
          DownstreamCorrelatedPairChangeEvent(VersionID(pair.asRef, evt.getId), typedAttributes, evt.getLastUpdated, evt.getParentVersion, evt.getVersion)
        }
      }

      // Validate that the entities provided meet the constraints of the endpoint
      val endpointCategories = targetEndpoint.categories.toMap
      val issues = AttributesUtil.detectAttributeIssues(
        endpointCategories, targetEndpoint.initialConstraints(None), evtAttributes, typedAttributes)

      if (issues.size > 0) {
        log.warn("Dropping invalid pair event " + pairEvt + " due to issues " + issues)
        diagnostics.logPairExplanation(pair.asRef, "Version Policy",
          "The result %s was dropped since it didn't meet the request constraints. Identified issues were (%s)".format(
            pairEvt, issues.map { case (k, v) => k + ": " + v }.mkString(", ")))
      } else {
        // TODO: Write a test to enforce that the matching manager processes first. This is necessary to ensure
        //    that the DifferencesManager doesn't emit spurious events.

        // If there is a matcher available, notify it first
        mm.getMatcher(pair.asRef) match {
          case None =>
          case Some(matcher) => matcher.onChange(pairEvt, () => {})
        }

        // Propagate the change event to the corresponding policy
        changeEventClient.propagateChangeEvent(pairEvt)
      }
    })
  }

  def startInventory(domain: String, endpoint: String, view:Option[String]):Seq[ScanRequest] = {
    val targetEndpoint = domainConfig.getEndpointDef(domain, endpoint)
    val requests = scala.collection.mutable.Set[ScanRequest]()

    view.foreach(v => {
      if (targetEndpoint.views.find(vd => vd.name == v).isEmpty) {
        // The user specified an unknown view
        throw new MissingObjectException("view " + v)
      }
    })

    domainConfig.listPairsForEndpoint(domain, endpoint).foreach(pair => {
      val side = pair.whichSide(targetEndpoint)

      // Propagate the change event to the corresponding policy
      requests ++= changeEventClient.startInventory(pair.asRef, side, view)
    })

    requests.toSeq
  }

  def submitInventory(domain:String, endpoint:String, view:Option[String], constraints:Seq[ScanConstraint], aggregations:Seq[ScanAggregation], entries:Seq[ScanResultEntry]):Seq[ScanRequest] = {
    val targetEndpoint = domainConfig.getEndpoint(domain, endpoint)
    val endpointCategories = CategoryUtil.fuseViewCategories(targetEndpoint.categories.toMap, targetEndpoint.views, view)
    val fullConstraints = CategoryUtil.mergeAndValidateConstraints(endpointCategories, constraints)

    // Validate the provided entries
    entries.zipWithIndex.foreach { case (entry,idx) =>
      val issues = AttributesUtil.detectAttributeIssues(endpointCategories, fullConstraints, entry.getAttributes.toMap)

      if (issues.size > 0) {
        throw new InvalidInventoryException(
          "Entry %s was invalid. Identified issues were: %s".format(
            idx+1,
            issues.map { case (k, v) => k + ": " + v }.mkString(", ")
          ))
      }
    }

    val nextRequests = scala.collection.mutable.Set[ScanRequest]()
    domainConfig.listPairsForEndpoint(domain, endpoint).foreach(pair => {
      val side = if (pair.upstream == endpoint) UpstreamEndpoint else DownstreamEndpoint

      // Propagate the change event to the corresponding policy
      nextRequests ++= changeEventClient.submitInventory(pair.asRef, side, fullConstraints, aggregations, entries)
    })

    nextRequests.toSeq
  }
}

/**
 * Exception for indicating than an inventory was invalid.
 */
class InvalidInventoryException(reason:String) extends RuntimeException(reason)
