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

import java.util.concurrent.TimeUnit.MILLISECONDS
import org.slf4j.{Logger, LoggerFactory}
import net.jcip.annotations.ThreadSafe
import java.util.concurrent.ScheduledFuture
import net.lshift.diffa.kernel.differencing._
import net.lshift.diffa.kernel.events.{VersionID, PairChangeEvent}
import net.lshift.diffa.kernel.participants.{DownstreamParticipant, UpstreamParticipant}
import org.joda.time.DateTime
import net.lshift.diffa.kernel.util.AlertCodes
import com.eaio.uuid.UUID
import akka.actor._
import collection.mutable.{SynchronizedQueue, Queue}
import concurrent.SyncVar
import net.lshift.diffa.kernel.diag.{DiagnosticLevel, DiagnosticsManager}
import net.lshift.diffa.kernel.config.{Pair => DiffaPair}
import net.lshift.diffa.kernel.util.StoreSynchronizationUtils._

/**
 * This actor serializes access to the underlying version policy from concurrent processes.
 */
case class PairActor(pair:DiffaPair,
                     us:UpstreamParticipant,
                     ds:DownstreamParticipant,
                     policy:VersionPolicy,
                     store:VersionCorrelationStore,
                     differencesManager:DifferencesManager,
                     pairScanListener:PairScanListener,
                     diagnostics:DiagnosticsManager,
                     changeEventBusyTimeoutMillis: Long,
                     changeEventQuietTimeoutMillis: Long) extends Actor {

  val logger:Logger = LoggerFactory.getLogger(getClass)

  self.id_=(pair.identifier)

  private val pairRef = pair.asRef

  private var lastEventTime: Long = 0
  private var scheduledFlushes: ScheduledFuture[_] = _

  /**
   * Flag that can be used to signal that scanning should be cancelled.
   */
  @ThreadSafe
  private var feedbackHandle:FeedbackHandle = null

  lazy val writer = store.openWriter()

  /**
   * A queue of deferred messages that arrived during a scanning state
   */
  private val deferred = new Queue[Deferrable]

  /**
   * Detail of the current scan-in-progress. Contains a UUID that lets us identify operations that belong to it.
   */
  private var activeScan:OutstandingScan = null

  /**
   * Keep track of scans that are still outstanding. This lets us know whether messages that arrive
   * are entirely spurious, or just jobs cleaning themselves up.
   */
  private val outstandingScans = collection.mutable.Map[UUID, OutstandingScan]()

  /**
   * This is the address of the client that requested the last cancellation
   */
  private var cancellationRequester:Channel[Any] = null

  /**
   * This allows tracing of spurious messages, but is only enabled in when the log level is set to TRACE
   */
  abstract class TraceableCommand(uuid:UUID) {
    var exception:Throwable = null
    if (logger.isTraceEnabled) {
      exception = new Exception().fillInStackTrace()
    }
  }

  /**
   * Describes a message coming from a child actor running as part of a scan
   */
  trait ChildActorScanMessage {
    def scanUuid:UUID   // The uuid of the scan that this message is coming from
  }

  private case class OutstandingScan(uuid:UUID) {
    var upstreamCompleted = false
    var downstreamCompleted = false

    def isCompleted = upstreamCompleted && downstreamCompleted
  }

  /**
   * This is the set of commands that the writer proxy understands
   */
  case class VersionCorrelationWriterCommand(scanUuid:UUID, invokeWriter:(LimitedVersionCorrelationWriter => Correlation))
      extends TraceableCommand(scanUuid)
      with ChildActorScanMessage {
    override def toString = scanUuid.toString
  }

  /**
   * Marker messages to let the actor know that a portion of the scan has successfully completed.
   */
  case class ChildActorCompletionMessage(scanUuid:UUID, upOrDown:UpOrDown, result:Result)
      extends ChildActorScanMessage {
    def logMessage(l:Logger, s:ActorState, code:String)
      = l.debug("%s: UUID[%s] -> Received %sstream %s in %s state".format(code, scanUuid, upOrDown, result, s))
  }

  /**
   * This proxy is presented to clients that need access to a LimitedVersionCorrelationWriter.
   * It wraps the underlying writer instance and forwards all commands via asynchronous messages,
   * thus allowing parallel access to the writer.
   */
  private def createWriterProxy(scanUuid:UUID) = new LimitedVersionCorrelationWriter() {
    // The receive timeout in seconds
    val timeout = 60

    def clearUpstreamVersion(id: VersionID) = call( _.clearUpstreamVersion(id) )
    def clearDownstreamVersion(id: VersionID) = call( _.clearUpstreamVersion(id) )
    def storeDownstreamVersion(id: VersionID, attributes: Map[String, TypedAttribute], lastUpdated: DateTime, uvsn: String, dvsn: String)
      = call( _.storeDownstreamVersion(id, attributes, lastUpdated, uvsn, dvsn) )
    def storeUpstreamVersion(id: VersionID, attributes: Map[String, TypedAttribute], lastUpdated: DateTime, vsn: String)
      = call( _.storeUpstreamVersion(id, attributes, lastUpdated, vsn) )
    def call(command:(LimitedVersionCorrelationWriter => Correlation)) = {
      val message = VersionCorrelationWriterCommand(scanUuid, command)
      (self !!(message, 1000L * timeout)) match {
        case Some(CancelMessage)  => throw new ScanCancelledException(pairRef)
        case Some(result)         => result.asInstanceOf[Correlation]
        case None                 =>
          logger.error("%s: Writer proxy timed out after %s seconds processing command: %s "
                       .format(AlertCodes.MESSAGE_RECEIVE_TIMEOUT, timeout, message), new Exception().fillInStackTrace())
          throw new RuntimeException("Writer proxy timeout")
      }
    }
  }

  case class MatchEvent(id: VersionID, command:(DifferencingListener => Unit))

  private val bufferingListener = new DifferencingListener {

    /**
     * Buffer up the match event because these won't be replayed by a subsequent replayUnmatchedDifferences operation.
     */
    def onMatch(id:VersionID, vsn:String, origin:MatchOrigin) = ()

    /**
     * Drop the mismatch, since we will be doing a full replayUnmatchedDifferences and the end of the scan process.
     */
    def onMismatch(id:VersionID, update:DateTime, uvsn:String, dvsn:String, origin:MatchOrigin, level:DifferenceFilterLevel) = ()
  }

  /**
   * Provides a simple handle to indicated that a scan should be cancelled.
   */
  private class ScanningFeedbackHandle extends FeedbackHandle {

    private val flag = new SyncVar[Boolean]
    flag.set(false)

    def isCancelled = flag.get
    def cancel() = flag.set(true)
  }

  override def preStart = {
    // schedule a recurring message to flush the writer
    scheduledFlushes = Scheduler.schedule(self, FlushWriterMessage, 0, changeEventQuietTimeoutMillis, MILLISECONDS)
  }

  override def postStop = scheduledFlushes.cancel(true)

  /**
   * Main receive loop of this actor. This is effectively a FSM.
   * When the scan state is entered, all non-writerProxy commands are buffered up
   * and will be re-delivered into this actor's mailbox when the scan state is exited.
   */
  def receive = {
    case ScanMessage(scanView) => {
      if (handleScanMessage(scanView)) {
        // Go into the scanning state
        become(receiveWhilstScanning)
      }
    }
    case c:ChangeMessage                   => handleChangeMessage(c)
    case DifferenceMessage                 => handleDifferenceMessage()
    case FlushWriterMessage                => writer.flush()
    case c:VersionCorrelationWriterCommand => {
      logger.trace("Received writer command (%s) in non-scanning state - sending cancellation".format(c), c.exception)
      self.reply(CancelMessage)
    }
    case camsg:ChildActorScanMessage if isOwnedByOutstandingScan(camsg) =>
      updateOutstandingScans(camsg)     // Allow outstanding cancelled scans to clean themselves up nicely
    case CancelMessage                     => {
      if (logger.isDebugEnabled) {
          logger.debug("%s : Received cancellation request in non-scanning state, ignoring".format(AlertCodes.CANCELLATION_REQUEST))
      }
      self.reply(true)
    }
    case a:ChildActorCompletionMessage     =>
      a.logMessage(logger, Ready, AlertCodes.OUT_OF_ORDER_MESSAGE)
    case x                                 =>
      logger.error("%s: Spurious message during ready loop: %s".format(AlertCodes.SPURIOUS_ACTOR_MESSAGE, x))
  }

  /**
   * Implementation of the receive loop whilst in a scanning state.
   * If a cancellation arrives whilst the actor is in this state, this is handled by the
   * handleCancellation function.
   */
  val receiveWhilstScanning : Actor.Receive  = {
    case FlushWriterMessage                 => // ignore flushes in this state - we may want to roll the index back
    case CancelMessage                      =>
      handleCancellation()
      self.reply(true)
    case c: VersionCorrelationWriterCommand =>
      if (isOwnedByActiveScan(c)) {
        self.reply(c.invokeWriter(writer))
      } else {
        logger.trace("Received writer command (%s) for different scan worker - sending cancellation".format(c), c.exception)
        self.reply(CancelMessage)
      }
    case ScanMessage                        => // ignore any scan requests whilst scanning
    case d: Deferrable                      => deferred.enqueue(d)
    case a: ChildActorCompletionMessage     if isOwnedByActiveScan(a) => {
      a.logMessage(logger, Scanning, AlertCodes.SCAN_OPERATION)
      updateOutstandingScans(a)

      a.result match {
        case Failure => leaveScanState(PairScanState.FAILED)
        case Success => maybeLeaveScanningState
      }
    }
    case camsg:ChildActorScanMessage if isOwnedByOutstandingScan(camsg) =>
      updateOutstandingScans(camsg)     // Allow outstanding cancelled scans to clean themselves up nicely
    case x                                  =>
      logger.error("%s: Spurious message during scanning loop: %s".format(AlertCodes.SPURIOUS_ACTOR_MESSAGE, x))
  }

  /**
   * Handles all messages that arrive whilst the actor is cancelling a scan
   */
  def handleCancellation() = {
    logger.info("%s: Scan %s for pair %s was cancelled on request".format(AlertCodes.CANCELLATION_REQUEST, activeScan.uuid, pair.identifier))
    feedbackHandle.cancel()

    // Leave the scanning state as cancelled
    leaveScanState(PairScanState.CANCELLED)
  }

  /**
   * Potentially exit the scanning state and notify interested parties
   */
  def maybeLeaveScanningState = {
    if (activeScan.isCompleted) {
      logger.trace("Finished scan %s".format(activeScan.uuid))

      // Notify all interested parties of all of the outstanding mismatches
      writer.flush()

      try {
        diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, "Calculating differences")
        replayCorrelationStore(differencesManager, writer, store, pair, TriggeredByScan)
      } catch {
        case ex =>
          logger.error("Failed to apply unmatched differences to the differences manager", ex)
      }

      // Re-queue all buffered commands
      leaveScanState(PairScanState.UP_TO_DATE)
    }
  }

  /**
   * Ensures that the scan state is left cleanly
   */
  def leaveScanState(state:PairScanState) = {

    if (state == PairScanState.FAILED || state == PairScanState.CANCELLED) {
      feedbackHandle.cancel()     // In the scenario where we failed, we want to make sure any dangling processes cancel
      writer.rollback()
    }

    state match {
      case PairScanState.FAILED => diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Scan failed")
      case PairScanState.CANCELLED => diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, "Scan cancelled")
      case PairScanState.UP_TO_DATE => diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, "Scan completed")
      case _                        => // Ignore - not a state that we'll see
    }

    // Remove the record of the active scan
    activeScan = null

    // Re-queue all buffered commands
    processBacklog(state)

    // Make sure that this flag is zeroed out
    feedbackHandle = null

    // Make sure there is no dangling back address
    cancellationRequester = null

    // Inform the diagnostics manager that we've completed a major operation, so it should checkpoint the explanation
    // data.
    diagnostics.checkpointExplanations(pair.asRef)

    // Leave the scan state
    unbecome()
  }

  /**
   * Resets the state of the actor and processes any pending messages that may have arrived during a scan phase.
   */
  def processBacklog(state:PairScanState) = {
    if (pairScanListener != null) {
      pairScanListener.pairScanStateChanged(pair.asRef, state)
    }
    deferred.dequeueAll(d => {self ! d; true})
  }

  /**
   * Events out normal changes.
   */
  def handleChangeMessage(message:ChangeMessage) = {
    policy.onChange(writer, message.event)
    // if no events have arrived within the timeout period, flush and clear the buffer
    if (lastEventTime < (System.currentTimeMillis() - changeEventBusyTimeoutMillis)) {
      writer.flush()
    }
    lastEventTime = System.currentTimeMillis()
  }

  /**
   * Runs a simple replayUnmatchedDifferences for the pair.
   */
  def handleDifferenceMessage() = {
    try {
      writer.flush()
      replayCorrelationStore(differencesManager, writer, store, pair, TriggeredByBoot)
    } catch {
      case ex => {
        diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Failed to Difference Pair: " + ex.getMessage)
        logger.error("Failed to difference pair " + pairRef.identifier, ex)
      }
    }
  }

  /**
   * Implements the top half of the request to scan the participants for digests.
   * This actor will still be in the scan state after this callback has returned.
   */
  def handleScanMessage(scanView:Option[String]) : Boolean = {
    val createdScan = OutstandingScan(new UUID)

    // allocate a writer proxy
    val writerProxy = createWriterProxy(createdScan.uuid)

    pairScanListener.pairScanStateChanged(pair.asRef, PairScanState.SCANNING)

    try {
      writer.flush()

      // Allocate a feedback handle, and capture it into a local variable. This prevents us having problems
      // later if one participant fails _really_ fast (ie, before the other has even made the scan* call).
      feedbackHandle = new ScanningFeedbackHandle
      val currentFeedbackHandle = feedbackHandle

      Actor.spawn {
        try {
          policy.scanUpstream(pair, scanView, writerProxy, us, bufferingListener, currentFeedbackHandle)
          self ! ChildActorCompletionMessage(createdScan.uuid, Up, Success)
        }
        catch {
          case c:ScanCancelledException => {
            logger.warn("Upstream scan on pair %s was cancelled".format(pair.identifier))
            self ! ChildActorCompletionMessage(createdScan.uuid, Up, Cancellation)
          }
          case e:Exception => {
            logger.error("Upstream scan failed: " + pairRef.identifier, e)
            diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Upstream scan failed: " + e.getMessage)
            self ! ChildActorCompletionMessage(createdScan.uuid, Up, Failure)
          }
        }
      }

      Actor.spawn {
        try {
          policy.scanDownstream(pair, scanView, writerProxy, us, ds, bufferingListener, currentFeedbackHandle)
          self ! ChildActorCompletionMessage(createdScan.uuid, Down, Success)
        }
        catch {
          case c:ScanCancelledException => {
            logger.warn("Downstream scan on pair %s was cancelled".format(pair.identifier))
            self ! ChildActorCompletionMessage(createdScan.uuid, Down, Cancellation)
          }
          case e:Exception => {
            logger.error("Downstream scan failed: " + pairRef.identifier, e)
            diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Downstream scan failed: " + e.getMessage)
            self ! ChildActorCompletionMessage(createdScan.uuid, Down, Failure)
          }
        }
      }

      // Mark the initiated scan as active and outstanding. We don't record this until the end because something
      // might go wrong during the setup, and we'd then need to remove it. Only the main actor looks at activeScan,
      // so even though the child actors are running by now, it is safe not to have activeScan set.
      activeScan = createdScan
      outstandingScans(activeScan.uuid) = activeScan

      true

    } catch {
      case x: Exception => {
        logger.error("Failed to initiate scan for pair: " + pairRef.identifier, x)
        diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Failed to initiate scan for pair: " + x.getMessage)
        processBacklog(PairScanState.FAILED)
        false
      }
    }
  }

  private def isOwnedByActiveScan(msg:ChildActorScanMessage) = activeScan != null && activeScan.uuid == msg.scanUuid
  private def isOwnedByOutstandingScan(msg:ChildActorScanMessage) = outstandingScans.contains(msg.scanUuid)
  private def updateOutstandingScans(msg:ChildActorScanMessage) = {
    msg match {
      case completion:ChildActorCompletionMessage =>
        outstandingScans.get(completion.scanUuid) match {
          case Some(scan) =>
            // Update the completion status flags, and remove it if it has reached a totally completed state
            completion.upOrDown match {
              case Up   => scan.upstreamCompleted = true
              case Down => scan.downstreamCompleted = true
            }
            if (scan.isCompleted) {
              outstandingScans.remove(scan.uuid)
            }
          case None       => // Doesn't match an outstanding scan. Ignore.
        }
      case _ => // Doesn't affect the outstanding scan set. Ignore.
    }


  }
}

/**
 * Denotes the current state of the actor
 */
abstract class ActorState
case object Ready extends ActorState
case object Scanning extends ActorState
case object Cancelling extends ActorState

/**
 * Enum to signify whether the messsage was in realtion to the up- or downstream
 */
abstract class UpOrDown
case object Up extends UpOrDown
case object Down extends UpOrDown

/**
 * Indicates the result of a scan operation
 */
abstract class Result
case object Success extends Result
case object Failure extends Result
case object Cancellation extends Result

/**
 * This is the group of all commands that should be buffered when the actor is the scan state.
 */
abstract class Deferrable
case class ChangeMessage(event: PairChangeEvent) extends Deferrable
case object DifferenceMessage extends Deferrable
case class ScanMessage(scanView:Option[String])

/**
 * This message indicates that this actor should cancel all current and pending scan operations.
 */
case object CancelMessage
/**
 * An internal command that indicates to the actor that the underlying writer should be flushed
 */
private case object FlushWriterMessage extends Deferrable
