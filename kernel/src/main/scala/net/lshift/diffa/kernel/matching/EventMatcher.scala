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

import net.lshift.diffa.kernel.events.{PairChangeEvent, VersionID}

/**
 * Trait supported by classes that can analyse streams to provide realtime warnings of likely
 * differences between participants.
 */
trait EventMatcher {
  /**
   * Callback type used by the version notification methods to allow the matcher to indicate when it no
   * longer wants the origin system to retain a reference to the message. This allows for reliable transports
   * (for example message queuing systems) to retain messages in the case of failure of the differencing system.
   */
  type AckCallback = Function0[Unit]

  /**
   * Handles an event that has been received from a participant. When a match has occurred, or the event has
   * expired, the acknowledgement callback will be invoked to inform the message origin that the message no longer
   * needs to be retained.
   */
  def onChange(evt:PairChangeEvent, eventAckCallback:AckCallback)
  
  /**
   * Adds a listener to be informed when a stream status events occur.
   */
  def addListener(l:MatchingStatusListener)

  /**
   * Queries whether an entity with the given VersionID is active within the matcher.
   * @returns true - an entity with the given VersionID is currently being monitored by the matcher;
   *          false - the matcher currently has no state around that provided ID.
   *
   */
  def isVersionIDActive(id:VersionID):Boolean

  /**
   * Disposes the matcher.
   */
  def dispose
}