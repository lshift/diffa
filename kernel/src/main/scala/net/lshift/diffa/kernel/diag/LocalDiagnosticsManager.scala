package net.lshift.diffa.kernel.diag

import collection.mutable.{ListBuffer, HashMap}
import net.lshift.diffa.kernel.differencing.{PairScanState, PairScanListener}
import net.lshift.diffa.kernel.lifecycle.{NotificationCentre, AgentLifecycleAware}
import org.slf4j.{Logger, LoggerFactory}
import java.io._
import java.util.zip.{ZipEntry, ZipOutputStream}
import org.apache.commons.io.IOUtils
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import org.joda.time.{DateTimeZone, DateTime}
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.schema.servicelimits._

/**
 * Local in-memory implementation of the DiagnosticsManager.
 *
 *   TODO: Release resources when pair is removed
 */
class LocalDiagnosticsManager(systemConfigStore:SystemConfigStore,
                              domainConfigStore:DomainConfigStore,
                              limits:PairServiceLimitsView,
                              explainRootDir:String)
    extends DiagnosticsManager
    with PairScanListener
    with AgentLifecycleAware {

  private val pairs = HashMap[PairRef, PairDiagnostics]()

  private val timeFormatter = ISODateTimeFormat.time()
  private val fileNameFormatter = DateTimeFormat.forPattern(DiagnosticsManager.fileSystemFriendlyDateFormat)
  
  def getPairFromRef(ref: PairRef) = domainConfigStore.getPairDef(ref)

  def checkpointExplanations(scanId:Option[Long], pair: PairRef) {
    maybeGetPair(pair).map(p => p.checkpointExplanations())
  }

  def logPairEvent(scanId:Option[Long], pair: PairRef, level: DiagnosticLevel, msg: String) {
    val pairDiag = getOrCreatePair(pair)
    pairDiag.logPairEvent(PairEvent(new DateTime(), level, msg))
  }

  def logPairExplanation(scanId:Option[Long], pair: PairRef, source:String, msg: String) {
    getOrCreatePair(pair).logPairExplanation(source, msg)
  }

  def writePairExplanationObject(scanId:Option[Long], pair:PairRef, source:String, objName: String, f:OutputStream => Unit) {
    getOrCreatePair(pair).writePairExplanationObject(source, objName, f)
  }

  def queryEvents(pair:PairRef, maxEvents: Int) = {
    pairs.synchronized { pairs.get(pair) } match {
      case None           => Seq()
      case Some(pairDiag) => pairDiag.queryEvents(maxEvents)
    }
  }

  def retrievePairScanStatesForDomain(space:Long) = {
    val domainPairs = domainConfigStore.listPairs(space)

    pairs.synchronized {
      domainPairs.map(p => pairs.get(PairRef(p.key, space)) match {
        case None           => p.key -> PairScanState.UNKNOWN
        case Some(pairDiag) => p.key -> pairDiag.scanState
      }).toMap
    }
  }

  def pairScanStateChanged(pair: PairRef, scanState: PairScanState) = pairs.synchronized {
    val pairDiag = getOrCreatePair(pair)
    pairDiag.scanState = scanState
  }

  /**
   * When pairs are deleted, we stop tracking their status in the pair scan map.
   */
  def onDeletePair(pair:PairRef) {
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

  private def getOrCreatePair(pair:PairRef) =
    pairs.synchronized { pairs.getOrElseUpdate(pair, new PairDiagnostics(pair)) }

  private def maybeGetPair(pair:PairRef) =
    pairs.synchronized { pairs.get(pair) }

  private class PairDiagnostics(pair:PairRef) {
    private val pairExplainRoot = new File(explainRootDir, pair.identifier)
    private val log = ListBuffer[PairEvent]()
    var scanState:PairScanState = PairScanState.UNKNOWN
    private val pairDef = getPairFromRef(pair)

    private val explainLock = new Object
    private var explainDir:File = null
    private var explanationWriter:PrintWriter = null

    private def getEventBufferSize = limits.getEffectiveLimitByNameForPair(pair.space, pair.name, DiagnosticEventBufferSize)
    private def getMaxExplainFiles = limits.getEffectiveLimitByNameForPair(pair.space, pair.name, ExplainFiles)

    def logPairEvent(evt:PairEvent) {
      log.synchronized {
        log += evt

        val drop = log.length - getEventBufferSize
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
      if (getMaxExplainFiles > 0) {
        explainLock.synchronized {
          if (explanationWriter == null) {
            explanationWriter = new PrintWriter(new FileWriter(new File(currentExplainDirectory, "explain.log")))
          }

          explanationWriter.println("%s: [%s] %s".format(timeFormatter.print(new DateTime()), source, msg))
        }
      }
    }

    def writePairExplanationObject(source:String, objName: String, f:OutputStream => Unit) {
      if (getMaxExplainFiles > 0) {
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
        val now: DateTime = new DateTime
        var cnt = 0;
        do {
          val dt: String = fileNameFormatter.print(now)
          explainDir = new File(pairExplainRoot, "%s-%06d".format(dt, cnt))
          cnt += 1;
        } while (explainDir.exists() || zipFileForExplainDir(explainDir).exists())
        explainDir.mkdirs()
      }

      explainDir
    }

    private def compressExplanationDir(dir:File) {
      val explainFiles = dir.listFiles()
      if (explainFiles != null) {
        val zos = new ZipOutputStream(new FileOutputStream(zipFileForExplainDir(dir)))

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

    def zipFileForExplainDir(dir: File): File = {
      new File(pairExplainRoot, dir.getName + ".zip")
    }

    /**
     * Ensures that for each pair, only <maxExplainFilesPerPair> zips are kept. When this value is exceeded,
     * files with older modification dates are removed first.
     */
    private def trimExplanations() {
      val explainFiles = pairExplainRoot.listFiles(new FilenameFilter() {
        def accept(dir: File, name: String) = name.endsWith(".zip")
      })
      if (explainFiles != null && explainFiles.length > getMaxExplainFiles) {
        val orderedFiles = explainFiles.toSeq.sortBy(_.getName)
        val todelete: Seq[File] = orderedFiles.take(explainFiles.length - getMaxExplainFiles)
        todelete.foreach(f => log.debug(s"Removing file: ${f}"))
        todelete.foreach(f => f.delete())
      }
    }
  }
}

