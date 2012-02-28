/**
 * Copyright (C) 2012 LShift Ltd.
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
package net.lshift.diffa.agent.itest

import org.junit.Test
import net.lshift.diffa.agent.itest.support.TestConstants._
import org.junit.Assert._
import net.lshift.diffa.agent.client.StatusRestClient

/**
 * Integration test for ensuring that the status of the agent can be retrieved.
 */
class StatusTest {
  val statusClient = new StatusRestClient(agentURL)

  @Test
  def shouldReturnSuccessForAgentInBootedState() {
    assertTrue(statusClient.checkStatus)
  }
}