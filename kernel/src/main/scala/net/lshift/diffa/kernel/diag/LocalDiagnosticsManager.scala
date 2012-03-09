package net.lshift.diffa.kernel.diag

import org.joda.time.DateTime
import collection.mutable.{ListBuffer, HashMap}
import net.lshift.diffa.kernel.differencing.{PairScanState, PairScanListener}
import net.lshift.diffa.kernel.lifecycle.{NotificationCentre, AgentLifecycleAware}
import org.slf4j.LoggerFactory
import java.io._
import org.joda.time.format.ISODateTimeFormat
import java.util.zip.{ZipEntry, ZipOutputStream}
import org.apache.commons.io.IOUtils
import net.lshift.diffa.kernel.config.{ConfigOption, DiffaPairRef, DomainConfigStore}
import net.lshift.diffa.kernel.config.system.SystemConfigStore

/**
 * Local in-memory implementation of the DiagnosticsManager.
 *
 *   TODO: Release resources when pair is removed
 */
class LocalDiagnosticsManager(systemConfigStore: SystemConfigStore, domainConfigStore:DomainConfigStore, explainRootDir:String)
    extends DiagnosticsManager
    with PairScanListener
    with AgentLifecycleAware {
  private val pairs = HashMap[DiffaPairRef, PairDiagnostics]()
  private val defaultMaxEventsPerPair = 100
  private val defaultMaxExplainFilesPerPair = 20

  private val timeFormatter = ISODateTimeFormat.time()
  
  def getPairFromRef(ref: DiffaPairRef) = domainConfigStore.getPairDef(ref.domain, ref.key)

  def checkpointExplanations(pair: DiffaPairRef) {
    maybeGetPair(pair).map(p => p.checkpointExplanations())
  }

  def logPairEvent(level: DiagnosticLevel, pair: DiffaPairRef, msg: String) {
    val pairDiag = getOrCreatePair(pair)
    pairDiag.logPairEvent(PairEvent(new DateTime(), level, msg))
  }

  def logPairExplanation(pair: DiffaPairRef, source:String, msg: String) {
    getOrCreatePair(pair).logPairExplanation(source, msg)
  }

  def writePairExplanationObject(pair:DiffaPairRef, source:String, objName: String, f:OutputStream => Unit) {
    getOrCreatePair(pair).writePairExplanationObject(source, objName, f)
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
        case Some(pairDiag) => p.key -> pairDiag.scanState
      }).toMap
    }
  }

  def pairScanStateChanged(pair: DiffaPairRef, scanState: PairScanState) = pairs.synchronized {
    val pairDiag = getOrCreatePair(pair)
    pairDiag.scanState = scanState
  }

  /**
   * When pairs are deleted, we stop tracking their status in the pair scan map.
   */
  def onDeletePair(pair:DiffaPairRef) {
    pairs.synchronized {
      pairs.remove(pair) match {
        case None =>
        case Some(pairDiag) => pairDiag.checkpointExplanations
      }
    }
  }

  
  //
  // Lifecycle Management
  //

  override def onAgentInstantiationCompleted(nc: NotificationCentre) {
    nc.registerForPairScanEvents(this)
  }


  //
  // Internals
  //

  private def getOrCreatePair(pair:DiffaPairRef) =
    pairs.synchronized { pairs.getOrElseUpdate(pair, new PairDiagnostics(pair)) }

  private def maybeGetPair(pair:DiffaPairRef) =
    pairs.synchronized { pairs.get(pair) }

  private class PairDiagnostics(pair:DiffaPairRef) {
    private val pairExplainRoot = new File(explainRootDir, pair.identifier)
    private val log = ListBuffer[PairEvent]()
    var scanState:PairScanState = PairScanState.UNKNOWN
    private val pairDef = getPairFromRef(pair)
    private val domainEventsPerPair = getConfigOrElse(pair.domain,
      ConfigOption.eventExplanationLimitKey, defaultMaxEventsPerPair)
    private val domainExplainFilesPerPair = getConfigOrElse(pair.domain,
      ConfigOption.explainFilesLimitKey, defaultMaxExplainFilesPerPair)

    private def getConfigOrElse(domain: String, configKey: String, defaultVal: Int) = try {
      systemConfigStore.maybeSystemConfigOption(configKey).get.toInt
    } catch {
      case _ => defaultVal
    }

    private val maxEvents = math.min(domainEventsPerPair, pairDef.eventsToLog)
    private val maxExplainFiles = math.min(domainExplainFilesPerPair, pairDef.maxExplainFiles)
    private val isLoggingEnabled = maxExplainFiles > 0 && maxEvents > 0

    private val explainLock = new Object
    private var explainDir:File = null
    private var explanationWriter:PrintWriter = null

    def logPairEvent(evt:PairEvent) {
      log.synchronized {
        log += evt

        val drop = log.length - maxEvents
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

    def checkpointExplanations() {
      explainLock.synchronized {
        if (explanationWriter != null) {
          explanationWriter.close()
          explanationWriter = null
        }

        // Compress the contents of the explanation directory
        if (explainDir != null) {
          compressExplanationDir(explainDir)
          explainDir = null

          // Ensure we don't keep too many explanation files
          trimExplanations()
        }
      }
    }

    def logPairExplanation(source:String, msg:String) {
      if (isLoggingEnabled) {
        explainLock.synchronized {
          if (explanationWriter == null) {
            explanationWriter = new PrintWriter(new FileWriter(new File(currentExplainDirectory, "explain.log")))
          }

          explanationWriter.println("%s: [%s] %s".format(timeFormatter.print(new DateTime()), source, msg))
        }
      }
    }

    def writePairExplanationObject(source:String, objName: String, f:OutputStream => Unit) {
      if (isLoggingEnabled) {
        explainLock.synchronized {
          val outputFile = new File(currentExplainDirectory, objName)
          val outputStream = new FileOutputStream(outputFile)
          try {
            f(outputStream)
          } finally {
            outputStream.close()
          }

          logPairExplanation(source, "Attached object " + objName)
        }
      }
    }

    private def currentExplainDirectory = {
      if (explainDir == null) {
        explainDir = new File(pairExplainRoot, System.currentTimeMillis().toString)
        explainDir.mkdirs()
      }

      explainDir
    }

    private def compressExplanationDir(dir:File) {
      val explainFiles = dir.listFiles()
      if (explainFiles != null) {
        val zos = new ZipOutputStream(new FileOutputStream(new File(pairExplainRoot, dir.getName + ".zip")))

        explainFiles.foreach(f => {
          zos.putNextEntry(new ZipEntry(f.getName))

          val inputFile = new FileInputStream(f)
          try {
            IOUtils.copy(inputFile, zos)
          } finally {
            inputFile.close()
          }
          zos.closeEntry()

          f.delete()
        })
        zos.close()
      }
      dir.delete()
    }

    /**
     * Ensures that for each pair, only <maxExplainFilesPerPair> zips are kept. When this value is exceeded,
     * files with older modification dates are removed first.
     */
    private def trimExplanations() {
      val explainFiles = pairExplainRoot.listFiles(new FilenameFilter() {
        def accept(dir: File, name: String) = name.endsWith(".zip")
      })
      if (explainFiles != null && explainFiles.length > maxExplainFiles) {
        val orderedFiles = explainFiles.toSeq.sortBy(f => (f.lastModified, f.getName))
        orderedFiles.take(explainFiles.length - maxExplainFiles).foreach(f => f.delete())
      }
    }
  }
}
