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

import net.lshift.diffa.kernel.config.DomainServiceLimitsView
import net.lshift.diffa.schema.servicelimits.{Unlimited, ChangeEventRate}

class ServiceLimitsDomainRateLimiterFactory(domainLimitsView: DomainServiceLimitsView) extends DomainRateLimiterFactory {
  def createRateLimiter(space: Long): RateLimiter = new RateLimiter(
    () => domainLimitsView.getEffectiveLimitByNameForDomain(space, ChangeEventRate)
  )
}

trait DomainRateLimiterFactory extends RateLimiterFactory[Long]

trait RateLimiterFactory[T] {
  def createRateLimiter(t: T): RateLimiter
}

/**
 * Limit the rate at which actions of the specified type are permitted.
 * Any action that should be rate limited should call RateLimiter.accept before executing the
 * rate limited action.
 *
 * Important Note
 * If the rate definition changes (eventsPerSecondFn), then there is a one second delay
 * before the new definition takes effect.
 */
class RateLimiter(eventsPerSecondFn: () => Int, clock: Clock = SystemClock) extends Limiter {
  private val params = new TokenBucketParameters {
    def capacity = eventsPerSecondFn()
    override def initialVolume = eventsPerSecondFn()
    override def refillInterval = 1000L
    override def refillAmount = eventsPerSecondFn()
  }

  private val tokenBucket = TokenBucket(params, clock)
  private val acceptFn: () => Boolean = if (eventsPerSecondFn() == Unlimited.value) {
    () => true
  } else {
    () => tokenBucket.tryConsume
  }

  def accept() = acceptFn()
}

trait Limiter {
  def accept(): Boolean
}

trait TypedLimiter[ActionType] {
  def accept(action: ActionType): Boolean
}
