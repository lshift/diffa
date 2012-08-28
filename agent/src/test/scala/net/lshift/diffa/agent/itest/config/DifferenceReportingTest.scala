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

package net.lshift.diffa.agent.itest.config

import net.lshift.diffa.agent.itest.support.TestConstants._
import net.lshift.diffa.agent.client.DifferencesRestClient
import net.lshift.diffa.kernel.participants.ParticipantType
import org.junit.Test
import net.lshift.diffa.kernel.differencing.InvalidSequenceNumberException
import org.apache.commons.lang.RandomStringUtils
import net.lshift.diffa.agent.itest.IsolatedDomainTest

/**
 * A bunch of smoke tests for the differences reporting of a known agent
 */
class DifferenceReportingTest extends IsolatedDomainTest {

  // Assume that the id given is sufficiently large that it won't be hit in test cases
  val invalidSeqId = RandomStringUtils.randomNumeric(7)

  @Test(expected = classOf[InvalidSequenceNumberException])
  def nonExistentSequenceNumberShouldGenerateNotSequenceError() {
    val client = new DifferencesRestClient(agentURL, isolatedDomain)
    client.eventDetail(invalidSeqId, ParticipantType.UPSTREAM)
  }
}