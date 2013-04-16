/**
 * Copyright (C) 2011 LShift Ltd.
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
package net.lshift.diffa.kernel.util

import net.lshift.diffa.kernel.differencing.DomainDifferenceStore
import java.util.{TimerTask, Timer}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

/**
 * Component responsible for periodically cleaning up internal system resources.
 */
class Sweeper(val period:Int, diffStore:DomainDifferenceStore) {
  val log = LoggerFactory.getLogger(getClass)
  val timer = new Timer()
  val matchAgeMins = 5    // How long ago matches should be purged from

  val sweepTask = new TimerTask {
    def run() {
      try {
        diffStore.expireMatches(new DateTime().minusMinutes(matchAgeMins))
      } catch {
        case e: Throwable =>
          // Log errors for alerting but continue so if, for example, we lose the database momentarily,
          // the sweeper timer can continue to fire until the database issue is resolved.
          log.error("An exception was thrown while expiring matches: " + e.getMessage)
      }
    }
  }

  def start() {
    timer.schedule(sweepTask, period * 1000, period * 1000)
  }

  def shutdown() {
    timer.cancel()
  }
}