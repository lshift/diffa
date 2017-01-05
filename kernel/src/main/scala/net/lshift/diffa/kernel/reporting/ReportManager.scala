/**
 * Copyright (C) 2011 LShift Ltd.
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
package net.lshift.diffa.kernel.reporting

import net.lshift.diffa.kernel.config.{PairReportType, DiffaPairRef, DomainConfigStore}
import net.lshift.diffa.kernel.differencing.DomainDifferenceStore
import org.apache.http.impl.client.DefaultHttpClient
import net.lshift.diffa.kernel.diag.{DiagnosticLevel, DiagnosticsManager}
import org.apache.http.client.methods.HttpPost
import org.apache.commons.io.FileUtils
import java.io.{FileInputStream, FileWriter, PrintWriter, File}
import org.apache.http.entity.InputStreamEntity
import org.joda.time.format.ISODateTimeFormat
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.util.MissingObjectException

/**
 * Component responsible for executing reports defined on a pair.
 */
class ReportManager(configStore:DomainConfigStore, diffStore:DomainDifferenceStore, diagnostics:DiagnosticsManager) {
  import PairReportType._

  /**
   * Executes a report with the given name for the given pair.
   * @throws MissingObjectException if the requested report doesn't exist.
   */
  def executeReport(pair:DiffaPairRef, name:String) {
    val reportDef = configStore.getPairDef(pair.domain, pair.key).reports.
      find(_.name == name).getOrElse(throw new MissingObjectException("pair report"))

    diagnostics.logPairEvent(None, pair, DiagnosticLevel.INFO, "Initiating report %s".format(name))
    try {
      // Execute the report, and stream it to disk. It is necessary to do this because a HttpPost needs to
      // include a length, which we won't know till we've assembled the report
      withTempFile(reportTmp => {
        val reportWriter = new PrintWriter(new FileWriter(reportTmp))
        try {
          reportDef.reportType match {
            case DIFFERENCES => generateDifferencesReport(pair, reportWriter)
          }
        } finally {
          reportWriter.close()
        }

        postReport(reportDef.target, reportTmp)
      })
      diagnostics.logPairEvent(None, pair, DiagnosticLevel.INFO, "Completed report %s".format(name))
    } catch {
      case e: Throwable =>
        diagnostics.logPairEvent(None, pair, DiagnosticLevel.ERROR, "Report %s failed: %s".format(name, e.getMessage))
    }
  }

  private def generateDifferencesReport(pair:DiffaPairRef, reportWriter:PrintWriter) {
    val datetimeFormatter = ISODateTimeFormat.basicDateTime().withZoneUTC()

    reportWriter.println("detection date,entity id,upstream version,downstream version,state")
    diffStore.streamUnmatchedEvents(pair, event => {
      val state = (event.upstreamVsn, event.downstreamVsn) match {
        case (null, null) => "entirely-absent"
        case (_, null)    => "missing-from-downstream"
        case (null, _)    => "missing-from-upstream"
        case (_, _)       => "version-mismatch"
      }

      reportWriter.println(
        event.detectedAt.toString(datetimeFormatter) + "," +
        event.objId.id + "," +
        formatVersion(event.upstreamVsn) + "," +
        formatVersion(event.downstreamVsn) + "," +
        state
      )
    })
  }

  private def postReport(target:String, reportFile:File) {
    val client = new DefaultHttpClient
    val reportLength = reportFile.length()
    val fileStream = new FileInputStream(reportFile)

    try {
      val reportPost = new HttpPost(target)
      val entity = new InputStreamEntity(fileStream, reportLength)
      entity.setContentType("text/csv")
      reportPost.setEntity(entity)
      client.execute(reportPost)
    } finally {
      fileStream.close()
    }
  }

  private def withTempFile[T](f:(File) => T):T = {
    val reportTmp = File.createTempFile("diffa-report", ".csv")
    try {
      f(reportTmp)
    } finally {
      // Delete the tmp file either now or later
      if (!FileUtils.deleteQuietly(reportTmp))
        reportTmp.deleteOnExit();
    }
  }

  private def formatVersion(vsn:String) = vsn match {
    case null => ""
    case v    => v
  }
}