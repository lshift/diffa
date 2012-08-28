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

package net.lshift.diffa.kernel.limiting

import org.easymock.EasyMock.{ createMock, expect, replay, reset }
import net.lshift.diffa.participant.changes.ChangeEvent
import org.joda.time.DateTime
import org.junit.{Before, Test, Assert}
import org.junit.runner.RunWith
import org.junit.experimental.theories.{Theories, DataPoints, Theory}
import net.lshift.diffa.schema.servicelimits.ChangeEventRate
import net.lshift.diffa.kernel.config.DomainServiceLimitsView

@RunWith(classOf[Theories])
class ChangeEventRateLimiterTest {
  import ChangeEventRateLimiterTest._

  var rateClock: Clock = createMock("rateClock", classOf[Clock])
  val rateLimitView = createMock("rateLimitView", classOf[DomainServiceLimitsView])
  var limiter: RateLimiter = _
  val event = ChangeEvent.forChange("id", "aaa111bbb222ffff", yesterday)
  val space = System.currentTimeMillis()

  @Before
  def setup() {
    setClock(0L)
    setRateLimit(1)
    limiter = new RateLimiter(
      () => rateLimitView.getEffectiveLimitByNameForDomain(space, ChangeEventRate),
      rateClock)
  }

  @Test
  def rateLimiterShouldAcceptFirstEvent() {
    Assert.assertTrue("First event should be accepted", limiter.accept())
  }

  @Theory
  def givenDefaultConfigurationRateLimiterShouldRejectSubsequentEventsWhileLimited(scenario: Scenario) {
    doForScenario[WithinLimitInterval](scenario, { d =>
      limiter.accept()

      setClock(d.relativeTime)
      Assert.assertFalse("Events received within limit window should be refused", limiter.accept())
    })
  }

  @Theory
  def givenDefaultConfigurationRateLimiterShouldAcceptSubsequentEventAfterLimitExpires(scenario: Scenario) {
    doForScenario[AfterLimitInterval](scenario, { after =>
      limiter.accept()

      setClock(after.relativeTime)
      Assert.assertTrue("An event received after the limit expires should be accepted", limiter.accept())
    })
  }

  @Theory
  def shouldRejectMoreThanConfiguredRateWithinASecond(scenario: Scenario) {
    doForScenario[RateLimit](scenario, { limit =>
      val n = limit.ratePerSecond
      setRateLimit(n)
      setClock(oneSecondAfterInitialization)
      (1 to n).foreach(i =>
        limiter.accept()
      )
      Assert.assertFalse(
        "Expected submission %d to be refused at time %d for rate %d".format(n + 1, oneSecondAfterInitialization, n),
        limiter.accept())
    })
  }

  @Theory
  def shouldAllowConfiguredRateEachSecond(scenario: Scenario) {
    doForScenario[RateLimit](scenario, { limit =>
      val n = limit.ratePerSecond
      setRateLimit(n)
      setClock(oneSecondAfterInitialization)
      (1 to n).foreach( i =>
        Assert.assertTrue("%d events should be accepted each second for this configuration".format(n), limiter.accept())
      )
    })
  }

  @Theory
  def shouldEnforceTheConfiguredRateLimitForASustainedPeriod(scenario: Scenario) {
    doForScenario[SustainedUsage](scenario, { data: SustainedUsage =>
      setRateLimit(data.ratePerSecond)
      var t = data.startingAt
      var i = 1
      while (t < secondsToMs(data.runForSeconds)) {
        data.atMsPastSecond foreach { ms =>
          setClock(t + ms)

          limiter.accept()
        }
        t += data.atInterval
        i += 1
        Assert.assertFalse(
          "Expected submission %d to be rejected in interval [%d,%d) for rate limit of %d".format(
            i, t - 1000, t, data.ratePerSecond),
          limiter.accept())
      }
    })
  }

  @Theory
  def shouldPermitTheConfiguredRateLimitForASustainedPeriod(scenario: Scenario) {
    doForScenario[SustainedUsage](scenario, { data: SustainedUsage =>
      setRateLimit(data.ratePerSecond)
      var t = data.startingAt
      var i = 1
      while (t < secondsToMs(data.runForSeconds)) {
        data.atMsPastSecond foreach { ms =>
          setClock(t + ms)

          Assert.assertTrue(
            "Expected submission %d to be accepted at time %d for rate limit of %d".format(i, t + ms, data.ratePerSecond),
            limiter.accept())
        }
        t += data.atInterval
        i += 1
      }
    })
  }

  private def setRateLimit(eventsPerSecond: Int) {
    reset(rateLimitView)
    expect(rateLimitView.getEffectiveLimitByNameForDomain(space, ChangeEventRate)).
      andReturn(eventsPerSecond).anyTimes()
    replay(rateLimitView)
  }

  private def setClock(relativeTime: Long) {
    reset(rateClock)
    expect(rateClock.currentTimeMillis).andReturn(relativeTime).anyTimes()
    replay(rateClock)
  }

  private def doForScenario[T: ClassManifest](scenario: Scenario, block: T => Unit) {
    if (scenario.data.getClass == classManifest[T].erasure)
      block(scenario.data.asInstanceOf[T])
  }

  private def secondsToMs(seconds: Int): Long = seconds * 1000L
}

object ChangeEventRateLimiterTest {
  private[ChangeEventRateLimiterTest] val yesterday = (new DateTime) minusDays 1
  // See the Important Note in RateLimiter regarding delayed effect of rate limit changes.
  private[ChangeEventRateLimiterTest] val oneSecondAfterInitialization = 1000L

  case class Scenario(data: Data)

  implicit def long2Data(l: Long): Data = if (l < 1000L) {
    WithinLimitInterval(l)
  } else {
    AfterLimitInterval(l)
  }

  @DataPoints
  def unacceptableEventTimes = Array(
    Scenario(1L),
    Scenario(100L),
    Scenario(999L)
  )

  @DataPoints
  def acceptableTimes = Array(
    Scenario(1000L),
    Scenario(1001L),
    Scenario(1100L)
  )

  @DataPoints
  def rateLimits = Array(
    Scenario(RateLimit(1)),
    Scenario(RateLimit(2)),
    Scenario(RateLimit(3)),
    Scenario(RateLimit(4))
  )

  @DataPoints
  def sustainedUsage = Array(
    Scenario(SustainedUsage(atInterval = 1000L, runForSeconds = 30, ratePerSecond = 1, atMsPastSecond = List(1), startingAt = 1000L)),
    Scenario(SustainedUsage(atInterval = 1000L, runForSeconds = 30, ratePerSecond = 5, atMsPastSecond = List(1, 2, 3, 4, 5), startingAt = 1000L)),
    Scenario(SustainedUsage(atInterval = 1000L, runForSeconds = 3600, ratePerSecond = 50, atMsPastSecond = List.fill(50)(1), startingAt = 1000L)),
    Scenario(SustainedUsage(atInterval = 3000L, runForSeconds = 3600, ratePerSecond = 500,
      atMsPastSecond = List.fill(100)(1) ::: List.fill(100)(100) ::: List.fill(100)(200) ::: List.fill(100)(600) ::: List.fill(100)(999),
      startingAt = 1000L))
  )
}

trait Data
case class WithinLimitInterval(relativeTime: Long) extends Data
case class AfterLimitInterval(relativeTime: Long) extends Data
case class RateLimit(ratePerSecond: Int) extends Data
case class SustainedUsage(atInterval: Long, runForSeconds: Int, ratePerSecond: Int, atMsPastSecond: Seq[Int], startingAt: Long) extends Data
