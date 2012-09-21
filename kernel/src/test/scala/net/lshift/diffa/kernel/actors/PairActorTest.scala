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
import net.lshift.diffa.kernel.config.{Space, DomainConfigStore, PairRef, Endpoint}
import java.util.concurrent.LinkedBlockingQueue
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.util._
import net.lshift.diffa.participant.scanning._
import akka.dispatch.{ExecutionContext, Future}
import org.junit.runner.RunWith
import org.junit.experimental.theories.{DataPoint, Theories, Theory}
import org.junit.{Test, After, Before}
import net.lshift.diffa.kernel.frontend.{DomainPairDef, FrontendConversions}
import net.lshift.diffa.kernel.scanning.{ScanStatement, ScanActivityStore}

@RunWith(classOf[Theories])
class PairActorTest {
  import PairActorTest._

  val spaceId = System.currentTimeMillis()

  val space = Space(id = spaceId)

  val pairKey = nextPairId
  val policyName = ""
  val upstream = Endpoint(name = "up", scanUrl = "up")
  val downstream = Endpoint(name = "down", scanUrl = "down")

  val pair = new DomainPairDef()
  pair.key = pairKey
  pair.space = spaceId
  pair.versionPolicyName = policyName
  pair.upstreamName = upstream.name
  pair.downstreamName = downstream.name

  val pairRef = pair.asRef

  val us = createStrictMock("upstreamParticipant", classOf[UpstreamParticipant])
  val ds = createStrictMock("downstreamParticipant", classOf[DownstreamParticipant])
  val diagnostics = createMock("diagnosticsManager", classOf[DiagnosticsManager])
  diagnostics.checkpointExplanations(EasyMock.anyObject[Option[Long]](), EasyMock.eq(pairRef)); expectLastCall().asStub()

  val participantFactory = org.easymock.classextension.EasyMock.createStrictMock("participantFactory", classOf[ParticipantFactory])
  expect(participantFactory.createUpstreamParticipant(upstream, pairRef)).andReturn(us)
  expect(participantFactory.createDownstreamParticipant(downstream, pairRef)).andReturn(ds)
  org.easymock.classextension.EasyMock.replay(participantFactory)

  val versionPolicyManager = org.easymock.classextension.EasyMock.createStrictMock("versionPolicyManager", classOf[VersionPolicyManager])

  val versionPolicy = createMock("versionPolicy", classOf[VersionPolicy])
  checkOrder(versionPolicy, false)

  // TODO: We use at least once here, because seemingly, we get called on the actor create code path.
  // And because in Akka2; creating an actor starts it, we end up running this code (again?) in the @After block,
  expect(versionPolicyManager.lookupPolicy(policyName)).andReturn(Some(versionPolicy)).atLeastOnce()
  org.easymock.classextension.EasyMock.replay(versionPolicyManager)

  val systemConfigStore = createStrictMock("systemConfigStore", classOf[SystemConfigStore])
  val domainConfigStore = createStrictMock("domainConfigStore", classOf[DomainConfigStore])

  expect(domainConfigStore.getPairDef(spaceId, pairKey)).andStubReturn(pair)
  expect(domainConfigStore.getPairDef(PairRef(pairKey, spaceId))).andStubReturn(pair)
  expect(systemConfigStore.listSpaces).andStubReturn(Seq(space))
  expect(systemConfigStore.listPairs).andReturn(Seq())      // Don't return our pair in the list, since we don't want it started immediately


  expect(domainConfigStore.listPairs(spaceId)).andStubReturn(Seq(pair))
  expect(domainConfigStore.getEndpoint(spaceId, upstream.name)).andStubReturn(upstream)
  expect(domainConfigStore.getEndpoint(spaceId, downstream.name)).andStubReturn(downstream)

  expect(domainConfigStore.configOptionOrDefault(spaceId,
                                                 CorrelationWriterProxy.TIMEOUT_KEY,
                                                 CorrelationWriterProxy.TIMEOUT_DEFAULT_VALUE)).
    andStubReturn(CorrelationWriterProxy.TIMEOUT_DEFAULT_VALUE)

  replay(systemConfigStore)
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
  expect(differencesManager.createDifferenceWriter(spaceId, pairKey, overwrite = true)).andStubReturn(diffWriter)
  expect(differencesManager.lastRecordedVersion(pairRef)).andStubReturn(None)
  replay(differencesManager)

  val actorSystem = ActorSystem("PairActorSTest%#x".format(hashCode()))

  val scanActivityStore = createStrictMock(classOf[ScanActivityStore])
  expect(scanActivityStore.createOrUpdateStatement(EasyMock.isA(classOf[ScanStatement]))); expectLastCall().atLeastOnce()
  replay(scanActivityStore)

  val supervisor = new PairActorSupervisor(versionPolicyManager, systemConfigStore, domainConfigStore, differencesManager, scanListener, participantFactory, stores, diagnostics, scanActivityStore, 50, 100, closeInterval, actorSystem)
  supervisor.onAgentAssemblyCompleted
  supervisor.onAgentConfigurationActivated

  @After
  def stop = {
    supervisor.stopActor(pair.asRef)
    actorSystem.shutdown()
  }

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
    expect(versionPolicy.scanUpstream(EasyMock.anyObject[Long](), EasyMock.eq(pairRef), EasyMock.eq(upstream), EasyMock.eq(None), EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
                                      EasyMock.eq(us), EasyMock.isA(classOf[DifferencingListener]),
                                      EasyMock.isA(classOf[FeedbackHandle])))
  }
  def expectDownstreamScan() = {
    expect(versionPolicy.scanDownstream(EasyMock.anyObject[Long](), EasyMock.eq(pairRef), EasyMock.eq(downstream), EasyMock.eq(None), EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
                                        EasyMock.eq(us), EasyMock.eq(ds), EasyMock.isA(classOf[DifferencingListener]),
                                        EasyMock.isA(classOf[FeedbackHandle])))
  }

  def expectScanCommencement(times:Int) = {
    expect(diagnostics.logPairEvent(EasyMock.anyObject[Option[Long]](),
                                    EasyMock.eq(pairRef),
                                    EasyMock.eq(DiagnosticLevel.INFO),
                                    EasyMock.contains("Commencing"))).times(times)
  }

  def expectScans() = {
    expectUpstreamScan()
    expectDownstreamScan()
  }

  def expectDifferencesReplay(assertFlush:Boolean = true, writerCloseMonitor:Object = null) = {
    if (assertFlush) writer.flush(); expectLastCall.atLeastOnce()
    diagnostics.logPairEvent(EasyMock.anyObject[Option[Long]](),
                             EasyMock.eq(pairRef),
                             EasyMock.eq(DiagnosticLevel.INFO),
                             EasyMock.eq("Calculating differences")); expectLastCall
    expect(store.unmatchedVersions(anyObject[Seq[ScanConstraint]], anyObject[Seq[ScanConstraint]], EasyMock.eq[Option[Long]](None))).andReturn(Seq())
    expect(store.tombstoneVersions(None)).andReturn(Seq())
    diffWriter.evictTombstones(Seq()); expectLastCall
    writer.clearTombstones(); expectLastCall
    diffWriter.close()

    if (writerCloseMonitor != null) {
      expectLastCall().andAnswer(new IAnswer[Unit] {
        def answer = {
          writerCloseMonitor.synchronized {
            writerCloseMonitor.notifyAll
          }
        }
      })
    } else {
      expectLastCall()
    }

    // Expect the Lucene writer to be closed
    writer.close(); expectLastCall.asStub
  }

  def expectWriterRollback() {
    expect(writer.flush()).atLeastOnce
    expect(writer.rollback())
  }

  def executeAndValidateScan(expectUpstream:Boolean = true, expectDownstream:Boolean = true) = {
    val monitor = new Object

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.SCANNING); expectLastCall.atLeastOnce()

    if (expectUpstream) expectUpstreamScan()
    if (expectDownstream) expectDownstreamScan()

    expectDifferencesReplay()

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.UP_TO_DATE); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer = { monitor.synchronized { monitor.notifyAll } }
    })
    diagnostics.logPairEvent(EasyMock.anyObject[Option[Long]](),
                             EasyMock.eq(pairRef),
                             EasyMock.eq(DiagnosticLevel.INFO),
                             EasyMock.eq("Scan completed")); expectLastCall
    //diagnostics.logPairEvent(Some(System.currentTimeMillis()), pairRef, DiagnosticLevel.INFO, "Scan completed"); expectLastCall
    val numberOfScans = 1
    expectScanCommencement(numberOfScans)

    replay(writer, store, versionPolicy, scanListener, diagnostics)

    supervisor.startActor(pair.asRef)
    supervisor.scanPair(pair.asRef, None, None)
    monitor.synchronized {
      monitor.wait(1000)
    }
    verify(writer, versionPolicy, scanListener, diagnostics)
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

    supervisor.startActor(pair.asRef)
    supervisor.difference(pair.asRef)

    monitor.synchronized {
      monitor.wait(1000)
    }

    verify(versionPolicy)
  }

  @Test
  def runScan = {
    executeAndValidateScan(expectUpstream = true, expectDownstream = true)
  }

  @Test
  def runScanWithUnscannableUpstream = {
    upstream.scanUrl = null
    executeAndValidateScan(expectUpstream = false, expectDownstream = true)
  }

  @Test
  def runScanWithUnscannableDownstream = {
    downstream.scanUrl = null
    executeAndValidateScan(expectUpstream = true, expectDownstream = false)
  }

  @Test
  def scanRequestsReceivedInScanningStateShouldBeIgnored = {

    val timeToWait = 1000
    val scanMonitor = new Object
    val diagnosticsMonitor = new Object

    expect(versionPolicy.scanUpstream(
               EasyMock.anyObject[Long](),
               EasyMock.eq(pairRef),
               EasyMock.eq(upstream),
               EasyMock.eq(None),
               EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
               EasyMock.eq(us),
               EasyMock.isA(classOf[DifferencingListener]),
               EasyMock.isA(classOf[FeedbackHandle]))
        ).andAnswer(new IAnswer[Unit] {
          def answer = {
            scanMonitor.synchronized { scanMonitor.notifyAll }
            // Make sure that this scan blocks long enough to allow a second scan to get ignored
            Thread.sleep(timeToWait * 5)
          }
        })

    writer.flush(); expectLastCall.asStub()
    writer.close
    expectLastCall.asStub

    expect(diagnostics.logPairEvent(EasyMock.anyObject[Option[Long]](),
                                    EasyMock.eq(pairRef),
                                    EasyMock.eq(DiagnosticLevel.INFO),
                                    EasyMock.eq("Ignoring scan request received during current scan"))
          ).andAnswer(new IAnswer[Unit] {
            def answer = {
              diagnosticsMonitor.synchronized {
                diagnosticsMonitor.notifyAll
              }
            }
          })

    val numberOfScans = 1
    expectScanCommencement(numberOfScans)

    replay(writer, store, versionPolicy, diagnostics)

    supervisor.startActor(pair.asRef)
    supervisor.scanPair(pairRef, None, None)

    scanMonitor.synchronized {
      scanMonitor.wait(timeToWait)
    }

    // Request a second scan that should get ignored by the actor that should be busy handling the first scan

    supervisor.scanPair(pairRef, None, None)

    verify(writer, store, versionPolicy)

    // Since the notification via the diagnostics facility will be invoked asynchronously,
    // we need a way to deterministically wait for the notification to come through

    diagnosticsMonitor.synchronized {
      diagnosticsMonitor.wait(timeToWait)
    }

    verify(diagnostics)
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

    expect(versionPolicy.onChange(writer, event))
    .andAnswer(new IAnswer[Unit] {
      def answer = {
        eventMonitor.synchronized {
          eventMonitor.notifyAll
        }
      }
    })

    expect(versionPolicy.scanUpstream(
           EasyMock.anyObject[Long](),
           EasyMock.eq(pairRef),
           EasyMock.eq(upstream),
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
    expect(versionPolicy.scanDownstream(
           EasyMock.anyObject[Long](),
           EasyMock.eq(pairRef),
           EasyMock.eq(downstream),
           EasyMock.eq(None),
           EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
           EasyMock.eq(us), EasyMock.eq(ds),
           EasyMock.isA(classOf[DifferencingListener]),
           EasyMock.isA(classOf[FeedbackHandle])))

    expectDifferencesReplay()
    expect(writer.close).anyTimes

    replay(writer, store, diffWriter, versionPolicy, scanListener)

    supervisor.startActor(pair.asRef)
    supervisor.scanPair(pair.asRef, None, None)

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

    supervisor.startActor(pair.asRef)
    assertTrue(supervisor.cancelScans(pair.asRef))
  }


  @Test
  def shouldHandleCancellationWhilstScanning = {

    val cancelMonitor = new Object
    val responseMonitor = new Object

    val timeToWait = 2000L
    implicit val system =  actorSystem
    implicit val ec = ExecutionContext.defaultExecutionContext

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.SCANNING); expectLastCall     // Expect once when the pair actor starts the call
    scanListener.pairScanStateChanged(pair.asRef, PairScanState.SCANNING); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer = {
        Future {
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
    diagnostics.logPairEvent(EasyMock.anyObject[Option[Long]](), EasyMock.eq(pairRef), EasyMock.eq(DiagnosticLevel.INFO), EasyMock.eq("Scan cancelled")); expectLastCall

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
    expect(writer.close).times(1)

    val numberOfScans = 1
    expectScanCommencement(numberOfScans)

    replay(store, writer, diffWriter, versionPolicy, scanListener, diagnostics)

    supervisor.startActor(pair.asRef)
    supervisor.scanPair(pair.asRef, None, None)

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

    expectFailingUpstreamScanAndUseProvidedDownstreamHandler(
      downstreamHandler = EasyMockScalaUtils.emptyAnswer,
      failStateHandler = new IAnswer[Unit] {
          def answer { monitor.synchronized { monitor.notifyAll } }
        })

    val numberOfScans = 1
    expectScanCommencement(numberOfScans)
    expect(writer.close).anyTimes

    replay(store, writer, diffWriter, versionPolicy, scanListener, diagnostics)

    supervisor.startActor(pair.asRef)
    supervisor.scanPair(pair.asRef, None, None)
    monitor.synchronized {
      monitor.wait(1000)
    }
    verify(versionPolicy, scanListener, diagnostics)
  }

  @Test
  def shouldCancelFeedbackHandleWhenAParticipantFails = {
    val wasMarkedAsCancelled = new SyncVar[Boolean]

    expectFailingUpstreamScanAndUseProvidedDownstreamHandler(downstreamHandler = new IAnswer[Unit] {
      def answer() {
        val feedbackHandle = EasyMock.getCurrentArguments()(8).asInstanceOf[FeedbackHandle]
        println("Marking as cancelled for %s".format(feedbackHandle))
        awaitFeedbackHandleCancellation(feedbackHandle)
        println("Feedbackhandle %s cancelled? %s".format(feedbackHandle, feedbackHandle.isCancelled))
        wasMarkedAsCancelled.set(feedbackHandle.isCancelled)
      }
    })

    val numberOfScans = 1
    expectScanCommencement(numberOfScans)
    writer.close

    replay(writer, store, diffWriter, versionPolicy, scanListener, diagnostics)

    supervisor.startActor(pair.asRef)
    supervisor.scanPair(pair.asRef, None, None)
    assertTrue(wasMarkedAsCancelled.get(4000).getOrElse(throw new Exception("Feedback handle check never reached in participant stub")))
    verify(versionPolicy, scanListener, diagnostics)
  }

  @Test
  def propagateChange = {
    val event = buildUpstreamEvent()
    val monitor = new Object

    expect(writer.flush()).atLeastOnce
    writer.close
    expect(versionPolicy.onChange(writer, event)).andAnswer(new IAnswer[Unit] {
      def answer = {
        monitor.synchronized {
          monitor.notifyAll
        }
      }
    })
    replay(store, diffWriter, versionPolicy, writer)

    supervisor.startActor(pair.asRef)
    supervisor.propagateChangeEvent(event)

    // propagateChangeEvent is an aysnc call, so yield the test thread to allow the actor to invoke the policy
    monitor.synchronized {
      monitor.wait(1000)
    }

    verify(versionPolicy)
  }

  @Test
  def propagateUpstreamInventory() { propagateInventory(upstream, UpstreamEndpoint) }

  @Test
  def propagateDownstreamInventory() { propagateInventory(downstream, DownstreamEndpoint) }

  def propagateInventory(endpoint:Endpoint, side:EndpointSide) = {
    val monitor = new Object
    val aggregations = Seq[ScanAggregation](new IntegerAggregation("foo2", 5))
    val constraints = Seq(new SetConstraint("foo", Set("a", "b")))
    val entries = Seq(ScanResultEntry.forEntity("id1", "v1", new DateTime, Map("foo" -> "a")))

    expect(versionPolicy.processInventory(pairRef, endpoint, writer, side, constraints, aggregations, entries)).andReturn(Seq())
    expectDifferencesReplay(assertFlush = true, writerCloseMonitor = monitor)

    replay(store, diffWriter, versionPolicy, writer)

    supervisor.startActor(pair.asRef)
    supervisor.submitInventory(pairRef, side, constraints, aggregations, entries)

    // submitInventory is an aysnc call, so yield the test thread to allow the actor to invoke the policy
    monitor.synchronized {
      monitor.wait(1000)
    }

    verify(versionPolicy, writer, diffWriter)
  }

  @Theory
  def versionCorrelationWriterShouldCloseAfterConfiguredCloseInterval(scenario: Scenario) {
    val event = buildUpstreamEvent()
    val monitor = new Object

    reset(writer)
    expect(writer.flush).anyTimes
    expect(versionPolicy.onChange(writer, event)).times(scenario.actionCount).andAnswer(new IAnswer[Unit] {
      def answer = {
        monitor.synchronized {
          monitor.notifyAll
        }
      }
    })

    // Pre-declared Then
    if (scenario.expectedCloses > 0) {
      expect(writer.close).times(scenario.expectedCloses)
    }

    replay(writer, store, diffWriter, versionPolicy)

    // When
    supervisor.startActor(pair.asRef)
    (1 to scenario.actionCount) foreach { n =>
      supervisor.propagateChangeEvent(event)
    }

    monitor.synchronized {
      monitor.wait(1000)
    }

    // Then
    verify(writer)
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

    supervisor.startActor(pair.asRef)

    // TODO Commented out because this is killing the vibe
    //mailbox.poll(5, TimeUnit.SECONDS) match { case null => fail("Flush not called"); case _ => () }
    //mailbox.poll(5, TimeUnit.SECONDS) match { case null => fail("Flush not called"); case _ => () }
  }

  def expectFailingUpstreamScanAndUseProvidedDownstreamHandler(downstreamHandler:IAnswer[Unit], failStateHandler:IAnswer[Unit] = EasyMockScalaUtils.emptyAnswer) {
    expectWriterRollback()

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.SCANNING); expectLastCall.atLeastOnce()

    expectUpstreamScan().andThrow(new RuntimeException("Deliberate runtime exception, this should be handled")).once()
    expectDownstreamScan().andAnswer(downstreamHandler).once()

    scanListener.pairScanStateChanged(pair.asRef, PairScanState.FAILED); expectLastCall[Unit].andAnswer(failStateHandler).once
    diagnostics.logPairEvent(EasyMock.anyObject[Option[Long]](), EasyMock.eq(pairRef), EasyMock.eq(DiagnosticLevel.ERROR), EasyMock.eq("Upstream scan failed: Deliberate runtime exception, this should be handled")); expectLastCall.once
    diagnostics.logPairEvent(EasyMock.anyObject[Option[Long]](), EasyMock.eq(pairRef), EasyMock.eq(DiagnosticLevel.ERROR), EasyMock.eq("Scan failed")); expectLastCall.once
  }

  def buildUpstreamEvent() = {
    val id = VersionID(PairRef(pairKey, spaceId), "foo")
    val lastUpdate = new DateTime
    val vsn = "foobar"
    UpstreamPairChangeEvent(id, Map(), lastUpdate, vsn)
  }

  def awaitFeedbackHandleCancellation(feedbackHandle:FeedbackHandle) {
    val endTime = System.currentTimeMillis() + 2000
    while (!feedbackHandle.isCancelled && endTime > System.currentTimeMillis()) {
      Thread.sleep(100)
    }
  }
}

case class Scenario(actionCount: Int, expectedCloses: Int)

object PairActorTest {
  private var pairIdCounter = 0
  private[PairActorTest] val closeInterval = 3

  def nextPairId = {
    pairIdCounter += 1
    "some-pairing-" + pairIdCounter
  }
  
  @DataPoint def doNotCloseWriterBeforeIntervalReached = Scenario(actionCount = closeInterval - 1, expectedCloses = 0)
  @DataPoint def closeWriterOnce = Scenario(actionCount = closeInterval, expectedCloses = 1)
  @DataPoint def closeWriterOnceOnly = Scenario(actionCount = closeInterval + 1, expectedCloses = 1)
  @DataPoint def closeWriterTwice = Scenario(actionCount = closeInterval * 2, expectedCloses = 2)
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
