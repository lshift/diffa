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

package net.lshift.diffa.kernel.lifecycle

import net.lshift.diffa.kernel.events.VersionID
import org.joda.time.DateTime
import net.lshift.diffa.kernel.config.{DiffaPairRef}
import net.lshift.diffa.kernel.differencing._
import collection.mutable.{ListBuffer}
import collection.immutable.HashSet
import net.lshift.diffa.kernel.frontend.SystemConfigListener

/**
 * Central system component for subscribing to notifications. To prevent dependency loops, consumer components should not
 * directly depend on the NotificationCentre, but instead implement AgentLifecycleAware and listen for the
 * <code>onAgentInstantiationCompleted</code> event, which will contain a reference to the center.
 */
class NotificationCentre
    extends DifferencingListener
    with PairScanListener
    with SystemConfigListener {
  private val unfilteredDifferenceListeners = new ListBuffer[DifferencingListener]
  private val filteredDifferenceListeners = new ListBuffer[DifferencingListener]
  private var allDifferenceListeners = Set[DifferencingListener]()
  private val pairScanListeners = new ListBuffer[PairScanListener]
  private val systemConfigListeners = new ListBuffer[SystemConfigListener]

  /**
   * Registers a listener to receive different events.
   */
  def registerForDifferenceEvents(l:DifferencingListener, level:DifferenceFilterLevel) {
    selectListenerList(level) += l
    allDifferenceListeners = unfilteredDifferenceListeners.toSet ++ filteredDifferenceListeners.toSet
  }

   /**
   * Registers a listener to receive pair scan events.
   */
  def registerForPairScanEvents(l:PairScanListener) {
    pairScanListeners += l
  }

   /**
   * Registers a listener to receive system config events.
   */
  def registerForSystemConfigEvents(l:SystemConfigListener) {
    systemConfigListeners += l
  }

  //
  // Differencing Listener Multicast
  //

  def onMismatch(id: VersionID, lastUpdated: DateTime, upstreamVsn: String, downstreamVsn: String, origin: MatchOrigin, level:DifferenceFilterLevel) {
    selectListenerList(level).foreach(_.onMismatch(id, lastUpdated, upstreamVsn, downstreamVsn, origin, level))
  }
  def onMatch(id: VersionID, vsn: String, origin: MatchOrigin) {
    allDifferenceListeners.foreach(_.onMatch(id, vsn, origin))
  }

  private def selectListenerList(level:DifferenceFilterLevel) = level match {
    case Unfiltered      => unfilteredDifferenceListeners
    case MatcherFiltered => filteredDifferenceListeners
  }

  //
  // Pair Scan Listener Multicast
  //

  def pairScanStateChanged(pair: DiffaPairRef, scanState: PairScanState) {
    pairScanListeners.foreach(_.pairScanStateChanged(pair, scanState))
  }

  //
  // System Config Listener Multicast
  //

  def configPropertiesUpdated(properties: Seq[String]) {
    systemConfigListeners.foreach(_.configPropertiesUpdated(properties))
  }
}