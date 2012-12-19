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

package net.lshift.diffa.snowflake

import org.junit.runner.RunWith
import org.scalacheck.Gen
import org.scalacheck.Prop
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.ScalaCheck
import org.specs2.runner.JUnitRunner
import java.lang.Math.{round, pow}

@RunWith(classOf[JUnitRunner])
class SnowflakeIdProviderSpec extends SpecificationWithJUnit with ScalaCheck {
  final val timestampMask = 0xffffffffffc00000L
  final val machineIdMask = 0x00000000003ff000L
  final val sequenceMask  = 0x0000000000000fffL

  final val timestampBits = 41
  final val machineBits = 10
  final val sequenceBits = 12

  final val timestampUpperBound: Long = round(pow(2, timestampBits)) - 1
  final val machineIdUpperBound: Short = (round(pow(2, machineBits)) - 1).asInstanceOf[Short]
  final val sequenceUpperBound: Short = (round(pow(2, sequenceBits)) - 1).asInstanceOf[Short]

  def retryEvery(retryInterval: Long)(fn: => Unit) {
    try {
      fn
    } catch {
      case ex: SequenceExhaustedException =>
        Thread.`yield`()
        retryEvery(retryInterval)(fn)
    }
  }

  def getWithRetry(provider: SnowflakeIdProvider): Long = {
    try {
      provider.getId()
    } catch {
      case ex: SequenceExhaustedException =>
        Thread.sleep(1L)
        getWithRetry(provider)
    }
  }

  "SnowflakeIdProvider" should {

    "generate an ID" in {
      val machineId = 1
      val provider = new SnowflakeIdProvider(machineId)
      (provider.getId should beGreaterThan(0L))
    }

    "generate 1,000,000 identifiers in less than one second" in {
      val provider = new SnowflakeIdProvider(1)
      val targetExecutionCount = 1000000
      val targetExecutionTime = 1000L // 1000 milliseconds == one second

      val startTime = System.currentTimeMillis()
      (1 to targetExecutionCount) foreach { i =>
        retryEvery(100L) { provider.getId() }
      }
      (System.currentTimeMillis() - startTime) must be lessThan targetExecutionTime
    }

    "generate at time T an ID that has a timestamp component of T" in {
      val ts = Gen.choose(1, timestampUpperBound)

      check(Prop.forAll(ts) { timestamp =>
        val provider = new SnowflakeIdProvider(1)
        provider.setTimeFn(new TimeFunction { def now = timestamp })
        ((provider.getId() & timestampMask) >> (SnowflakeIdProvider.machineBits + SnowflakeIdProvider.sequenceBits)) must_== timestamp
      })
    }

    "generate an ID that has a machine ID component matching the generating ID Provider" in {
      val machineId = Gen.choose(0, machineIdUpperBound)

      check(Prop.forAll(machineId) { id =>
        val provider = new SnowflakeIdProvider(id)
        ((provider.getId() & machineIdMask) >> SnowflakeIdProvider.sequenceBits) must_== id
      })
    }

    "not operate with a machine ID outside the range [0, 1023]" in {
      check(Prop.forAll { id: Int => (id < 0 || id > 1023) ==> {
          (new SnowflakeIdProvider(id)) should throwAn[IllegalArgumentException]
        }
      })
    }

    "generate the first ID with a sequence component of 0" in {
      ((new SnowflakeIdProvider(1)).getId & sequenceMask) should_== 0
    }

    "generate a second ID for the same timestamp with a sequence component of 1" in {
      val provider = new SnowflakeIdProvider(1)
      provider.setTimeFn(new TimeFunction { def now = 1L })

      provider.getId()
      (provider.getId() & sequenceMask) should_== 1
    }

    "generate the (k+1)th ID for the same timestamp with a sequence component of k" in {
      val seqNum = Gen.choose(1, SnowflakeIdProvider.sequenceUpperBound)

      check(Prop.forAll(seqNum) { k =>
        val provider = new SnowflakeIdProvider(1)
        provider.setTimeFn(new TimeFunction { def now = 1L })

        (1 to k) foreach { i => provider.getId() }
        (provider.getId() & sequenceMask) must_== k
      })
    }

    "serialise requests to generate an ID" in {
      val pauseGen = Gen.choose(50, 100)

      check(Prop.forAllNoShrink(pauseGen) { pause =>
        val provider = new SnowflakeIdProvider(1)
        provider.setPauseMs(pause)
        val otherThread = new Thread(new Runnable() {
          def run() {
            provider.getId()
          }
        })
        val startTime = System.currentTimeMillis()
        otherThread.start()
        provider.getId()
        otherThread.join()
        (System.currentTimeMillis() - startTime) must be greaterThanOrEqualTo (2 * pause)
      })(set(minTestsOk -> 5))
    }

    "generate k-ordered identifiers over a period" in {
      val provider = new SnowflakeIdProvider(1)
      val period = 2000L // milliseconds
      var lastId = -1L

      val startTime = System.currentTimeMillis()
      while (elapsedTime < period) {
        val nextId = getWithRetry(provider)
        nextId must be greaterThan lastId
        nextId must be greaterThan 0
        lastId = nextId
      }

      def elapsedTime = System.currentTimeMillis() - startTime
    }

    "generate only unique identifiers, even when time runs backwards" in {
      val provider = new SnowflakeIdProvider(1)
      provider.setTimeFn(new TimeFunction { def now = 3L })
      val id = provider.getId()
      provider.setTimeFn(new TimeFunction { def now = 2L })
      try {
        provider.getId()
        failure("getId() should throw an InvalidSystemClockException when its clock runs backwards")
      } catch {
        case ex: InvalidSystemClockException =>
        case x => throw x
      }
    }
  }
}
