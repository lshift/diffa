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
import net.jcip.annotations.ThreadSafe
import java.util.concurrent.ScheduledFuture
import net.lshift.diffa.kernel.differencing._
import net.lshift.diffa.kernel.events.{VersionID, PairChangeEvent}
import net.lshift.diffa.kernel.participants.{DownstreamParticipant, UpstreamParticipant}
import org.joda.time.DateTime
import net.lshift.diffa.kernel.util.AlertCodes._
import com.eaio.uuid.UUID
import akka.actor._
import collection.mutable.{SynchronizedQueue, Queue}
import concurrent.SyncVar
import net.lshift.diffa.kernel.diag.{DiagnosticLevel, DiagnosticsManager}
import net.lshift.diffa.kernel.util.StoreSynchronizationUtils._
import net.lshift.diffa.participant.scanning.{ScanResultEntry, ScanConstraint}
import org.slf4j.{LoggerFactory, Logger}
import net.lshift.diffa.kernel.config.{DomainConfigStore, Endpoint, DiffaPair}
import net.lshift.diffa.kernel.util.{EndpointSide, DownstreamEndpoint, UpstreamEndpoint}

/**
 * This actor serializes access to the underlying version policy from concurrent processes.
 */
case class PairActor(pair:DiffaPair,
                     us:Endpoint,
                     ds:Endpoint,
                     usp:UpstreamParticipant,
                     dsp:DownstreamParticipant,
                     policy:VersionPolicy,
                     store:VersionCorrelationStore,
                     differencesManager:DifferencesManager,
                     pairScanListener:PairScanListener,
                     diagnostics:DiagnosticsManager,
                     domainConfigStore:DomainConfigStore,
                     changeEventBusyTimeoutMillis: Long,
                     changeEventQuietTimeoutMillis: Long,
                     indexWriterCloseInterval: Int) extends Actor {

  val logger:Logger = LoggerFactory.getLogger(getClass)

  self.id_=(pair.identifier)

  private val pairRef = pair.asRef

  private var actionsRemainingUntilClose = indexWriterCloseInterval
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
    def logMessage(l:Logger, s:ActorState, code:Int) {
      val formattedCode = formatAlertCode(pairRef, code)
      l.debug("%s Received %sstream %s in %s state; scan id = %s".format(formattedCode, upOrDown, result, s, scanUuid))
    }
  }

  /**
   * This proxy is presented to clients that need access to a LimitedVersionCorrelationWriter.
   * It wraps the underlying writer instance and forwards all commands via asynchronous messages,
   * thus allowing parallel access to the writer.
   */
  private def createWriterProxy(scanUuid:UUID) = new LimitedVersionCorrelationWriter() {

    // The receive timeout in seconds
    val timeout = domainConfigStore.configOptionOrDefault(pairRef.domain, CorrelationWriterProxy.TIMEOUT_KEY, CorrelationWriterProxy.TIMEOUT_DEFAULT_VALUE)

    def clearUpstreamVersion(id: VersionID) = call( _.clearUpstreamVersion(id) )
    def clearDownstreamVersion(id: VersionID) = call( _.clearDownstreamVersion(id) )
    def storeDownstreamVersion(id: VersionID, attributes: Map[String, TypedAttribute], lastUpdated: DateTime, uvsn: String, dvsn: String)
      = call( _.storeDownstreamVersion(id, attributes, lastUpdated, uvsn, dvsn) )
    def storeUpstreamVersion(id: VersionID, attributes: Map[String, TypedAttribute], lastUpdated: DateTime, vsn: String)
      = call( _.storeUpstreamVersion(id, attributes, lastUpdated, vsn) )
    def call(command:(LimitedVersionCorrelationWriter => Correlation)) = {
      val message = VersionCorrelationWriterCommand(scanUuid, command)
      (self !!(message, 1000L * timeout.toInt)) match {
        case Some(CancelMessage)  => throw new ScanCancelledException(pairRef)
        case Some(result)         => result.asInstanceOf[Correlation]
        case None                 =>
          logger.error("%s Writer proxy timed out after %s seconds processing command: %s "
                       .format(formatAlertCode(pairRef, MESSAGE_RECEIVE_TIMEOUT), timeout, message), new Exception().fillInStackTrace())
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
    case i:InventoryMessage                => handleInventoryMessage(i)
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
          logger.debug(formatAlertCode(pairRef, CANCELLATION_REQUEST_RECEIVED)  + " Received cancellation request in non-scanning state, ignoring")
      }
      self.reply(true)
    }
    case a:ChildActorCompletionMessage     =>
      a.logMessage(logger, Ready, OUT_OF_ORDER_MESSAGE)
    case x                                 =>
      logger.error("%s Spurious message during ready loop: %s".format(formatAlertCode(pairRef, SPURIOUS_ACTOR_MESSAGE), x))
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
    case ScanMessage(scanView)              =>
      // ignore any scan requests whilst scanning
      diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, "Ignoring scan request received during current scan")
      logger.warn("%s Ignoring scan request; view = %s".format(formatAlertCode(pairRef, SCAN_REQUEST_IGNORED), scanView))
    case d: Deferrable                      => deferred.enqueue(d)
    case a: ChildActorCompletionMessage     if isOwnedByActiveScan(a) => {
      a.logMessage(logger, Scanning, CHILD_SCAN_COMPLETED)
      updateOutstandingScans(a)

      a.result match {
        case Failure => leaveScanState(PairScanState.FAILED)
        case Success => maybeLeaveScanningState
      }
    }
    case camsg:ChildActorScanMessage if isOwnedByOutstandingScan(camsg) =>
      updateOutstandingScans(camsg)     // Allow outstanding cancelled scans to clean themselves up nicely
    case x                                  =>
      logger.error("%s Spurious message during scanning loop: %s".format(formatAlertCode(pairRef, SPURIOUS_ACTOR_MESSAGE), x))
  }

  /**
   * Handles all messages that arrive whilst the actor is cancelling a scan
   */
  def handleCancellation() = {
    logger.info("%s Scan %s for pair %s was cancelled on request".format(formatAlertCode(pairRef, CANCELLATION_REQUEST_RECEIVED), activeScan.uuid, pair.identifier))
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
        replayCorrelationStore(differencesManager, writer, store, pairRef, us, ds, TriggeredByScan)
      } catch {
        case ex =>
          logger.error(formatAlertCode(pairRef, DIFFERENCE_REPLAY_FAILURE) + " failed to apply unmatched differences to the differences manager", ex)
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

    cleanupIndexFilesIfCorrelationStoreBackedByLucene

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
    if (timeSince(lastEventTime) > changeEventBusyTimeoutMillis) {
      writer.flush()
    }

    actionsRemainingUntilClose -= 1
    if (actionsRemainingUntilClose <= 0) {
      cleanupIndexFilesIfCorrelationStoreBackedByLucene
    }

    lastEventTime = System.currentTimeMillis()
  }

  def handleInventoryMessage(message:InventoryMessage) = {
    val ep = message.side match {
      case UpstreamEndpoint   => us
      case DownstreamEndpoint => ds
    }

    policy.processInventory(pair.asRef, ep, writer, message.side, message.constraints, message.entries)

    // always flush after an inventory
    writer.flush()

    actionsRemainingUntilClose -= 1
    if (actionsRemainingUntilClose <= 0) {
      cleanupIndexFilesIfCorrelationStoreBackedByLucene
    }

    lastEventTime = System.currentTimeMillis()

    // Play events from the correlation store into the differences manager
    replayCorrelationStore(differencesManager, writer, store, pairRef, us, ds, TriggeredByScan)
  }

  /**
   * Runs a simple replayUnmatchedDifferences for the pair.
   */
  def handleDifferenceMessage() = {
    try {
      writer.flush()
      replayCorrelationStore(differencesManager, writer, store, pairRef, us, ds, TriggeredByBoot)
    } catch {
      case ex => {
        diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Failed to Difference Pair: " + ex.getMessage)
        logger.error(formatAlertCode(pairRef, DIFFERENCING_FAILURE), ex)
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

      val infoMsg = scanView match {
        case Some(name) => "Commencing scan on %s view for pair %s".format(name, pairRef.key)
        case None =>       "Commencing non-filtered scan for pair %s".format(pairRef.key)
      }

      diagnostics.logPairEvent(DiagnosticLevel.INFO, pairRef, infoMsg)

      Actor.spawn {
        try {
          policy.scanUpstream(pairRef, us, scanView, writerProxy, usp, bufferingListener, currentFeedbackHandle)
          self ! ChildActorCompletionMessage(createdScan.uuid, Up, Success)
        }
        catch {
          case c:ScanCancelledException => {
            logger.warn("Upstream scan on pair %s was cancelled".format(pair.identifier))
            self ! ChildActorCompletionMessage(createdScan.uuid, Up, Cancellation)
          }
          case x:Exception              => handleScanError(self, createdScan.uuid, Up, x)
        }
      }

      Actor.spawn {
        try {
          policy.scanDownstream(pairRef, ds, scanView, writerProxy, usp, dsp, bufferingListener, currentFeedbackHandle)
          self ! ChildActorCompletionMessage(createdScan.uuid, Down, Success)
        }
        catch {
          case c:ScanCancelledException => {
            logger.warn("Downstream scan on pair %s was cancelled".format(pair.identifier))
            self ! ChildActorCompletionMessage(createdScan.uuid, Down, Cancellation)
          }
          case x:Exception              => handleScanError(self, createdScan.uuid, Down, x)
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
        logger.error(formatAlertCode(pairRef, SCAN_INITIALIZATION_FAILURE), x)
        diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "Failed to initiate scan for pair: " + x.getMessage)
        processBacklog(PairScanState.FAILED)
        false
      }
    }
  }

  private def cleanupIndexFilesIfCorrelationStoreBackedByLucene {
    logger.debug("Closing Version Correlation Store")
    actionsRemainingUntilClose = indexWriterCloseInterval
    store.openWriter.close
  }

  private def timeSince(pastTime: Long) = new Ordered[Long] {
    def compare(interval: Long) = if (System.currentTimeMillis() - pastTime > interval) 1 else -1
  }
  
  private def handleScanError(actor:ActorRef, scanId:UUID, upOrDown:UpOrDown, x:Exception) = {

    val (prefix, marker) = upOrDown match {
      case Up   => (formatAlertCode(pairRef, UPSTREAM_SCAN_FAILURE), "Upstream")
      case Down => (formatAlertCode(pairRef, DOWNSTREAM_SCAN_FAILURE), "Downstream")
    }

    val logTemplate = "%s " + marker + " scan failed; scan id = %s"

    x match {
      case f:ScanFailedException =>
        logger.error(logTemplate.format(prefix, scanId) + "; reason was: " + f.getMessage)
      case e:Exception =>
        logger.error(logTemplate.format(prefix, scanId), e)
    }

    diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairRef, "%s scan failed: %s".format(marker, x.getMessage))
    actor ! ChildActorCompletionMessage(scanId, upOrDown, Failure)
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
 * Configuration keys for the correlation writer proxy
 */
object CorrelationWriterProxy {
  val TIMEOUT_KEY = "correlation.writer.proxy.timeout"
  val TIMEOUT_DEFAULT_VALUE = "60" // 60 seconds
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
case class InventoryMessage(side:EndpointSide, constraints:Seq[ScanConstraint], entries:Seq[ScanResultEntry])

/**
 * This message indicates that this actor should cancel all current and pending scan operations.
 */
case object CancelMessage
/**
 * An internal command that indicates to the actor that the underlying writer should be flushed
 */
private case object FlushWriterMessage extends Deferrable

object PairActorConstants {
  final val MAX_CLEANUP_INTERVAL = minutesToMs(60) // 1 hour, expressed in milliseconds
  
  private def minutesToMs(minutes: Int) = minutes * 60 * 1000L
}
