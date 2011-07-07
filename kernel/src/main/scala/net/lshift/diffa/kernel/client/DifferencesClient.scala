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

package net.lshift.diffa.kernel.client

import org.joda.time.DateTime
import net.lshift.diffa.kernel.participants.ParticipantType
import net.lshift.diffa.kernel.differencing.{PairScanInfo, PairScanState, SessionEvent, SessionScope}

/**
 * Interface supported by clients capable of retrieving differences from the server.
 */
trait DifferencesClient {
  /**
   * Retrieves whether the client supports polling retrieval of differences.
   */
  def supportsPolling:Boolean

  /**
   * Retrieves whether the client supports streaming retrieval of differences.
   */
  def supportsStreaming:Boolean

  /**
   * Calls subscribe/3 will default date values
   */
  def subscribe(scope:SessionScope) : String = subscribe(scope, null, null)

  /**
   * Creates a subscription based on the given scope, constrained with the given start and end time.
   * @throws UnsupportedOperationException if this client doesn't support polling.
   */
  def subscribe(scope:SessionScope, start:DateTime, end:DateTime):String

  /**
   * Requests that the agent run a synchronisation of the given session.
   */
  def runScan(sessionId:String): Unit

  /**
   * Retrieves the sync status of all the pairs associated with the session.
   */
  def getScanStatus(sessionId: String):Map[String, PairScanInfo]

  /**
   * Polls the identified session for all events since the start of the session.
   */
  def poll(sessionId:String): Array[SessionEvent]

  /**
   * This will poll the session identified by the id parameter for events, retrieving any events occurring
   * since the given sequence id.
   */
  def poll(sessionId:String, sinceSeqId:String): Array[SessionEvent]


  // TODO document
  def page(sessionId:String, from:DateTime, until:DateTime, offset:Int, length:Int) : Array[SessionEvent]

  /**
   * Retrieves further details of the given event with the specified session.
   */
  def eventDetail(sessionId:String, evtSeqId:String, t:ParticipantType.ParticipantType): String

  /**
   * Runs a polling loop for the given session, invoking the provided function each time a new event is received.
   */
  def pollLoop(sessionId:String, pollDelay:Int = 100)(f:(SessionEvent) => Unit) {
    var currentSeqId:Option[String] = None

    while (true) {
      val events = currentSeqId match {
        case None       => poll(sessionId)
        case Some(seq)  => poll(sessionId, seq)
      }
      events.lastOption match {
        case None    => Thread.sleep(pollDelay)
        case Some(e) => currentSeqId = Some(e.seqId)
      }
      events.foreach(f)
    }
  }
}