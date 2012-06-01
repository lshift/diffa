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

object TokenBucket {
  def apply(params: TokenBucketParameters, clock: Clock) =
    new FixedIntervalTokenBucket(params, clock)
}

trait TokenBucket {
  def tryConsume: Boolean
  def refill()
}

class FixedIntervalTokenBucket(params: TokenBucketParameters, clock: Clock) {
  private var lastDrip = clock.currentTimeMillis
  private var volume: Long = params.initialVolume

  def tryConsume: Boolean = {
    synchronized {
      refill()

      if (volume > 0) {
        volume -= 1
        true
      } else {
        false
      }
    }
  }

  def refill() {
    val now = clock.currentTimeMillis
    volume = math.min(
      volume + params.refillAmount * floor((now - lastDrip)/params.refillInterval),
      params.capacity)

    if (now - lastDrip >= params.refillInterval) {
      lastDrip = lastDrip + (now - lastDrip) / 1000 * 1000
    }
  }

  private def floor(d: Double): Long = math.round(math.floor(d))
}

trait TokenBucketParameters {
  def capacity: Long
  def initialVolume: Long = 0L
  def refillInterval: Long = 1000L // milliseconds
  def refillAmount: Long = 1L
}
