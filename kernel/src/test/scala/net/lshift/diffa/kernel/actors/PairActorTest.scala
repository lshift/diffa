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

package net.lshift.diffa.kernel.actors

import org.junit.{Test, After, Before}
import org.easymock.EasyMock._
import org.junit.Assert._
import net.lshift.diffa.kernel.differencing._
import org.joda.time.DateTime
import net.lshift.diffa.kernel.events.{UpstreamPairChangeEvent, VersionID}
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.participants._
import org.easymock.{EasyMock, IAnswer}
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.classic.spi.ILoggingEvent
import java.lang.RuntimeException
import akka.actor._
import concurrent.{SyncVar}
import net.lshift.diffa.kernel.diag.{DiagnosticLevel, DiagnosticsManager}
import net.lshift.diffa.kernel.util.{EasyMockScalaUtils, AlertCodes}
import net.lshift.diffa.kernel.config.{DomainConfigStore, DiffaPairRef, Domain, Endpoint, Pair => DiffaPair}
import net.lshift.diffa.kernel.frontend.FrontendConversions
import java.util.concurrent.{TimeUnit, LinkedBlockingQueue}
import net.lshift.diffa.participant.scanning.ScanConstraint

class PairActorTest {
  import PairActorTest._

  val domainName = "some-domain"
  val pairKey = nextPairId
  val policyName = ""
  val upstream = Endpoint(name = "up", scanUrl = "up", contentType = "application/json")
  val downstream = Endpoint(name = "down", scanUrl = "down", contentType = "application/json")

  val pair = new DiffaPair()
  pair.key = pairKey
  pair.domain = Domain(name = domainName)
  pair.versionPolicyName = policyName
  pair.upstream = upstream
  pair.downstream = downstream

  val pairRef = pair.asRef

  val us = createStrictMock("upstreamParticipant", classOf[UpstreamParticipant])
  val ds = createStrictMock("downstreamParticipant", classOf[DownstreamParticipant])
  val diagnostics = createStrictMock("diagnosticsManager", classOf[DiagnosticsManager])
  diagnostics.checkpointExplanations(pairRef); expectLastCall().asStub()
  
  val participantFactory = org.easymock.classextension.EasyMock.createStrictMock("participantFactory", classOf[ParticipantFactory])
  expect(participantFactory.createUpstreamParticipant(upstream)).andReturn(us)
  expect(participantFactory.createDownstreamParticipant(downstream)).andReturn(ds)
  org.easymock.classextension.EasyMock.replay(participantFactory)

  val versionPolicyManager = org.easymock.classextension.EasyMock.createStrictMock("versionPolicyManager", classOf[VersionPolicyManager])

  val versionPolicy = createMock("versionPolicy", classOf[VersionPolicy])
  checkOrder(versionPolicy, false)

  expect(versionPolicyManager.lookupPolicy(policyName)).andReturn(Some(versionPolicy))
  org.easymock.classextension.EasyMock.replay(versionPolicyManager)

  val systemConfigStore = createStrictMock("systemConfigStore", classOf[SystemConfigStore])

  expect(systemConfigStore.getPair(domainName, pairKey)).andStubReturn(pair)
  expect(systemConfigStore.getPair(DiffaPairRef(pairKey, domainName))).andStubReturn(pair)
  expect(systemConfigStore.listDomains).andStubReturn(Seq(Domain(name = domainName)))
  expect(systemConfigStore.listPairs).andReturn(Seq())      // Don't return our pair in the list, since we don't want it started immediately
  replay(systemConfigStore)

  val domainConfigStore = createStrictMock(classOf[DomainConfigStore])
  expect(domainConfigStore.listPairs(domainName)).andStubReturn(Seq(FrontendConversions.toPairDef(pair)))
  replay(domainConfigStore)

  val writer = createMock("writer", classOf[ExtendedVersionCorrelationWriter])

  val store = createMock("versionCorrelationStore", classOf[VersionCorrelationStore])
  expect(store.openWriter()).andStubReturn(writer)

  val stores = createStrictMock("versionCorrelationStoreFactory", classOf[VersionCorrelationStoreFactory])
  expect(stores.apply(pairRef)).andReturn(store)
  replay(stores)

  val scanListener = createStrictMock("scanListener", classOf[PairScanListener])

  val differencesManager = createStrictMock(classOf[DifferencesManager])
  val diffWriter = createStrictMock("differenceWriter", classOf[DifferenceWriter])
  expect(differencesManager.createDifferenceWriter(domainName, pairKey, overwrite = true)).andStubReturn(diffWriter)
  expect(differencesManager.lastRecordedVersion(pairRef)).andStubReturn(None)
  replay(differencesManager)

  val supervisor = new PairActorSupervisor(versionPolicyManager, systemConfigStore, domainConfigStore, differencesManager, scanListener, participantFactory, stores, diagnostics, 50, 100)
  supervisor.onAgentAssemblyCompleted
  supervisor.onAgentConfigurationActivated
  
  @After
  def stop = supervisor.stopActor(pair.asRef)

  // Check for spurious actor events
  val spuriousEventAppender = new SpuriousEventAppender

  @Before
  def attachAppenderToContext = {
    val ctx = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    spuriousEventAppender.setContext(ctx)
    spuriousEventAppender.start()
    val log = LoggerFactory.getLogger(classOf[PairActor]).asInstanceOf[ch.qos.logback.classic.Logger]
    log.addAppender(spuriousEventAppender)
  }

  @After
  def checkForSpuriousEvents = {
    if (spuriousEventAppender.hasSpuriousEvent) {
      fail("Should not have receied a spurious message: %s".format(spuriousEventAppender.lastEvent.getMessage))
    }
  }

  def expectUpstreamScan() = {
    expect(versionPolicy.scanUpstream(EasyMock.eq(pair), EasyMock.eq(None), EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
                                      EasyMock.eq(us), EasyMock.isA(classOf[DifferencingListener]),
                                      EasyMock.isA(classOf[FeedbackHandle])))
  }
  def expectDownstreamScan() = {
    expect(versionPolicy.scanDownstream(EasyMock.eq(pair), EasyMock.eq(None), EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
                                        EasyMock.eq(us), EasyMock.eq(ds), EasyMock.isA(classOf[DifferencingListener]),
                                        EasyMock.isA(classOf[FeedbackHandle])))
  }

  def expectScans() = {
    expectUpstreamScan()
    expectDownstreamScan()
  }

  def expectDifferencesReplay(assertFlush:Boolean = true) = {
    if (assertFlush) writer.flush(); expectLastCall.atLeastOnce()
    diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, "Calculating differences"); expectLastCall
    expect(store.unmatchedVersions(anyObject[Seq[ScanConstraint]], anyObject[Seq[ScanConstraint]], EasyMock.eq[Option[Long]](None))).andReturn(Seq())
    expect(store.tombstoneVersions(None)).andReturn(Seq())
    diffWriter.evictTombstones(Seq()); expectLastCall
    writer.clearTombstones(); expectLastCall
    diffWriter.close(); expectLastCall()
  }

  def expectWriterRollback() {
    expect(writer.flush()).atLeastOnce
    expect(writer.rollback())
  }

  @Test
  def runDifference = {
    val monitor = new Object
    writer.flush(); expectLastCall.atLeastOnce()
    expect(store.unmatchedVersions(anyObject[Seq[ScanConstraint]], anyObject[Seq[ScanConstraint]], EasyMock.eq[Option[Long]](None))).andReturn(Seq())
    expect(store.tombstoneVersions(None)).andReturn(Seq())
    diffWriter.evictTombstones(Seq()); expectLastCall
    writer.clearTombstones(); expectLastCall
    diffWriter.close(); expectLastCall().andAnswer(new IAnswer[Unit] {
      def answer = { monitor.synchronized { monitor.notifyAll } }
    })

    replay(versionPolicy, store, diffWriter, writer)

    supervisor.startActor(pair)
    supervisor.difference(pair.asRef)

    monitor.synchronized {
      monitor.wait(1000)
    }

    verify(versionPolicy)
  }

  @Test
  def runScan = {
    val monitor = new Object

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.SCANNING); expectLastCall.atLeastOnce()

    expectScans

    expectDifferencesReplay()

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.UP_TO_DATE); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer = { monitor.synchronized { monitor.notifyAll } }
    })
    diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, "Scan completed"); expectLastCall
    replay(writer, store, versionPolicy, scanListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pair.asRef, None)
    monitor.synchronized {
      monitor.wait(1000)
    }
    verify(writer, versionPolicy, scanListener, diagnostics)
  }

  @Test
  def backlogShouldBeProcessedAfterScan = {
    val flushMonitor = new Object
    val eventMonitor = new Object

    val event = buildUpstreamEvent()

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.SCANNING); expectLastCall.atLeastOnce()
    scanListener.pairScanStateChanged(pair.asRef, PairScanState.UP_TO_DATE); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer = { flushMonitor.synchronized { flushMonitor.notifyAll } }
    })
    replay(scanListener)

    expect(versionPolicy.onChange(writer, event))
    .andAnswer(new IAnswer[Unit] {
      def answer = {
        eventMonitor.synchronized {
          eventMonitor.notifyAll
        }
      }
    })

    expect(versionPolicy.scanUpstream(EasyMock.eq(pair),
           EasyMock.eq(None),
           EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
           EasyMock.eq(us),
           EasyMock.isA(classOf[DifferencingListener]),
           EasyMock.isA(classOf[FeedbackHandle]))
    ).andAnswer(new IAnswer[Unit] {
      def answer = {
        // Queue up a change event and block the actor in the scanning state for a 1 sec
        supervisor.propagateChangeEvent(event)
        Thread.sleep(1000)
      }
    })
    expect(versionPolicy.scanDownstream(EasyMock.eq(pair),
           EasyMock.eq(None),
           EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
           EasyMock.eq(us), EasyMock.eq(ds),
           EasyMock.isA(classOf[DifferencingListener]),
           EasyMock.isA(classOf[FeedbackHandle])))

    expectDifferencesReplay()

    replay(writer, store, diffWriter, versionPolicy)

    supervisor.startActor(pair)
    supervisor.scanPair(pair.asRef, None)

    flushMonitor.synchronized {
      flushMonitor.wait(2000)
    }
    eventMonitor.synchronized {
      eventMonitor.wait(2000)
    }

    verify(versionPolicy)
    verify(scanListener)
  }

  @Test
  def shouldHandleCancellationWhilstNotScanning = {
    writer.flush(); expectLastCall.asStub()
    replay(store, writer, diffWriter, versionPolicy)

    supervisor.startActor(pair)
    assertTrue(supervisor.cancelScans(pair.asRef))
  }


  @Test
  def shouldHandleCancellationWhilstScanning = {

    val cancelMonitor = new Object
    val responseMonitor = new Object

    val event = buildUpstreamEvent()

    val timeToWait = 2000L

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.SCANNING); expectLastCall     // Expect once when the pair actor starts the call
    scanListener.pairScanStateChanged(pair.asRef, PairScanState.SCANNING); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer = {
        Actor.spawn {
          // Request a cancellation in a background thread so that the pair actor can be scheduled
          // in to process the cancellation. Notifying the main test thread that the request
          // returned true is the same thing as assertTrue(supervisor.cancelScans(pairKey))
          // except that the assertion is effectively on the main test thread.
          if (supervisor.cancelScans(pair.asRef)) {
            responseMonitor.synchronized{ responseMonitor.notifyAll() }
          }
        }
      }
    })

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.CANCELLED); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer = cancelMonitor.synchronized{ cancelMonitor.notifyAll() }
    })
    diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, "Scan cancelled"); expectLastCall

    expectScans.andAnswer(new IAnswer[Unit] {
      def answer = {
        // Put the sub actor into a sufficiently long pause so that the cancellation request
        // has enough time to get processed by the parent actor, have it trigger the
        // the scan state listener and send a response back the thread that requested the
        // cancellation
        Thread.sleep(timeToWait)
      }
    })

    writer.flush(); expectLastCall.asStub()
    expect(writer.rollback); expectLastCall
    replay(store, writer, diffWriter, versionPolicy, scanListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pair.asRef, None)

    responseMonitor.synchronized {
      responseMonitor.wait(timeToWait * 2)
    }

    cancelMonitor.synchronized {
      cancelMonitor.wait(timeToWait * 2)
    }

    verify(versionPolicy, scanListener, diagnostics)
  }

  @Test
  def shouldReportScanFailure = {
    val monitor = new Object

    expectFailingScan(
      downstreamHandler = EasyMockScalaUtils.emptyAnswer,
      failStateHandler = new IAnswer[Unit] {
          def answer { monitor.synchronized { monitor.notifyAll } }
        })
    replay(store, writer, diffWriter, versionPolicy, scanListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pair.asRef, None)
    monitor.synchronized {
      monitor.wait(1000)
    }
    verify(versionPolicy, scanListener, diagnostics)
  }

  @Test
  def shouldCancelFeedbackHandleWhenAParticipantFails = {
    val wasMarkedAsCancelled = new SyncVar[Boolean]

    expectFailingScan(downstreamHandler = new IAnswer[Unit] {
      def answer() {
        val feedbackHandle = EasyMock.getCurrentArguments()(6).asInstanceOf[FeedbackHandle]
        awaitFeedbackHandleCancellation(feedbackHandle)
        wasMarkedAsCancelled.set(feedbackHandle.isCancelled)
      }
    })
    replay(writer, store, diffWriter, versionPolicy, scanListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pair.asRef, None)
    assertTrue(wasMarkedAsCancelled.get(4000).getOrElse(throw new Exception("Feedback handle check never reached in participant stub")))
    verify(versionPolicy, scanListener, diagnostics)
  }

  @Test
  def shouldGenerateExceptionInVersionCorrelationWriterProxyWhenAParticipantFails = {
    val proxyDidGenerateException = new SyncVar[Boolean]

    expectFailingScan(downstreamHandler = new IAnswer[Unit] {
      def answer() {
        val feedbackHandle = EasyMock.getCurrentArguments()(6).asInstanceOf[FeedbackHandle]
        awaitFeedbackHandleCancellation(feedbackHandle)

        val writer = EasyMock.getCurrentArguments()(2).asInstanceOf[LimitedVersionCorrelationWriter]
        try {
          writer.clearDownstreamVersion(VersionID(DiffaPairRef("p1","domain"), "abc"))
          proxyDidGenerateException.set(false)
        } catch {
          case c:ScanCancelledException => proxyDidGenerateException.set(true)
        }
      }
    })
    replay(store, diffWriter, writer, versionPolicy, scanListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pair.asRef, None)
    
    assertTrue(proxyDidGenerateException.get(4000).getOrElse(throw new Exception("Exception validation never reached in participant stub")))
    verify(versionPolicy, scanListener, diagnostics)
  }

  @Test
  def shouldGenerateExceptionInVersionCorrelationWriterProxyWhenAParticipantFailsAndANewScanHasStarted = {
    // NOTE: We have a couple of different actions blocking on different threads. EasyMock by default will make
    //       mocks threadsafe, which results in a deadlock. It isn't clear whether we'll have problems with disabling
    //       the threadsafety given we are calling the mock from multiple threads, but without completely skipping the
    //       use of EasyMock for this test, there aren't many other options.
    makeThreadSafe(versionPolicy, false)

    val proxyDidGenerateException = new SyncVar[Boolean]
    val secondScanIsRunning = new SyncVar[Boolean]
    val completionMonitor = new Object
    val waitForSecondScanToStartDelay = 2000    // Wait up to 2 seconds for the first scan to fail and the second to start
    val waitForStragglerToFinishDelay = 2000    // Wait up to 2 seconds for the "straggler" to finish once we've unblocked it
    val overallProcessWait = waitForSecondScanToStartDelay + waitForStragglerToFinishDelay + 1000
      // The overall process could take up to both delays, plus a bit of breathing room

    expectFailingScan(
      downstreamHandler = new IAnswer[Unit] {
          def answer() {
            if (secondScanIsRunning.get(waitForSecondScanToStartDelay).isDefined) {
              val writer = EasyMock.getCurrentArguments()(2).asInstanceOf[LimitedVersionCorrelationWriter]
              try {
                writer.clearDownstreamVersion(VersionID(DiffaPairRef("p1","domain"), "abc"))
                proxyDidGenerateException.set(false)
              } catch {
                case c:ScanCancelledException => proxyDidGenerateException.set(true)
              }
            } else {
              // Given this is on a background thread, we can't fail the test from here. The "proxyDidGenerateException"
              // call will fail later (and cause the test to fail), but this exception should help diagnose where in
              // the overall process things started falling apart.
              throw new RuntimeException("Test has failed: Expired whilst waiting for second scan to start")
            }
          }
        },
      failStateHandler = new IAnswer[Unit] {
          def answer {
            supervisor.scanPair(pair.asRef, None)      // Run a second scan when the first one fails
          }
        })
    
    scanListener.pairScanStateChanged(pair.asRef, PairScanState.SCANNING); expectLastCall().atLeastOnce()
    expectUpstreamScan().once()  // Succeed on second
    expectDownstreamScan().andAnswer(new IAnswer[Unit] {
      def answer() {
        // Notify that the second scan has started
        secondScanIsRunning.set(true)

        // Block the second scan until the first has reached a conclusion
        proxyDidGenerateException.get(waitForStragglerToFinishDelay)
      }
    }).once()
    expectDifferencesReplay(assertFlush = false)
    diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, "Scan completed"); expectLastCall
    scanListener.pairScanStateChanged(pair.asRef, PairScanState.UP_TO_DATE); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer {
        completionMonitor.synchronized { completionMonitor.notifyAll() }
      }
    }).once()

    replay(store, diffWriter, writer, versionPolicy, scanListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pairRef, None)

    assertTrue(proxyDidGenerateException.get(overallProcessWait).getOrElse(throw new Exception("Exception validation never reached in participant stub")))
    completionMonitor.synchronized { completionMonitor.wait(1000) }   // Wait for the scan to complete too

    verify(scanListener, diagnostics)
  }

  @Test
  def propagateChange = {
    val event = buildUpstreamEvent()
    val monitor = new Object

    expect(writer.flush()).atLeastOnce
    replay(writer)
    expect(versionPolicy.onChange(writer, event)).andAnswer(new IAnswer[Unit] {
      def answer = {
        monitor.synchronized {
          monitor.notifyAll
        }
      }
    })
    replay(store, diffWriter, versionPolicy)

    supervisor.startActor(pair)
    supervisor.propagateChangeEvent(event)

    // propagateChangeEvent is an aysnc call, so yield the test thread to allow the actor to invoke the policy
    monitor.synchronized {
      monitor.wait(1000)
    }

    verify(versionPolicy)
  }

  @Test
  def scheduledFlush {
    val mailbox = new LinkedBlockingQueue[Object]
    
    expect(writer.flush()).andStubAnswer(new IAnswer[Unit] {
      def answer = {
        mailbox.add(new Object)
        null
      }
    })
    replay(writer, store, diffWriter)

    supervisor.startActor(pair)

    // TODO Commented out because this is killing the vibe
    //mailbox.poll(5, TimeUnit.SECONDS) match { case null => fail("Flush not called"); case _ => () }
    //mailbox.poll(5, TimeUnit.SECONDS) match { case null => fail("Flush not called"); case _ => () }
  }

  def expectFailingScan(downstreamHandler:IAnswer[Unit], failStateHandler:IAnswer[Unit] = EasyMockScalaUtils.emptyAnswer) {
    expectWriterRollback()

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.SCANNING); expectLastCall.atLeastOnce()

    expectUpstreamScan().andThrow(new RuntimeException("Deliberate runtime exception, this should be handled")).once()
    expectDownstreamScan().andAnswer(downstreamHandler).once()

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.FAILED); expectLastCall[Unit].andAnswer(failStateHandler).once
    diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Upstream scan failed: Deliberate runtime exception, this should be handled"); expectLastCall.once
    diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Scan failed"); expectLastCall.once
  }

  def buildUpstreamEvent() = {
    val id = VersionID(DiffaPairRef(pairKey, domainName), "foo")
    val lastUpdate = new DateTime
    val vsn = "foobar"
    UpstreamPairChangeEvent(id, Seq(), lastUpdate, vsn)
  }

  def awaitFeedbackHandleCancellation(feedbackHandle:FeedbackHandle) {
    val endTime = System.currentTimeMillis() + 2000
    while (!feedbackHandle.isCancelled && endTime > System.currentTimeMillis()) {
      Thread.sleep(100)
    }
  }
}

object PairActorTest {
  private var pairIdCounter = 0

  def nextPairId = {
    pairIdCounter += 1
    "some-pairing-" + pairIdCounter
  }
}

/**
 * Simple logback appender that detects whether the PairActor has received a spurious message
 */
class SpuriousEventAppender extends AppenderBase[ILoggingEvent]{

  var hasSpuriousEvent = false
  var lastEvent:ILoggingEvent = null

  def append(event:ILoggingEvent) = {
    lastEvent = event
    if (event.getFormattedMessage.contains(AlertCodes.SPURIOUS_ACTOR_MESSAGE + ":")) {
      hasSpuriousEvent = true
    }
  }

}