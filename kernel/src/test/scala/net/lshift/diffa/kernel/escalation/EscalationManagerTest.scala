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

package net.lshift.diffa.kernel.escalation

import org.easymock.EasyMock._
import net.lshift.diffa.kernel.client.{ActionableRequest, ActionsClient}
import net.lshift.diffa.kernel.events.VersionID
import org.joda.time.DateTime
import net.lshift.diffa.kernel.frontend.wire.InvocationResult
import org.junit.runner.RunWith
import net.lshift.diffa.kernel.config._
import org.junit.experimental.theories.{DataPoints, DataPoint, Theories, Theory}
import net.lshift.diffa.kernel.config.DiffaPair
import net.lshift.diffa.kernel.reporting.ReportManager
import net.lshift.diffa.kernel.differencing._
import org.easymock.classextension.{EasyMock => EasyMock4Classes}
import org.junit.Assume._
import org.hamcrest.CoreMatchers._
import net.lshift.diffa.kernel.lifecycle.NotificationCentre
import org.easymock.{IAnswer, EasyMock}
import akka.actor.ActorSystem
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.frontend.{DomainPairDef, PairDef, EscalationDef}
import org.junit.{Ignore, Before, After}
import net.lshift.diffa.kernel.util.EasyMockScalaUtils._
import java.util.concurrent.atomic.AtomicInteger
import system.SystemConfigStore
import java.util.concurrent.{TimeUnit, CountDownLatch}

@RunWith(classOf[Theories])
class EscalationManagerTest {

  val domain = "domain"
  val pairKey = "some pair key"
  val pair = DiffaPair(key = pairKey, domain = Domain(name=domain))
  val actorSystem = ActorSystem("EscalationManagerTestAt%#x".format(hashCode()))
  actorSystem.registerOnTermination(println("Per-test actor system shutdown; %s".format(this)))

  val notificationCentre = new NotificationCentre
  val systemConfig = createMock(classOf[SystemConfigStore])
  val configStore = createMock(classOf[DomainConfigStore])
  val actionsClient = createStrictMock(classOf[ActionsClient])
  val reportManager = EasyMock4Classes.createStrictMock(classOf[ReportManager])
  val diffs = createStrictMock(classOf[DomainDifferenceStore])
  checkOrder(diffs, false)
  val escalationManager = new EscalationManager(configStore, systemConfig, diffs, actionsClient, reportManager, actorSystem)

  escalationManager.onAgentInstantiationCompleted(notificationCentre)

  @Before
  def startActor = escalationManager.startActor(pair.asRef)

  @After
  def shutdown = escalationManager.close()

  def expectConfigStoreWithRepairs(event:String) {

    expect(configStore.getPairDef(DiffaPairRef(pairKey, domain))).andReturn(
      DomainPairDef(escalations = Set(EscalationDef("foo", "bar", EscalationActionType.REPAIR, event, EscalationOrigin.SCAN)))
    ).anyTimes()
  }

  def expectConfigStoreWithReports(event:String) {

    expect(configStore.getPairDef(DiffaPairRef(pairKey, domain))).andReturn(
      DomainPairDef(escalations = Set(EscalationDef("foo", "bar", EscalationActionType.REPORT, event)))
    ).anyTimes()
  }

  def expectActionsClient(count:Int, latch: CountDownLatch) {
    if (count > 0) {
      val answer = new IAnswer[InvocationResult] {
        var counter = 0
        def answer = {
          counter += 1
          if (counter == count) latch.countDown()
          InvocationResult("200", "Success")
        }
      }
      expect(actionsClient.invoke(EasyMock.isA(classOf[ActionableRequest]))).andAnswer(answer).times(count)
    }
  }

  def expectIgnore(latch: CountDownLatch) {
    val answer = new IAnswer[DifferenceEvent] {
      def answer = {
        latch.countDown()
        DifferenceEvent()
      }
    }
    expect(diffs.ignoreEvent("d", "123")).andAnswer(answer).once()
  }

  def expectReportManager(count:Int) {
    if (count > 0) {
      reportManager.executeReport(pair.asRef, "bar"); expectLastCall.times(count)
    }
  }
  
  /**
   * Test to escalate a difference through all the various escalations that are available to it.
   */
  @Theory
  def escalationsShouldBeSelectedFromAvailableEscalations(scenario:Scenario) {
    assumeThat(scenario, is(instanceOf(classOf[EscalationSchedulingScenario])))
    val s = scenario.asInstanceOf[EscalationSchedulingScenario]

    val now = new DateTime
    val event = DifferenceEvent(
      seqId = "123", objId = VersionID(DiffaPairRef(key = "p1", domain ="d"), "id1"),
      upstreamVsn = s.uvsn, downstreamVsn = s.dvsn, detectedAt = now,
      nextEscalation = null)

    (s.expectedSelections ++ Seq(Selection(null, None))).foreach(selection => {
      val expectedTime = selection.delay.map(d => now.plusSeconds(d)).getOrElse(null)

      diffs.scheduleEscalation(event,  selection.name, expectedTime); expectLastCall
      expect(configStore.getPairDef(event.objId.pair)).andReturn(DomainPairDef(escalations = new java.util.HashSet(s.escalations)))
      replayAll()

      escalationManager.initiateEscalation(event)
      verifyAll()

      event.nextEscalation = selection.name
      resetAll()
    })
  }

  /**
   * Test to ensure that escalations are executed.
   */
  @Theory
  def escalationsShouldBeExecuted(scenario:Scenario) {
    assumeThat(scenario, is(instanceOf(classOf[EscalationSchedulingScenario])))
    val s = scenario.asInstanceOf[EscalationSchedulingScenario]
    assumeTrue(s.expectedSelections.length > 0)

    val now = new DateTime
    val event = DifferenceEvent(
      seqId = "123", objId = VersionID(DiffaPairRef(key = "p1", domain ="d"), "id1"),
      upstreamVsn = s.uvsn, downstreamVsn = s.dvsn, detectedAt = now,
      nextEscalation = s.expectedSelections.head.name)
    val callCounter = new AtomicInteger(0)
    val actionCompletionMonitor = new CountDownLatch(1)
    val schedulingCompletionMonitor = new CountDownLatch(1)

    // Return our pair to have a corresponding actor started
    expect(systemConfig.listPairs).andReturn(
      Seq(DomainPairDef(domain = event.objId.pair.domain, key = event.objId.pair.key)))

    // Make the diffs store return the difference once
    expect(diffs.pendingEscalatees(anyTimestamp, anyUnitF1)).andAnswer(new IAnswer[Unit] {
      def answer() {
        val callback = EasyMock.getCurrentArguments()(1).asInstanceOf[(DifferenceEvent) => Unit]
        if (callCounter.getAndSet(1) == 0) {
          callback(event)
        }
      }
    })
    expect(configStore.getPairDef(event.objId.pair)).
      andReturn(DomainPairDef(escalations = new java.util.HashSet(
        s.escalations.map(e => {
          if (s.actionType == EscalationActionType.IGNORE)
            e.copy(actionType = EscalationActionType.IGNORE)
          else
            e.copy(actionType = EscalationActionType.REPAIR, action = "some-action")
        })))).atLeastOnce()
    if (s.actionType == EscalationActionType.REPAIR) expectActionsClient(1, actionCompletionMonitor)
    if (s.actionType == EscalationActionType.IGNORE) expectIgnore(actionCompletionMonitor)
    expect(diffs.scheduleEscalation(EasyMock.eq(event), anyString, anyTimestamp)).andAnswer(new IAnswer[Unit] {
      def answer() {
        schedulingCompletionMonitor.countDown()
      }
    })
    replayAll()

    escalationManager.start()

    actionCompletionMonitor.await(5000, TimeUnit.MILLISECONDS)
    schedulingCompletionMonitor.await(5000, TimeUnit.MILLISECONDS)
    verifyAll()
  }

  @Theory
  def pairEscalationsSometimesTriggerReports(scenario:Scenario) = {
    assumeThat(scenario, is(instanceOf(classOf[PairScenario])))
    val pairScenario = scenario.asInstanceOf[PairScenario]
    
    expectConfigStoreWithReports(pairScenario.event)
    expectActionsClient(0, new CountDownLatch(0))
    expectReportManager(pairScenario.invocations)
    replayAll()
    
    notificationCentre.pairScanStateChanged(pair.asRef, pairScenario.state)
    
    verifyAll()
  }

  def resetAll() {
    reset(configStore, systemConfig, actionsClient, diffs)
    EasyMock4Classes.reset(reportManager)
  }

  def replayAll() {
    replay(configStore, systemConfig, actionsClient, diffs)
    EasyMock4Classes.replay(reportManager)
  }

  def verifyAll() {
    verify(configStore, systemConfig, actionsClient, diffs)
    EasyMock4Classes.verify(reportManager)
  }
}

case class Selection(name:String, delay:Option[Int])

abstract class Scenario
case class EntityScenario(uvsn:String, dvsn: String, event: String, matchOrigin: MatchOrigin, invocations: Int) extends Scenario
case class PairScenario(state:PairScanState, event: String, invocations: Int) extends Scenario
case class EscalationSchedulingScenario(uvsn:String, dvsn:String, actionType:String, escalations:Seq[EscalationDef], expectedSelections:Selection*) extends Scenario

object EscalationManagerTest {

  @DataPoint def noEscalationsToSelect =
    EscalationSchedulingScenario("usvn", "dvsn", EscalationActionType.REPAIR, Seq())

  @DataPoints def noMatchingEscalations = Array(
    EscalationSchedulingScenario("usvn", "dvsn", EscalationActionType.REPAIR, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.UPSTREAM_MISSING),
      EscalationDef(name = "e2", event = EscalationEvent.DOWNSTREAM_MISSING)
    )),
    EscalationSchedulingScenario(null, "dvsn", EscalationActionType.REPAIR, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.MISMATCH),
      EscalationDef(name = "e2", event = EscalationEvent.DOWNSTREAM_MISSING)
    )),
    EscalationSchedulingScenario("usvn", null, EscalationActionType.REPAIR, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.UPSTREAM_MISSING),
      EscalationDef(name = "e2", event = EscalationEvent.MISMATCH)
    ))
  )

  @DataPoints def immediateMatchingEscalations = Array(
    EscalationSchedulingScenario("usvn", "dvsn", EscalationActionType.REPAIR, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.UPSTREAM_MISSING),
      EscalationDef(name = "e2", event = EscalationEvent.MISMATCH)
    ), Selection("e2", Some(0))),
    EscalationSchedulingScenario(null, "dvsn", EscalationActionType.REPAIR, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.MISMATCH),
      EscalationDef(name = "e2", event = EscalationEvent.UPSTREAM_MISSING)
    ), Selection("e2", Some(0))),
    EscalationSchedulingScenario("usvn", null, EscalationActionType.REPAIR, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.UPSTREAM_MISSING),
      EscalationDef(name = "e2", event = EscalationEvent.DOWNSTREAM_MISSING)
    ), Selection("e2", Some(0)))
  )

  @DataPoints def ignoreEscalations = Array(
    EscalationSchedulingScenario("usvn", "dvsn", EscalationActionType.IGNORE, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.UPSTREAM_MISSING),
      EscalationDef(name = "e2", event = EscalationEvent.MISMATCH)
    ), Selection("e2", Some(0))),
    EscalationSchedulingScenario(null, "dvsn", EscalationActionType.IGNORE, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.MISMATCH),
      EscalationDef(name = "e2", event = EscalationEvent.UPSTREAM_MISSING)
    ), Selection("e2", Some(0))),
    EscalationSchedulingScenario("usvn", null, EscalationActionType.IGNORE, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.UPSTREAM_MISSING),
      EscalationDef(name = "e2", event = EscalationEvent.DOWNSTREAM_MISSING)
    ), Selection("e2", Some(0)))
  )

  @DataPoints def delayedEscalationsProcessedInOrder = Array(
    EscalationSchedulingScenario("usvn", "dvsn", EscalationActionType.REPAIR, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.MISMATCH, delay = 50),
      EscalationDef(name = "e2", event = EscalationEvent.MISMATCH, delay = 10)
    ), Selection("e2", Some(10)), Selection("e1", Some(50))),
    EscalationSchedulingScenario("usvn", "dvsn", EscalationActionType.REPAIR, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.MISMATCH, delay = 50),
      EscalationDef(name = "e2", event = EscalationEvent.UPSTREAM_MISSING, delay = 20),
      EscalationDef(name = "e3", event = EscalationEvent.MISMATCH, delay = 10)
    ), Selection("e3", Some(10)), Selection("e1", Some(50)))
  )

  @DataPoints def noProgressingToInvalidScenarios = Array(
    EscalationSchedulingScenario("usvn", "dvsn", EscalationActionType.REPAIR, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.UPSTREAM_MISSING),
      EscalationDef(name = "e2", event = EscalationEvent.MISMATCH)
    ), Selection("e2", Some(0))),
    EscalationSchedulingScenario(null, "dvsn", EscalationActionType.REPAIR, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.MISMATCH),
      EscalationDef(name = "e2", event = EscalationEvent.UPSTREAM_MISSING)
    ), Selection("e2", Some(0))),
    EscalationSchedulingScenario("usvn", null, EscalationActionType.REPAIR, Seq(
      EscalationDef(name = "e1", event = EscalationEvent.UPSTREAM_MISSING),
      EscalationDef(name = "e2", event = EscalationEvent.DOWNSTREAM_MISSING)
    ), Selection("e2", Some(0)))
  )

  @DataPoints def scanCompletedShouldBeEscalated = Array(
    PairScenario(PairScanState.UP_TO_DATE, EscalationEvent.SCAN_COMPLETED, 1),
    PairScenario(PairScanState.FAILED, EscalationEvent.SCAN_COMPLETED, 0),
    PairScenario(PairScanState.CANCELLED, EscalationEvent.SCAN_COMPLETED, 0)
  )

  @DataPoints def scanFailedShouldBeEscalated = Array(
    PairScenario(PairScanState.UP_TO_DATE, EscalationEvent.SCAN_FAILED, 0),
    PairScenario(PairScanState.FAILED, EscalationEvent.SCAN_FAILED, 1),
    PairScenario(PairScanState.CANCELLED, EscalationEvent.SCAN_FAILED, 0)
  )

}
