/**
 * Copyright (C) 2011 LShift Ltd.
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

import org.junit.Assert._
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.util.EasyMockScalaUtils._
import net.lshift.diffa.kernel.differencing.DomainDifferenceStore
import org.joda.time.DateTime
import org.easymock.IAnswer
import java.util.concurrent.{TimeUnit, CountDownLatch}
import org.junit.{After, Test}

/**
 * Test cases for the Sweeper.
 */
class SweeperTest {
  val diffStore = createStrictMock(classOf[DomainDifferenceStore])
  val sweeper = new Sweeper(1, diffStore)

  /**
   * Ensure that the expire method is called at least 3 times using a countdown latch. Also ensure
   * that the sweeper doesn't trigger too quickly.
   */
  @Test
  def shouldPeriodicallyCallExpireMethod() {
    val latch = new CountDownLatch(3)
    val expireStart = new DateTime().minusMinutes(5)

    diffStore.expireMatches(between(expireStart, expireStart.plusSeconds(10)))
    expectLastCall[Unit]().andAnswer(new IAnswer[Unit] {
      def answer() {
        latch.countDown()
      }
    }).anyTimes()
    replay(diffStore)

    val startTime = System.currentTimeMillis()
    sweeper.start()
    latch.await(10, TimeUnit.SECONDS)

    val duration = (System.currentTimeMillis() - startTime);
    assertTrue(duration + "ms was not at least 2s", duration > 2000)
  }

  @After
  def cleanup() {
    sweeper.shutdown()
  }
}