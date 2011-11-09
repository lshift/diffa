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
import net.lshift.diffa.kernel.actors.PairPolicyClient
import net.lshift.diffa.kernel.config.DomainConfigStore
import net.lshift.diffa.kernel.differencing.AttributesUtil
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.participant.changes.ChangeEvent

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

    val targetEndpoint = domainConfig.getEndpoint(domain, endpoint)
    val evtAttributes = targetEndpoint.schematize(evt.getAttributes.toMap)

    domainConfig.listPairsForEndpoint(domain, endpoint).foreach(pair => {
      val pairEvt = if (pair.upstream == endpoint) {
        UpstreamPairChangeEvent(VersionID(pair.asRef, evt.getId), evtAttributes, evt.getLastUpdated, evt.getVersion)
      } else {
        if (pair.versionPolicyName == "same" || evt.getParentVersion == null) {
          DownstreamPairChangeEvent(VersionID(pair.asRef, evt.getId), evtAttributes, evt.getLastUpdated, evt.getVersion)
        } else {
          DownstreamCorrelatedPairChangeEvent(VersionID(pair.asRef, evt.getId), evtAttributes, evt.getLastUpdated, evt.getParentVersion, evt.getVersion)
        }
      }

      // Validate that the entities provided meet the constraints of the endpoint
      val endpointCategories = targetEndpoint.categories.toMap
      val issues = AttributesUtil.detectMissingAttributes(endpointCategories, evt.getAttributes.toMap) ++
        AttributesUtil.detectOutsideConstraints(targetEndpoint.initialConstraints(None), evtAttributes)

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
}