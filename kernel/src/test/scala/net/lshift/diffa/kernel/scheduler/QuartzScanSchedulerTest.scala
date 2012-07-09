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
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.kernel.frontend.{PairViewDef, DomainPairDef}

/**
 * Test cases for the QuartzScanScheduler.
 */
@RunWith(classOf[ConcurrentJunitRunner])
@Concurrent(threads = 20)
class QuartzScanSchedulerTest {
  val systemConfig = createStrictMock(classOf[SystemConfigStore])
  val domainConfig = createStrictMock(classOf[DomainConfigStore])
  val pairPolicyClient = createStrictMock(classOf[PairPolicyClient])

  @Test
  def shouldAllowScheduleCreation() {
    val mb = createExecuteListenerQueue

    val pair = DomainPairDef(key = "PairA", domain="domain", scanCronSpec = generateNowishCronSpec)

    expect(systemConfig.listPairs).andReturn(Seq())
    expect(domainConfig.getPairDef(pair.asRef)).andStubReturn(pair)

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, domainConfig, pairPolicyClient, "shouldAllowScheduleCreation")) { scheduler =>
      scheduler.onUpdatePair(pair.asRef)

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

    val pair = DomainPairDef(key = "PairA", domain="domain", scanCronSpec = null,
      views = List(buildView(pairKey = "PairA", name = "someview", scanCronSpec = generateNowishCronSpec)))

    expect(systemConfig.listPairs).andReturn(Seq())
    expect(domainConfig.getPairDef(pair.asRef)).andStubReturn(pair)

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, domainConfig, pairPolicyClient, "shouldAllowScheduleCreationForView")) { scheduler =>
      scheduler.onUpdatePair(pair.asRef)

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

    val pair = DomainPairDef(key = "PairB", domain="domain", scanCronSpec = generateNowishCronSpec)

    expect(systemConfig.listPairs).andReturn(Seq(pair))
    expect(domainConfig.getPairDef(pair.asRef)).andReturn(pair)

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, domainConfig, pairPolicyClient, "shouldRestoreSchedulesOnStartup")) { scheduler =>
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

    val pair = DomainPairDef(key = "PairC", domain="domain", scanCronSpec = generateNowishCronSpec)

    expect(systemConfig.listPairs).andReturn(Seq(pair))
    expect(domainConfig.getPairDef(pair.asRef)).andStubReturn(pair)

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, domainConfig, pairPolicyClient, "shouldAllowSchedulesToBeDeleted")) { scheduler =>
      scheduler.onDeletePair(pair.asRef)

      mb.poll(3, TimeUnit.SECONDS) match {
        case null =>
        case ScanInvocation(p, v) => fail("Scheduler should not have started scan for pair " + p)
      }
    }
  }

  @Test
  def shouldAllowSchedulesToBeUpdated() {
    val mb = createExecuteListenerQueue

    val p1 = DomainPairDef(key = "PairD", domain="domain", scanCronSpec = generateOldCronSpec)
    val p2 = DomainPairDef(key = "PairD", domain="domain", scanCronSpec = generateNowishCronSpec)

    expect(systemConfig.listPairs).andReturn(Seq())
    expect(domainConfig.getPairDef(p1.asRef)).andReturn(p1).once()
    expect(domainConfig.getPairDef(p2.asRef)).andReturn(p2).once()

    replayAll()

    // Initially schedule with something too old to run, then update it with something new enough that will
    withScheduler(new QuartzScanScheduler(systemConfig, domainConfig, pairPolicyClient, "shouldAllowSchedulesToBeUpdated")) { scheduler =>
      scheduler.onUpdatePair(p1.asRef)   // We'll get a different pair result on each call
      scheduler.onUpdatePair(p2.asRef)

      mb.poll(5, TimeUnit.SECONDS) match {
        case null => fail("Scan was not triggered")
        case ScanInvocation(p, v) => assertEquals("PairD", p.key)
      }

      verify(systemConfig, domainConfig, pairPolicyClient)
    }
  }

  @Test
  def shouldAddNewViewSchedules() {
    val mb = createExecuteListenerQueue

    val oldView = buildView("PairF", "someview", generateOldCronSpec)
    val newView = buildView("PairF", "someview2", generateNowishCronSpec)

    val p1 = DomainPairDef(key = "PairF", domain="domain", scanCronSpec = generateOldCronSpec,
      views = List(oldView))
    val p2 = DomainPairDef(key = "PairF", domain="domain", scanCronSpec = null,
      views = List(oldView, newView))

    expect(systemConfig.listPairs).andReturn(Seq())
    expect(domainConfig.getPairDef(p1.asRef)).andReturn(p1).once()
    expect(domainConfig.getPairDef(p2.asRef)).andReturn(p2).once()

    replayAll()

      // Initially schedule with something that should run
    withScheduler(new QuartzScanScheduler(systemConfig, domainConfig, pairPolicyClient, "shouldAddNewViewSchedules")) { scheduler =>
      // Create the pair with some schedules that are too old to run, then update with another view
      // that has a schedule that should run
      scheduler.onUpdatePair(p1.asRef)
      scheduler.onUpdatePair(p2.asRef)

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

    val p1 = DomainPairDef(key = "PairE", domain="domain", scanCronSpec = generateNowishCronSpec,
      views = List(buildView("PairE", "someview", generateNowishCronSpec)))
    val p2 = DomainPairDef(key = "PairE", domain="domain", scanCronSpec = null,
      views = List(buildView("PairE", "someview2", generateOldCronSpec)))

    expect(systemConfig.listPairs).andReturn(Seq())
    expect(domainConfig.getPairDef(p1.asRef)).andReturn(p1).once()
    expect(domainConfig.getPairDef(p2.asRef)).andReturn(p2).once()

    replayAll()

      // Initially schedule with something that should run
    withScheduler(new QuartzScanScheduler(systemConfig, domainConfig, pairPolicyClient, "shouldRemoveUnusedPairAndViewSchedules")) { scheduler =>
      // Create the pair with some schedules that should run, then update with only one too old to run
      scheduler.onUpdatePair(p1.asRef)
      scheduler.onUpdatePair(p2.asRef)

      mb.poll(3, TimeUnit.SECONDS) match {
        case null =>
        case ScanInvocation(p, v) => fail("Scheduler should not have started scan for pair " + p)
      }
    }
  }

  @Test
  def shouldAllowPairScanSchedulesToBeDisabled {
    val mb = createExecuteListenerQueue

    val p1 = DomainPairDef(key="PairG", domain="domain", scanCronSpec=generateNowishCronSpec)
    val p2 = p1.copy(scanCronEnabled = false)

    expect(systemConfig.listPairs).andReturn(Seq())
    expect(domainConfig.getPairDef(p1.asRef)).andReturn(p1).once()
    expect(domainConfig.getPairDef(p2.asRef)).andReturn(p2).once()

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, domainConfig, pairPolicyClient, "shouldAllowPairScanSchedulesToBeDisabled")) { scheduler =>
      scheduler.onUpdatePair(p1.asRef)
      scheduler.onUpdatePair(p2.asRef)

      mb.poll(3, TimeUnit.SECONDS) match {
        case null =>
        case ScanInvocation(p, v) => fail("Scheduler should not have started scan for pair " + p)
      }
    }
  }

  @Test
  def shouldAllowPairScanSchedulesToBeReEnabled {
    val mb = createExecuteListenerQueue

    val p1 = DomainPairDef(key="PairH", domain="domain", scanCronSpec=generateNowishCronSpec, scanCronEnabled=false)
    val p2 = p1.copy(scanCronEnabled = true)

    expect(systemConfig.listPairs).andReturn(Seq())
    expect(domainConfig.getPairDef(p1.asRef)).andReturn(p1).once()
    expect(domainConfig.getPairDef(p2.asRef)).andReturn(p2).once()

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, domainConfig, pairPolicyClient, "shouldAllowPairScanSchedulesToBeReEnabled")) { scheduler =>
      scheduler.onUpdatePair(p1.asRef)
      scheduler.onUpdatePair(p2.asRef)

      mb.poll(3, TimeUnit.SECONDS) match {
        case null => fail("Scan was not triggered")
        case ScanInvocation(p, v) =>
          assertEquals("PairH", p.key)
          assertEquals(None, v)
      }
    }
  }

  @Test
  def shouldAllowViewScanSchedulesToBeDisabled {
    val mb = createExecuteListenerQueue

    val p1 = DomainPairDef(key="PairI", domain="domain", views=List(buildView("PairI", "someview", generateNowishCronSpec)))
    val p2 = p1.copy(views=List(buildView("PairI", "someview", generateNowishCronSpec, scanCronEnabled=false)))

    expect(systemConfig.listPairs).andReturn(Seq())
    expect(domainConfig.getPairDef(p1.asRef)).andReturn(p1).once()
    expect(domainConfig.getPairDef(p2.asRef)).andReturn(p2).once()

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, domainConfig, pairPolicyClient, "shouldAllowViewScanSchedulesToBeDisabled")) { scheduler =>
      scheduler.onUpdatePair(p1.asRef)
      scheduler.onUpdatePair(p2.asRef)

      mb.poll(3, TimeUnit.SECONDS) match {
        case null =>
        case ScanInvocation(p, v) => fail("Scheduler should not have started scan for pair " + p)
      }
    }
  }

  @Test
  def shouldAllowViewScanSchedulesToBeReEnabled {
    val mb = createExecuteListenerQueue

    val p1 = DomainPairDef(key="PairJ", domain="domain", views=List(buildView("PairJ", "someview", generateNowishCronSpec, false)))
    val p2 = p1.copy(views=List(buildView("PairJ", "someview", generateNowishCronSpec, true)))

    expect(systemConfig.listPairs).andReturn(Seq())
    expect(domainConfig.getPairDef(p1.asRef)).andReturn(p1).once()
    expect(domainConfig.getPairDef(p2.asRef)).andReturn(p2).once()

    replayAll()

    withScheduler(new QuartzScanScheduler(systemConfig, domainConfig, pairPolicyClient, "shouldAllowViewScanSchedulesToBeReEnabled")) { scheduler =>
      scheduler.onUpdatePair(p1.asRef)
      scheduler.onUpdatePair(p2.asRef)

      mb.poll(3, TimeUnit.SECONDS) match {
        case null => fail("Scan was not triggered")
        case ScanInvocation(p, v) =>
          assertEquals("PairJ", p.key)
          assertEquals(Some("someview"), v)
      }
    }
  }

  private def replayAll() { replay(systemConfig, domainConfig, pairPolicyClient) }

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

  private def buildView(pairKey:String, name:String, scanCronSpec:String, scanCronEnabled:Boolean = true) = {
    PairViewDef(name, scanCronSpec, scanCronEnabled)
  }

  private case class ScanInvocation(pair:DiffaPairRef, view:Option[String])
}