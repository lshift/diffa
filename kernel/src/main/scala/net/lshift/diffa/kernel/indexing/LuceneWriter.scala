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
package net.lshift.diffa.kernel.indexing

import org.apache.lucene.store.Directory
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.events.VersionID
import org.apache.lucene.util.Version
import org.apache.lucene.analysis.standard.StandardAnalyzer
import net.lshift.diffa.kernel.differencing._
import org.joda.time.{LocalDate, DateTimeZone, DateTime}
import org.apache.lucene.document.{NumericField, Fieldable, Field, Document}
import collection.mutable.{HashSet, HashMap}
import scala.collection.JavaConversions._
import org.apache.lucene.index.{IndexReader, IndexWriter, IndexWriterConfig, Term}
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import org.apache.lucene.search.{BooleanQuery, BooleanClause, TermQuery}

class LuceneWriter(index: Directory, diagnostics:DiagnosticsManager) extends ExtendedVersionCorrelationWriter {
  import LuceneVersionCorrelationHandler._

  private val log = LoggerFactory.getLogger(getClass)

  private val maxBufferSize = 10000

  private val updatedDocs = HashMap[VersionID, Document]()
  private var writer = createIndexWriter

  def getReader : IndexReader = IndexReader.open(writer, true)

  private val VERSION_LABEL = "latest.store.version"
  private var latestVersion : Long = getReader.getCommitUserData match {
    case null     => 0L
    case userData => {
      userData.get(VERSION_LABEL) match {
        case null => 0L
        case version => version.toLong
      }
    }
  }

  def storeUpstreamVersion(id:VersionID, attributes:scala.collection.immutable.Map[String,TypedAttribute], lastUpdated: DateTime, vsn: String) = {
    log.trace("Indexing upstream " + id + " with attributes: " + attributes + " lastupdated at " + lastUpdated + " with version " + vsn)

    def extractKeyFields(doc:Document) = Map("uvsn" -> doc.get("uvsn")) ++ findAttributes(doc, "up.")
    val newFields = Map("uvsn" -> vsn) ++ AttributesUtil.toUntypedMap(attributes)

    doDocUpdate(id, lastUpdated, extractKeyFields, newFields, "upstream", doc => {
      // Update all of the upstream attributes
      applyAttributes(doc, "up.", attributes)
      updateField(doc, boolField(Upstream.presenceIndicator, true))
      updateField(doc, stringField("uvsn", vsn))
    })
  }

  def storeDownstreamVersion(id: VersionID, attributes: scala.collection.immutable.Map[String, TypedAttribute], lastUpdated: DateTime, uvsn: String, dvsn: String) = {
    log.trace("Indexing downstream " + id + " with attributes: " + attributes + " lastupdated at " + lastUpdated + " with up-version " + uvsn + "and down-version " + dvsn)

    def extractKeyFields(doc:Document) =
      Map("duvsn" -> doc.get("duvsn"), "ddvsn" -> doc.get("ddvsn")) ++ findAttributes(doc, "down.")
    val newFields = Map("duvsn" -> uvsn, "ddvsn" -> dvsn) ++ AttributesUtil.toUntypedMap(attributes)

    log.trace("Indexing downstream " + id + " with attributes: " + attributes)
    doDocUpdate(id, lastUpdated, extractKeyFields, newFields, "downstream", doc => {
      // Update all of the upstream attributes
      applyAttributes(doc, "down.", attributes)
      updateField(doc, boolField(Downstream.presenceIndicator, true))
      updateField(doc, stringField("duvsn", uvsn))
      updateField(doc, stringField("ddvsn", dvsn))
    })
  }

  def clearUpstreamVersion(id:VersionID) = {
    doClearAttributes(id, "upstream", doc => {
      // Remove all the upstream attributes. Convert to list as middle-step to prevent ConcurrentModificationEx - see #177
      doc.getFields.toList.foreach(f => {
        if (f.name.startsWith("up.")) doc.removeField(f.name)
      })
      updateField(doc, boolField(Upstream.presenceIndicator, false))
      doc.removeField("uvsn")
    })
  }

  def clearDownstreamVersion(id:VersionID) = {
    doClearAttributes(id, "downstream", doc => {
      // Remove all the upstream attributes. Convert to list as middle-step to prevent ConcurrentModificationEx - see #177
      doc.getFields.toList.foreach(f => {
        if (f.name.startsWith("down.")) doc.removeField(f.name)
      })
      updateField(doc, boolField(Downstream.presenceIndicator, false))
      doc.removeField("duvsn")
      doc.removeField("ddvsn")
    })
  }

  def isDirty = updatedDocs.size > 0

  def rollback() = {
    writer.rollback()
    writer = createIndexWriter    // We need to create a new writer, since rollback will have closed the previous one
    log.info("Writer rolled back")
  }

  def clearTombstones() {
    prepareFlush()
    writer.deleteDocuments(createTombstoneQuery)
    flushInternal()
  }

  def flush() {
    if (prepareFlush()) {
      flushInternal()
    }
  }

  def reset() {
    writer.deleteAll()
    writer.commit()
  }

  def close() {
    writer.close()
  }

  private def createIndexWriter() = {
    val version = Version.LUCENE_34
    val config = new IndexWriterConfig(version, new StandardAnalyzer(version))
    val writer = new IndexWriter(index, config)
    writer
  }

  private def prepareUpdate(id: VersionID, doc: Document) = {
    updatedDocs.put(id, doc)
    if (updatedDocs.size >= maxBufferSize) {
      flush()
    }
  }

  private def getCurrentOrNewDoc(id:VersionID) = {
    if (updatedDocs.contains(id)) {
      updatedDocs(id)
    } else {
      val doc = retrieveCurrentDoc(this, id)

      doc match {
        case None => {
          // Nothing in the index yet for this document, or it's pending deletion
          val doc = new Document
          doc.add(new Field("id", id.id, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS, Field.TermVector.NO))
          doc.add(new Field(Upstream.presenceIndicator, "0", Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS, Field.TermVector.NO))
          doc.add(new Field(Downstream.presenceIndicator, "0", Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS, Field.TermVector.NO))
          doc
        }
        case Some(doc) => doc
      }
    }
  }

  private def doDocUpdate(id:VersionID, lastUpdatedIn:DateTime, extractFields:Document => Map[String, String], newFields:Map[String, String], sectionName:String, f:Document => Unit) = {
    val doc = getCurrentOrNewDoc(id)

    val currentFields = extractFields(doc)
    val changedFields = summariseChanges(currentFields, newFields)

    // If any of the key fields have been changed, then we'll apply an update
    if (changedFields.size > 0) {
      diagnostics.logPairExplanation(id.pair, "Correlation Store", "Updating %s (%s) with changes (%s)".format(id.id, sectionName,
        changedFields.map { case (k, (ov, nv)) => k + ": " + ov + " -> " + nv }.mkString(", ")))

      // Increment the version counter and update the entry
      updateStoreVersion(doc)
      f(doc)

      // If the participant does not supply a timestamp, then create one on the fly
      val lastUpdated = lastUpdatedIn match {
        case null => new DateTime
        case d    => d
      }

      val oldLastUpdate = parseDate(doc.get("lastUpdated"))
      if (oldLastUpdate == null || lastUpdated.isAfter(oldLastUpdate)) {
        updateField(doc, dateTimeField("lastUpdated", lastUpdated, indexed = false))
      }

      // Update the matched status
      val isMatched = doc.get("uvsn") == doc.get("duvsn")
      updateField(doc, boolField("isMatched", isMatched))

      // Update the timestamp
      updateField(doc, dateTimeField("timestamp", new DateTime().withZone(DateTimeZone.UTC), indexed = false))

      prepareUpdate(id, doc)
    }

    docToCorrelation(doc, id)
  }

  private def updateStoreVersion(doc:Document) = {
    latestVersion += 1
    updateField(doc, longField("store.version", latestVersion))
  }

  private def doClearAttributes(id:VersionID, sectionName:String, f:Document => Unit) = {
    val currentDoc =
      if (updatedDocs.contains(id))
        Some(updatedDocs(id))
      else
        retrieveCurrentDoc(this, id)

    currentDoc match {
      case None => Correlation.asDeleted(id, new DateTime)
      case Some(doc) => {

        // Increment the version counter and update the entry
        updateStoreVersion(doc)
        f(doc)

        diagnostics.logPairExplanation(id.pair, "Correlation Store", "Updating %s to remove %s".format(id.id, sectionName))

        // Update the match status of the document. A document with neither participant is matched (and will be seen
        // as a tombstone)
        updateField(doc, boolField("isMatched", hasUpstream(doc) || hasDownstream(doc)))

        prepareUpdate(id, doc)

        docToCorrelation(doc, id)
      }
    }
  }

  private def applyAttributes(doc:Document, prefix:String, attributes:Map[String, TypedAttribute]) = {
    attributes.foreach { case (k, v) => {
      val vF = v match {
        case StringAttribute(s)     => stringField(prefix + k, s)
        case DateAttribute(dt)      => dateField(prefix + k, dt)
        case DateTimeAttribute(dt)  => dateTimeField(prefix + k, dt)
        case IntegerAttribute(intV) => intField(prefix + k, intV)
      }
      updateField(doc, vF)
    } }
  }

  private def updateField(doc:Document, field:Fieldable) = {
    doc.removeField(field.name)
    doc.add(field)
  }

  private def stringField(name:String, value:String, indexed:Boolean = true) =
    new Field(name, value, Field.Store.YES, indexConfig(indexed), Field.TermVector.NO)
  private def dateTimeField(name:String, dt:DateTime, indexed:Boolean = true) =
    new Field(name, formatDateTime(dt), Field.Store.YES, indexConfig(indexed), Field.TermVector.NO)
  private def dateField(name:String, dt:LocalDate, indexed:Boolean = true) =
    new Field(name, formatDate(dt), Field.Store.YES, indexConfig(indexed), Field.TermVector.NO)
  private def intField(name:String, value:Int, indexed:Boolean = true) =
    (new NumericField(name, Field.Store.YES, indexed)).setIntValue(value)
  private def longField(name:String, value:Long, indexed:Boolean = true) =
    (new NumericField(name, Field.Store.YES, indexed)).setLongValue(value)
  private def boolField(name:String, value:Boolean, indexed:Boolean = true) =
    new Field(name, if (value) "1" else "0", Field.Store.YES, indexConfig(indexed), Field.TermVector.NO)
  private def indexConfig(indexed:Boolean) = if (indexed) Field.Index.NOT_ANALYZED_NO_NORMS else Field.Index.NO

   /**
   * Differences two maps, producing a map containing fields that have changed (with the key being the field name, and
   * the value being a (before, after) pair.
   */
  private def summariseChanges(storedFields:Map[String, String], newFields:Map[String, String]) = {
    val result = scala.collection.mutable.HashMap[String, Tuple2[String, String]]()

    storedFields.foreach { case (k, currentV) =>
      newFields.get(k) match {
        case None           => result(k) = (currentV, null)
        case Some(matchingV) if currentV == matchingV => // New value matches, so ignore
        case Some(otherV)   => result(k) = (currentV, otherV)
      }
    }
    newFields.foreach { case (k, newV) =>
      storedFields.get(k) match {
        case None     =>  result(k) = (null, newV)
        case Some(_)  => // Ignore - comparison of fields already done in previous iteration
      }
    }

    result.toMap
  }

  private def prepareFlush() = {
    if (isDirty) {
      updatedDocs.foreach { case (id, doc) =>
        writer.updateDocument(new Term("id", id.id), doc)
      }
      true
    } else {
      false
    }
  }

  private def flushInternal() {
    writer.commit(Map(VERSION_LABEL -> latestVersion.toString))
      updatedDocs.clear()
      log.trace("Writer flushed")
  }
}