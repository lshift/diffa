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

package net.lshift.diffa.agent.itest.support

import org.joda.time.DateTime

/**
 * Useful constants for use in test cases.
 */
object TestConstants {
  val today = new DateTime
  val yesterday = today.minusDays(1)
  val tomorrow = today.plusDays(1)
  val yearAgo = today.minusYears(1)
  val nextYear = today.plusYears(1)
  val agentHost = "localhost"
  val agentPort = 19093
  val agentUsername = "guest"
  val agentPassword = "guest"
  val agentURL = "http://" + agentHost + ":"+ agentPort + "/diffa-agent"
  val domain = "domain"
  val defaultDomain = "diffa"
  val domainsLabel = "domains"
}