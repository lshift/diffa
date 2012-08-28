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

package net.lshift.diffa.kernel.matching

import collection.mutable.{ListBuffer, HashMap}
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.config.{PairRef, DomainConfigStore}
import net.lshift.diffa.kernel.frontend.{DomainPairDef, PairDef}
import org.slf4j.LoggerFactory

/**
 * Keeps track of and updates Local event matchers for pair entries from DomainConfigStore.
 */
class LocalEventMatchingManager(systemConfigStore: SystemConfigStore,
                                domainConfigStore: DomainConfigStore) extends MatchingManager {

  val log = LoggerFactory.getLogger(getClass)

  private val reaper = new LocalEventMatcherReaper
  private val matchers = new HashMap[PairRef, LocalEventMatcher]
  private val listeners = new ListBuffer[MatchingStatusListener]

  // Create a matcher for each pre-existing pair
  systemConfigStore.listPairs.foreach(updateMatcher(_))

  def getMatcher(pair:PairRef) = matchers.get(pair)

  def onUpdatePair(pairRef:PairRef):Unit = {

    val pair = domainConfigStore.getPairDef(pairRef)

    if (pair.matchingTimeout < 0)  {
      removeMatcher(pairRef)
    } else {
      updateMatcher(pair)
    }
  }

  def onDeletePair(pair:PairRef) = {
    removeMatcher(pair)
  }

  def close: Unit = {
    matchers.values foreach (m => m.dispose)
    reaper.dispose
  }

  def addListener(l:MatchingStatusListener) {
    listeners.synchronized {
      listeners += l
      matchers.values.foreach(m => m.addListener(l))
    }
  }

  private def updateMatcher(pair:DomainPairDef):Unit = {

    // TODO demote this statement
    log.info("Updating matcher for " + pair)

    val newMatcher = new LocalEventMatcher(pair, reaper)

    matchers.remove(pair.asRef) match {
      case Some(matcher) => {
        // Recreate matcher with new window length but original listeners
        val listeners = matcher.listeners
        matcher.dispose
        listeners foreach (l => newMatcher.addListener(l))
      }
      case None => {
        // Apply all of our default listeners to the new matcher
        listeners.synchronized {
          listeners.foreach(l => newMatcher.addListener(l))
        }
      }
    }
    matchers(pair.asRef) = newMatcher
  }

  private def removeMatcher(pair:PairRef):Unit = {
    matchers.get(pair) match {
      case Some(matcher) => {
        matcher.dispose
        matchers -= pair
      }
      case None => // nothing to do
    }
  }
}