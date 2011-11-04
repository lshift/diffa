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

package net.lshift.diffa.kernel.scheduler

import org.junit.Test
import org.easymock.EasyMock._
import org.junit.Assert._
import org.joda.time.DateTime
import org.junit.runner.RunWith
import net.lshift.diffa.kernel.util.{Concurrent, ConcurrentJunitRunner}
import java.util.concurrent.{TimeUnit, LinkedBlockingQueue}
import net.lshift.diffa.kernel.actors.PairPolicyClient
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import org.easymock.{EasyMock, IAnswer}
import net.lshift.diffa.kernel.config.{PairView, DiffaPairRef, Domain, Pair => DiffaPair}
import scala.collection.JavaConversions._

/**
 * Test cases for the QuartzScanScheduler.
 */
@RunWith(classOf[ConcurrentJunitRunner])
@Concurrent(threads = 20)
class QuartzScanSchedulerTest {
  val systemConfig = createStrictMock(classOf[SystemConfigStore])
  val pairPolicyClient = createStrictMock(classOf[PairPolicyClient])

  val domain = Domain(name="domain")

  @Test
  def shouldAllowScheduleCreation() {
    val mb = createExecuteListenerQueue

    val pair = DiffaPair(key = "PairA", domain=domain, scanCronSpec = generateNowishCronSpec)

    expect(systemConfig.listPairs).andReturn(Seq())

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, pairPolicyClient, "shouldAllowScheduleCreation")) { scheduler =>
      scheduler.onUpdatePair(pair)

      mb.poll(3, TimeUnit.SECONDS) match {
        case null => fail("Scan was not triggered")
        case ScanInvocation(p, v) =>
          assertEquals("PairA", p.key)
          assertEquals(None, v)
      }
    }
  }

  @Test
  def shouldAllowScheduleCreationForViews() {
    val mb = createExecuteListenerQueue

    val pair = DiffaPair(key = "PairA", domain=domain, scanCronSpec = null,
      views = Set(buildView(pairKey = "PairA", name = "someview", scanCronSpec = generateNowishCronSpec)))

    expect(systemConfig.listPairs).andReturn(Seq())

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, pairPolicyClient, "shouldAllowScheduleCreationForView")) { scheduler =>
      scheduler.onUpdatePair(pair)

      mb.poll(3, TimeUnit.SECONDS) match {
        case null => fail("Scan was not triggered")
        case ScanInvocation(p, v) =>
          assertEquals("PairA", p.key)
          assertEquals(Some("someview"), v)
      }
    }
  }

  @Test
  def shouldRestoreSchedulesOnStartup() {
    val mb = createExecuteListenerQueue

    val pair = DiffaPair(key = "PairB", domain=domain, scanCronSpec = generateNowishCronSpec)

    expect(systemConfig.listPairs).andReturn(Seq(pair))
    expect(systemConfig.getPair("domain", "PairB")).andReturn(pair)

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, pairPolicyClient, "shouldRestoreSchedulesOnStartup")) { scheduler =>
      mb.poll(3, TimeUnit.SECONDS) match {
        case null => fail("Scan was not triggered")
        case ScanInvocation(p, v) =>
          assertEquals("PairB", p.key)
          assertEquals(None, v)
      }
    }
  }

  @Test
  def shouldAllowSchedulesToBeDeleted() {
    val mb = createExecuteListenerQueue

    val pair = DiffaPair(key = "PairC", domain=domain, scanCronSpec = generateNowishCronSpec)

    expect(systemConfig.listPairs).andReturn(Seq(pair))

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, pairPolicyClient, "shouldAllowSchedulesToBeDeleted")) { scheduler =>
      scheduler.onDeletePair(pair)

      mb.poll(3, TimeUnit.SECONDS) match {
        case null =>
        case ScanInvocation(p, v) => fail("Scheduler should not have started scan for pair " + p)
      }
    }
  }

  @Test
  def shouldAllowSchedulesToBeUpdated() {
    val mb = createExecuteListenerQueue

    val p1 = DiffaPair(key = "PairD", domain=domain, scanCronSpec = generateOldCronSpec)
    val p2 = DiffaPair(key = "PairD", domain=domain, scanCronSpec = generateNowishCronSpec)

    expect(systemConfig.listPairs).andReturn(Seq())

    replayAll()

    // Initially schedule with something too old to run, then update it with something new enough that will
    withScheduler(new QuartzScanScheduler(systemConfig, pairPolicyClient, "shouldAllowSchedulesToBeUpdated")) { scheduler =>
      scheduler.onUpdatePair(p1)   // We'll get a different pair result on each call
      scheduler.onUpdatePair(p2)

      mb.poll(5, TimeUnit.SECONDS) match {
        case null => fail("Scan was not triggered")
        case ScanInvocation(p, v) => assertEquals("PairD", p.key)
      }

      verify(systemConfig, pairPolicyClient)
    }
  }

  @Test
  def shouldAddNewViewSchedules() {
    val mb = createExecuteListenerQueue

    val p1 = DiffaPair(key = "PairF", domain=domain, scanCronSpec = generateOldCronSpec,
      views = Set(buildView("PairF", "someview", generateOldCronSpec)))
    val p2 = DiffaPair(key = "PairF", domain=domain, scanCronSpec = null,
      views = p1.views ++ Set(buildView("PairF", "someview2", generateNowishCronSpec)))

    expect(systemConfig.listPairs).andReturn(Seq())

    replayAll()

      // Initially schedule with something that should run
    withScheduler(new QuartzScanScheduler(systemConfig, pairPolicyClient, "shouldAddNewViewSchedules")) { scheduler =>
      // Create the pair with some schedules that are too old to run, then update with another view
      // that has a schedule that should run
      scheduler.onUpdatePair(p1)
      scheduler.onUpdatePair(p2)

      mb.poll(5, TimeUnit.SECONDS) match {
        case null => fail("Scan was not triggered")
        case ScanInvocation(p, v) =>
          assertEquals("PairF", p.key)
          assertEquals(Some("someview2"), v)
      }

      verify(systemConfig, pairPolicyClient)
    }
  }

  @Test
  def shouldRemoveUnusedPairAndViewSchedules() {
    val mb = createExecuteListenerQueue

    val p1 = DiffaPair(key = "PairE", domain=domain, scanCronSpec = generateNowishCronSpec,
      views = Set(buildView("PairE", "someview", generateNowishCronSpec)))
    val p2 = DiffaPair(key = "PairE", domain=domain, scanCronSpec = null,
      views = Set(buildView("PairE", "someview2", generateOldCronSpec)))

    expect(systemConfig.listPairs).andReturn(Seq())

    replayAll()

      // Initially schedule with something that should run
    withScheduler(new QuartzScanScheduler(systemConfig, pairPolicyClient, "shouldRemoveUnusedPairAndViewSchedules")) { scheduler =>
      // Create the pair with some schedules that should run, then update with only one too old to run
      scheduler.onUpdatePair(p1)
      scheduler.onUpdatePair(p2)

      mb.poll(3, TimeUnit.SECONDS) match {
        case null =>
        case ScanInvocation(p, v) => fail("Scheduler should not have started scan for pair " + p)
      }
    }
  }

  private def replayAll() { replay(systemConfig, pairPolicyClient) }

  private def withScheduler[T](s:QuartzScanScheduler)(f:(QuartzScanScheduler) => T) {
    try {
      f(s)
    } finally {
      s.close()
    }
  }

  private def generateNowishCronSpec = {
    val nowish = (new DateTime).plusSeconds(2)
    nowish.getSecondOfMinute + " " + nowish.getMinuteOfHour + " " + nowish.getHourOfDay + " * * ?"
  }

  private def generateOldCronSpec = {
    val nowish = (new DateTime).minusHours(1)
    nowish.getSecondOfMinute + " " + nowish.getMinuteOfHour + " " + nowish.getHourOfDay + " * * ?"
  }

  private def createExecuteListenerQueue = {
    val q = new LinkedBlockingQueue[ScanInvocation]
    expect(pairPolicyClient.scanPair(anyObject.asInstanceOf[DiffaPairRef], anyObject.asInstanceOf[Option[String]])).andAnswer(new IAnswer[Unit] {
      def answer = {
        val pair = getCurrentArguments()(0).asInstanceOf[DiffaPairRef]
        val view = getCurrentArguments()(1).asInstanceOf[Option[String]]
        q.add(ScanInvocation(pair, view))
      }
    })

    q
  }

  private def buildView(pairKey:String, name:String, scanCronSpec:String) = {
    val view = PairView(name, scanCronSpec)
    view.pair = DiffaPair(key = pairKey, domain = domain)
    view
  }

  private case class ScanInvocation(pair:DiffaPairRef, view:Option[String])
}