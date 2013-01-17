/**
 * Copyright (C) 2010-2012 LShift Ltd.
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

import support.TestConstants.{ agentURL, yesterday }
import net.lshift.diffa.agent.client.ConfigurationRestClient
import net.lshift.diffa.kernel.frontend.EndpointDef
import net.lshift.diffa.adapter.changes.ChangeEvent

import org.junit.Assert.fail
import net.lshift.diffa.client.{RateLimitExceededException, ChangesRestClient}
import net.lshift.diffa.kernel.client.ChangesClient
import org.junit.{Ignore, Before, Test}
import com.hazelcast.util.Clock
import org.apache.commons.lang3.RandomStringUtils

@Ignore
class ChangeEventRateLimitingTest extends IsolatedDomainTest {

  var clientCreateTime: Long = 0L
  var changesClient: ChangesClient = _
  var event: ChangeEvent = _

  val endpoint = RandomStringUtils.randomAlphanumeric(10)
  val lastUpdated = yesterday
  val ratePerSecondLimit = 1

  @Before
  def initializeChangesClient {

    new ConfigurationRestClient(agentURL, isolatedDomain).declareEndpoint(EndpointDef(name = endpoint))

    clientCreateTime = Clock.currentTimeMillis()
    changesClient = new ChangesRestClient(agentURL, isolatedDomain, endpoint)
    event = ChangeEvent.forChange("id", "aaff00001111", lastUpdated)

    // Make sure that no previous change events interfere with the acceptance of
    // the next test.
    Thread.sleep(1000 / ratePerSecondLimit)
  }

  @Test
  def shouldAcceptFirstEvent {
    try {
      changesClient.onChangeEvent(event)
    } catch {
      case x: RateLimitExceededException =>
        fail("First event was rate limited, but should not have been")
    }
  }

  @Test
  def givenDefaultConfigurationAndRateLimitAlreadyReachedWhenSubsequentChangeEventReceivedThenRejectEventSubmission {
    try {
      changesClient.onChangeEvent(event)
      assertFailUntil(clientCreateTime + 1000L)
    } catch {
      case x: Exception =>
        fail("Unexpected failure of first change event submission: " + x.toString)
    }
  }

  private def assertFailUntil(sysTimeMillis: Long) {
    val retryFrequency = 50 // milliseconds

    while (Clock.currentTimeMillis < sysTimeMillis) {
      try {
        changesClient.onChangeEvent(event)
        // check the time again in case the previous call took a while to execute,
        // in which case it's not necessarily true that the action should have been
        // rate limited.
        if (Clock.currentTimeMillis < sysTimeMillis) {
          fail("Change Event submission was expected to raise an exception due to violating the rate limit, but succeeded")
        }
      } catch {
        case x: RateLimitExceededException =>
      }
      Thread.sleep(retryFrequency)
    }
  }
}
