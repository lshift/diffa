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
import net.lshift.diffa.kernel.frontend.EscalationDef
import net.lshift.diffa.kernel.reporting.ReportManager
import net.lshift.diffa.kernel.differencing._
import org.easymock.classextension.{EasyMock => EasyMock4Classes}
import org.junit.Assume._
import org.hamcrest.CoreMatchers._
import net.lshift.diffa.kernel.lifecycle.NotificationCentre
import org.easymock.{IAnswer, EasyMock}
import org.junit.{Ignore, Before, After}
import akka.actor.ActorSystem

@RunWith(classOf[Theories])
class EscalationManagerTest {

  val domain = "domain"
  val pairKey = "some pair key"
  val pair = DiffaPair(key = pairKey, domain = Domain(name=domain))
  val actorSystem = ActorSystem("EscalationManagerTestAt%#x".format(hashCode()))
  actorSystem.registerOnTermination(println("Per-test actor system shutdown; %s".format(this)))

  val notificationCentre = new NotificationCentre
  val configStore = createMock(classOf[DomainConfigStore])
  val actionsClient = createStrictMock(classOf[ActionsClient])
  val reportManager = EasyMock4Classes.createStrictMock(classOf[ReportManager])
  val escalationManager = new EscalationManager(configStore, actionsClient, reportManager, actorSystem)

  escalationManager.onAgentInstantiationCompleted(notificationCentre)

  @Before
  def startActor = escalationManager.startActor(pair.asRef)

  @After
  def shutdown = escalationManager.close()

  def expectConfigStoreWithRepairs(event:String) {

    expect(configStore.listEscalationsForPair(domain, pairKey)).andReturn(
      List(EscalationDef("foo", pairKey, "bar", EscalationActionType.REPAIR, event, EscalationOrigin.SCAN))
    ).anyTimes()

    replay(configStore)
  }

  def expectConfigStoreWithReports(event:String) {

    expect(configStore.listEscalationsForPair(domain, pairKey)).andReturn(
      List(EscalationDef("foo", pairKey, "bar", EscalationActionType.REPORT, event))
    ).anyTimes()

    replay(configStore)
  }

  def expectActionsClient(count:Int, monitor: Object) {
    if (count > 0) {
      val answer = new IAnswer[InvocationResult] {
        var counter = 0
        def answer = {
          counter += 1
          if (counter == count) monitor.synchronized {
            monitor.notifyAll()
          }
          InvocationResult("200", "Success")
        }
      }
      expect(actionsClient.invoke(EasyMock.isA(classOf[ActionableRequest]))).andAnswer(answer).times(count)
    }
    replay(actionsClient)
  }

  def expectReportManager(count:Int) {
    if (count > 0) {
      reportManager.executeReport(pair.asRef, "bar"); expectLastCall.times(count)
    }
    EasyMock4Classes.replay(reportManager)
  }

  @Theory
  def entityEscalationsSometimesTriggerActions(scenario:Scenario) = {
    assumeThat(scenario, is(instanceOf(classOf[EntityScenario])))
    val entityScenario = scenario.asInstanceOf[EntityScenario]

    val callCompletionMonitor = new Object

    expectConfigStoreWithRepairs(entityScenario.event)
    expectActionsClient(entityScenario.invocations, callCompletionMonitor)
    expectReportManager(0)

    callCompletionMonitor.synchronized {
      notificationCentre.onMismatch(VersionID(pair.asRef, "id"), new DateTime(), entityScenario.uvsn, entityScenario.dvsn, entityScenario.matchOrigin, MatcherFiltered)

      if (entityScenario.invocations > 0) {
        callCompletionMonitor.wait()
      }
    }

    verifyAll()
  }

  @Theory
  def pairEscalationsSometimesTriggerReports(scenario:Scenario) = {
    assumeThat(scenario, is(instanceOf(classOf[PairScenario])))
    val pairScenario = scenario.asInstanceOf[PairScenario]
    
    expectConfigStoreWithReports(pairScenario.event)
    expectActionsClient(0, new Object)
    expectReportManager(pairScenario.invocations)
    
    notificationCentre.pairScanStateChanged(pair.asRef, pairScenario.state)
    
    verifyAll()
  }

  def verifyAll() {
    verify(configStore, actionsClient)
    EasyMock4Classes.verify(reportManager)
  }
}

abstract class Scenario
case class EntityScenario(uvsn:String, dvsn: String, event: String, matchOrigin: MatchOrigin, invocations: Int) extends Scenario
case class PairScenario(state:PairScanState, event: String, invocations: Int) extends Scenario

object EscalationManagerTest {

  @DataPoints def mismatchShouldBeEscalated = Array (
    EntityScenario("uvsn", "dsvn", EscalationEvent.MISMATCH, TriggeredByScan, 1),
    EntityScenario("uvsn", "", EscalationEvent.MISMATCH, TriggeredByScan, 0),
    EntityScenario("", "dsvn", EscalationEvent.MISMATCH, TriggeredByScan, 0)
  )

  @DataPoints def missingDownstreamShouldBeEscalated = Array (
    EntityScenario("uvsn", "", EscalationEvent.DOWNSTREAM_MISSING, TriggeredByScan, 1),
    EntityScenario("uvsn", "dvsn", EscalationEvent.DOWNSTREAM_MISSING, TriggeredByScan, 0),
    EntityScenario("", "dvsn", EscalationEvent.DOWNSTREAM_MISSING, TriggeredByScan, 0)
  )

  @DataPoints def missingUpstreamShouldBeEscalated = Array (
    EntityScenario("uvsn", "", EscalationEvent.UPSTREAM_MISSING, TriggeredByScan, 0),
    EntityScenario("uvsn", "dvsn", EscalationEvent.UPSTREAM_MISSING, TriggeredByScan, 0),
    EntityScenario("", "dvsn", EscalationEvent.UPSTREAM_MISSING, TriggeredByScan, 1)
  )

  @DataPoint def liveWindowShouldNotGetEscalated =
    EntityScenario("uvsn", "dvsn", EscalationEvent.MISMATCH, LiveWindow, 0)

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
