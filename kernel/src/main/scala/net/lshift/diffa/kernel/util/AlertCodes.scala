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

/**
 * A dictionary of alert codes that can be used to classify errors in log files quicker
 */
object AlertCodes {

  /**
   * Occurs when an actor receives an out of order message. This can occur as a result of a downstream error.
   */
  val OUT_OF_ORDER_MESSAGE = "D1"
  /**
   * Occurs when an actor receives an unexpected message. This generally indicates a bug in Diffa.
   */
  val SPURIOUS_ACTOR_MESSAGE = "D2"

  /**
   * Occurs when a cancellation for all pending scans to a particular pair is requested.
   */
  val CANCELLATION_REQUEST = "D3"

  /**
   * Signifies that an actor has timed out waiting for a message to arrive
   */
  val MESSAGE_RECEIVE_TIMEOUT = "D4"

  /**
   * Signifies the result of a scanning operation
   */
  val SCAN_OPERATION = "D5"

  /**
   * Occurs when the agent fails to establish communication with a repair action endpoint
   */
  val ACTION_ENDPOINT_FAILURE = "D6"

  /**
   * Indicates that the scan scheduler is starting a scan.
   */
  val SCHEDULED_SCAN_STARTING = "D7"

  /**
   * Indicates the scheduler failed to start a scheduled scan.
   */
  val SCHEDULED_SCAN_FAILURE = "D8"

  /**
   * Indicates the system is not configured properly.
   */
  val INVALID_SYSTEM_CONFIGURATION = "D9"

  /**
   * Indicates a given domain is not valid or does not exist.
   */
  val INVALID_DOMAIN = "D10"

  /**
   * Indicates that an error has occurred in the message processing infrastructure
   */
  val GENERAL_MESSAGING_ERROR = "D11"
}