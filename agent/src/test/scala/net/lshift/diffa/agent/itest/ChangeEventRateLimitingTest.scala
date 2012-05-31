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

import support.TestConstants.{ agentURL, defaultDomain, yesterday }
import net.lshift.diffa.agent.client.ConfigurationRestClient
import net.lshift.diffa.kernel.frontend.EndpointDef
import net.lshift.diffa.participant.changes.ChangeEvent

import com.eaio.uuid.UUID
import org.junit.Assert.fail
import net.lshift.diffa.client.{RateLimitExceededException, ChangesRestClient}
import net.lshift.diffa.kernel.client.ChangesClient
import org.junit.{Before, BeforeClass, Test}
import com.hazelcast.util.Clock

class ChangeEventRateLimitingTest {
  import ChangeEventRateLimitingTest._

  var changesClient: ChangesClient = _
  var event: ChangeEvent = _

  @Before
  def initializeChangesClient {
    changesClient = new ChangesRestClient(agentURL, defaultDomain, endpoint)
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
  def `GIVEN default configuration AND rate limit already reached WHEN subsequent change event received THEN reject event submission` {
    try {
      var firstEvent = Clock.currentTimeMillis
      changesClient.onChangeEvent(event)
      assertFailUntil(firstEvent + ratePerSecondLimit * 1000)
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

object ChangeEventRateLimitingTest {
  private[ChangeEventRateLimitingTest] val endpoint = new UUID().toString
  private[ChangeEventRateLimitingTest] val lastUpdated = yesterday
  private[ChangeEventRateLimitingTest] val ratePerSecondLimit = 1

  @BeforeClass
  def configure {
    configureAgent(ratePerSecondLimit)
    configureEndpoint
  }

  private def configureAgent(rateLimit: Int) {
  }

  private def configureEndpoint {
    new ConfigurationRestClient(agentURL, defaultDomain).declareEndpoint(
      EndpointDef(name = endpoint))
  }
}
