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

package net.lshift.diffa.kernel.notifications

import net.lshift.diffa.kernel.events.VersionID
import org.slf4j.{Logger, LoggerFactory}
import org.joda.time.{Period, DateTime}
import collection.mutable.ListBuffer
import net.lshift.diffa.kernel.config.DomainConfigStore
import net.lshift.diffa.kernel.lifecycle.{NotificationCentre, AgentLifecycleAware}
import net.lshift.diffa.kernel.differencing._

/**
 * This fires mismatch events out to each registered NotificationProvider.
 */
class EventNotifier(val differencesManager:DifferencesManager,
                    val domainConfigStore:DomainConfigStore,
                    val quietTime:Period)
    extends DifferencingListener
    with AgentLifecycleAware {

  val log:Logger = LoggerFactory.getLogger(getClass)


  log.debug("Quiet time set to " + quietTime.toString)

  private val providers = new ListBuffer[NotificationProvider]()
  private var nextRun = new DateTime()

  override def onAgentInstantiationCompleted(nc: NotificationCentre) {
    nc.registerForDifferenceEvents(this, MatcherFiltered)
  }

  def registerProvider(p:NotificationProvider) = providers += p

  /**
   * Ignore matching events for now because they are not so interesting
   */
  def onMatch(id:VersionID, vsn:String, origin:MatchOrigin) = {}

  def onMismatch(id:VersionID, lastUpdated:DateTime, upstreamVsn:String, downstreamVsn:String, origin:MatchOrigin, level:DifferenceFilterLevel) {
    val now = new DateTime()
    if (now.isAfter(nextRun)) {
      log.trace("About to notify users, the received event was " + id + " at " + lastUpdated)
      nextRun = now.plus(quietTime)
      val e = new NotificationEvent(id, lastUpdated, upstreamVsn, downstreamVsn)      
      domainConfigStore.listDomainMembers(id.pair.domain).foreach(m => providers.foreach( p => p.notify(e,m.user)))
    }
    
  }

}