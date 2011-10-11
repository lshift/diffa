package net.lshift.diffa.kernel.diag

import org.joda.time.DateTime
import collection.mutable.{ListBuffer, HashMap}
import net.lshift.diffa.kernel.differencing.{PairScanState, PairScanListener}
import net.lshift.diffa.kernel.config.{DiffaPairRef, DomainConfigStore, Pair => DiffaPair}
import net.lshift.diffa.kernel.lifecycle.{NotificationCentre, AgentLifecycleAware}
import org.slf4j.LoggerFactory

/**
 * Local in-memory implementation of the DiagnosticsManager.
 *
 *   TODO: Release resources when pair is removed
 */
class LocalDiagnosticsManager(domainConfigStore:DomainConfigStore)
    extends DiagnosticsManager
    with PairScanListener
    with AgentLifecycleAware {
  private val pairs = HashMap[DiffaPairRef, PairDiagnostics]()
  private val maxEventsPerPair = 100

  private val log = LoggerFactory.getLogger(getClass)

  def logPairEvent(level: DiagnosticLevel, pair: DiffaPairRef, msg: String) {
    val pairDiag = pairs.synchronized { pairs.getOrElseUpdate(pair, new PairDiagnostics) }
    pairDiag.logPairEvent(PairEvent(new DateTime(), level, msg))
  }

  def logPairExplanation(pair: DiffaPairRef, msg: String) {
    log.info("Explain: %s: %s".format(pair, msg))
  }

  def attachPairExplanationObject(pair: DiffaPairRef, objName: String, content: String) {
  }

  def queryEvents(pair:DiffaPairRef, maxEvents: Int) = {
    pairs.synchronized { pairs.get(pair) } match {
      case None           => Seq()
      case Some(pairDiag) => pairDiag.queryEvents(maxEvents)
    }
  }

  def retrievePairScanStatesForDomain(domain:String) = {
    val domainPairs = domainConfigStore.listPairs(domain)

    pairs.synchronized {
      domainPairs.map(p => pairs.get(DiffaPairRef(p.key, domain)) match {
        case None           => p.key -> PairScanState.UNKNOWN
        case Some(pairDiag) => p.key -> pairDiag.scanScate
      }).toMap
    }
  }

  def pairScanStateChanged(pair: DiffaPairRef, scanState: PairScanState) = pairs.synchronized {
    val pairDiag = pairs.synchronized { pairs.getOrElseUpdate(pair, new PairDiagnostics) }
    pairDiag.scanScate = scanState
  }

  /**
   * When pairs are deleted, we stop tracking their status in the pair scan map.
   */
  def onDeletePair(pair:DiffaPairRef) {
    pairs.synchronized { pairs.remove(pair) }
  }

  
  //
  // Lifecycle Management
  //

  override def onAgentInstantiationCompleted(nc: NotificationCentre) {
    nc.registerForPairScanEvents(this)
  }

  private class PairDiagnostics {
    private val log = ListBuffer[PairEvent]()
    var scanScate:PairScanState = PairScanState.UNKNOWN

    def logPairEvent(evt:PairEvent) {
      log.synchronized {
        log += evt

        val drop = log.length - maxEventsPerPair
        if (drop > 0)
          log.remove(0, drop)
      }
    }

    def queryEvents(maxEvents:Int):Seq[PairEvent] = {
      log.synchronized {
        val startIdx = log.length - maxEvents
        if (startIdx < 0) {
          log.toSeq
        } else {
          log.slice(startIdx, log.length).toSeq
        }
      }
    }
  }
}