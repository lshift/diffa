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

package net.lshift.diffa.kernel.util

import net.lshift.diffa.kernel.config.DiffaPairRef

/**
 * A dictionary of alert codes that can be used to classify errors in log files quicker
 */
object AlertCodes {

  def formatAlertCode(pair:DiffaPairRef, code:Int) = "%s [%s/%s]".format(code, pair.domain, pair.key)
  def formatAlertCode(domain:String, pair:String, code:Int) = "%s [%s/%s]".format(code, domain, pair)
  def formatAlertCode(domain:String, code:Int) = "%s [%s]".format(code, domain)
  def formatAlertCode(code: Int) = "%d".format(code)

  // 1xx Informational

  // 12x Informational scan events
  val CANCELLATION_REQUEST_RECEIVED = 120
  val SCAN_QUERY_EVENT = 121
  val SCAN_REQUEST_IGNORED = 122

  // 13x Internal stages of a scan (for benchmarking purposes)
  val SCAN_STARTED_BENCHMARK = 130
  val SCAN_COMPLETED_BENCHMARK = 131
  val UPSTREAM_SCAN_COMPLETED_BENCHMARK = 132
  val DOWNSTREAM_SCAN_COMPLETED_BENCHMARK = 133

  // 14x Informational non-scan events
  val BREAKER_TRIPPED = 140

  // 2xx Successful

  // 22x Successful manual scan events
  // Occurs when a cancellation for all pending scans to a particular pair is requested
  val API_SCAN_STARTED = 220

  // 23x Successful scheduled scan events
  val BASIC_SCHEDULED_SCAN_STARTED = 230
  val VIEW_SCHEDULED_SCAN_STARTED = 231

  // 24x Successful child scan events
  val CHILD_SCAN_COMPLETED = 241

  // 25x Successful actor events
  val ACTOR_STARTED = 250
  val ACTOR_STOPPED = 251

  // 4xx Errors that occur that as a result of an invalid inbound request from a client

  // 42x DB errors
  val DB_ERROR = 420
  val INTEGRITY_CONSTRAINT_VIOLATED = 421

  // 5xx Errors
  
  // 50x System configuration errors
  
  // Indicates the system is not configured properly.
  val INVALID_SYSTEM_CONFIGURATION = 500
  // Indicates a given domain is not valid or does not exist.
  val INVALID_DOMAIN = 501
  // Indicates that a pair has been configured with an invalid version correlation policy
  val INVALID_VERSION_POLICY = 502
  // Indicates the use of a bogus form of external credentials
  val INVALID_EXTERNAL_CREDENTIAL_TYPE = 503

  // 51x General scan errors
  val UPSTREAM_SCAN_FAILURE = 510
  val DOWNSTREAM_SCAN_FAILURE = 511
  val SCAN_INITIALIZATION_FAILURE = 512
  val NEITHER_ENDPOINT_SUPPORT_SCANNING = 513

  // 52x Scheduled scan errors
  
  val BASIC_SCHEDULED_SCAN_FAILED = 530
  val VIEW_SCHEDULED_SCAN_FAILED = 531

  // 55x Differencing errors

  val DIFFERENCING_FAILURE = 550
  val DIFFERENCE_REPLAY_FAILURE = 551

  // 56x DB errors
  val DB_EXECUTION_ERROR = 560
  val DB_RELEASE_ERROR = 561


  // 6xx Errors interacting with external systems, generally speaking these are outbound requests

  // 60x Problems invoking actions on external systems
  
  // Occurs when the agent fails to establish communication with a repair action endpoint
  val ACTION_ENDPOINT_FAILURE = 600
  // The agent failed while attempting to clean up after an HTTP request
  val ACTION_HTTP_CLEANUP_FAILURE = 601

  // 61x External scan errors
  val SCAN_CONNECTION_REFUSED = 610
  val EXTERNAL_SCAN_ERROR = 611
  val SCAN_CONNECTION_CLOSED = 612
  val CONTENT_RETRIEVAL_FAILED = 613
  val VERSION_GENERATION_FAILED = 614
  val SCAN_SOCKET_TIMEOUT = 615
  val NON_HTTP_RESPONSE = 616

  // 65x Problems with messaging systems
  
  // Indicates that an error has occurred in the message processing infrastructure  
  val GENERAL_MESSAGING_ERROR = 650

  // 7xx Bugs

  // 71x Potential actor bugs

  // Signifies that an actor has timed out waiting for a message to arrive.
  val MESSAGE_RECEIVE_TIMEOUT = 710
  //Occurs when an actor receives an unexpected message. This generally indicates a bug in Diffa.
  val SPURIOUS_ACTOR_MESSAGE = 711
  //Occurs when an actor receives an out of order message. This can occur as a result of a downstream error.
  val OUT_OF_ORDER_MESSAGE = 712
  // Occurs when no actor is found for a given key.
  val MISSING_ACTOR_FOR_KEY = 713
  // Occurs when multiple actors are registered with the same key. This indicates a bug in Diffa.
  val MULTIPLE_ACTORS_FOR_KEY = 714
  // Occurs when it wasn't possible to initialize an actor
  val BAD_ACTOR = 715

  // 72x Potential auth bugs
  val SPURIOUS_AUTH_TOKEN = 720

  // There is a bug in the diffs store
  val INCONSISTENT_DIFF_STORE = 730
}

