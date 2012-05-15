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
import java.io.Closeable
import scala.collection.JavaConversions._
import org.apache.lucene.search._
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.participants._
import collection.mutable.{ListBuffer}
import net.lshift.diffa.kernel.differencing._
import org.apache.lucene.document._
import net.lshift.diffa.participant.scanning._
import net.lshift.diffa.kernel.config.system.{InvalidSystemConfigurationException, SystemConfigStore}
import org.apache.lucene.index.{IndexReader, Term}
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.kernel.util._

/**
 * Implementation of the VersionCorrelationStore that utilises Lucene to store (and index) the version information
 * provided. Lucene is utilised as it provides for schema-free storage, which strongly suits the dynamic schema nature
 * of pair attributes.
 */
class LuceneVersionCorrelationStore(val pair: DiffaPairRef, index:Directory, configStore:SystemConfigStore, diagnostics:DiagnosticsManager)
    extends VersionCorrelationStore
    with Closeable {

  import LuceneVersionCorrelationHandler._

  private val log = LoggerFactory.getLogger(getClass)

  val version = VersionCorrelationStore.currentSchemaVersion.toString
  val schemaKey = VersionCorrelationStore.schemaVersionKey

  configStore.maybeSystemConfigOption(schemaKey) match {
    case None          => configStore.setSystemConfigOption(schemaKey,version)
    case Some(x)       => {
      // Check to see if we're up to date
      if (x != version) {
        // We're not up to date, so perform an upgrade
        // Ticket #323 - We need to migrate from this version, but we currently don't have the capability to do this
        val msg = "%s: Do not have the ability the migrate the correlation store from version %s (see ticket #323), exiting now"
        log.error(msg.format(AlertCodes.INVALID_SYSTEM_CONFIGURATION, x))
        throw new InvalidSystemConfigurationException("Cannot migrate correlation store")
      }
    }
  }

  val writer = new LuceneWriter(index, diagnostics)

  def openWriter() = writer // TODO: rename this - openWriter is a misnomer; probably should be getWriter

  def unmatchedVersions(usConstraints:Seq[ScanConstraint], dsConstraints:Seq[ScanConstraint], fromVersion:Option[Long]) = {
    searchForCorrelations(fromVersion, query => {
      query.add(new TermQuery(new Term("isMatched", "0")), BooleanClause.Occur.MUST)
      applyConstraints(query, usConstraints, Upstream, true)
      applyConstraints(query, dsConstraints, Downstream, true)
    })
  }

  def tombstoneVersions(fromVersion:Option[Long]) = {
    searchForCorrelations(fromVersion, query => {
      addTombstoneClauses(query)
    })
  }

  private def searchForCorrelations(fromVersion:Option[Long], f:BooleanQuery => Any) = {
    val query = new BooleanQuery
    f(query)
    maybeAddStoreVersionConstraint(query, fromVersion)
    withSearcher(writer, s => {
      val idOnlyCollector = new DocIdOnlyCollector
      s.search(query, idOnlyCollector)
      idOnlyCollector.allCorrelations(s)
    })
  }


  private def maybeAddStoreVersionConstraint(query:BooleanQuery, fromVersion:Option[Long]) = fromVersion match {
    case None          => // ignore
    case Some(version) =>
      query.add(NumericRangeQuery.newLongRange("store.version", version, Long.MaxValue, false, true), BooleanClause.Occur.MUST)
  }

  def retrieveCurrentCorrelation(id:VersionID) = {
    retrieveCurrentDoc(writer, id) match {
      case None => None
      case Some(doc) => Some(docToCorrelation(doc, id))
    }
  }

  def queryUpstreams(constraints:Seq[ScanConstraint]) = {
    val query = new BooleanQuery
    applyConstraints(query, constraints, Upstream, false)

    withSearcher(writer, s => {
      val idOnlyCollector = new DocIdOnlyCollector
      s.search(preventEmptyQuery(query), idOnlyCollector)
      idOnlyCollector.allSortedCorrelations(s).filter(c => c.upstreamVsn != null)
    })

  }
  def queryDownstreams(constraints:Seq[ScanConstraint]) = {
    val query = new BooleanQuery
    applyConstraints(query, constraints, Downstream, false)

    val idOnlyCollector = new DocIdOnlyCollector
    val searcher = new IndexSearcher(writer.getReader)
    searcher.search(preventEmptyQuery(query), idOnlyCollector)
    idOnlyCollector.allSortedCorrelations(searcher).filter(c => c.downstreamUVsn != null)
  }

  def ensureUpgradeable(side:EndpointSide, changes:Seq[CategoryChange]) {
    // Allows a query to be run against the appropriate side
    def doQuery(constraints:Seq[ScanConstraint]) = side match {
      case UpstreamEndpoint => queryUpstreams(constraints)
      case DownstreamEndpoint => queryDownstreams(constraints)
    }

    lazy val existingCorrelationCount = doQuery(Seq()).size
    lazy val hasCorrelations = existingCorrelationCount > 0

    // We can't support additions if we have data.
    val addition = changes.find(_.isAddition)
    if (addition.isDefined && hasCorrelations) {
      throw new IncompatibleCategoryChangeException(addition.get.name,
        "Cannot add a category as existing data is stored for pair " + pair)
    }

    // Changes might be supported, depending on the details
    changes.filter(_.isChange).foreach(c => {
      val before = c.before.get
      val after = c.after.get

      // The type of a category can't be changed if we have data.
      if (!before.isSameType(after) && hasCorrelations) {
        throw new IncompatibleCategoryChangeException(c.name,
          "Cannot change category type as existing data is stored for pair " + pair)
      }

      val newCorrelationCount = doQuery(CategoryUtil.initialConstraintsFor(Seq(c.name -> after))).size
      val includesAllCorrelations = (existingCorrelationCount == newCorrelationCount)

      // The new category descriptor should retrieve the same number of values from the correlation store as
      // existing correlation.
      if (!includesAllCorrelations) {
        throw new IncompatibleCategoryChangeException(c.name,
          "Updated category bounds do not cover all stored values for pair " + pair)
      }
    })
  }

  private def applyConstraints(query:BooleanQuery, constraints:Seq[ScanConstraint], partType:StoreParticipantType, allowMissing:Boolean) = {
    val prefix = partType.prefix

    // Apply all upstream constraints to a subquery
    val partQuery = new BooleanQuery
    constraints.foreach {
      case r:RangeConstraint          => {
        val tq = r match {
          case t:TimeRangeConstraint =>
            new TermRangeQuery(prefix + t.getAttributeName, formatDateTime(t.getStart), formatDateTime(t.getEnd), true, true)
          case d:DateRangeConstraint =>
            new TermRangeQuery(prefix + d.getAttributeName, formatDate(d.getStart), formatDate(d.getEnd), true, true)
          case i:IntegerRangeConstraint =>
            NumericRangeQuery.newIntRange(prefix + i.getAttributeName, i.getStart, i.getEnd, true, true)
        }
        partQuery.add(tq, BooleanClause.Occur.MUST)
      }
      case s:StringPrefixConstraint => {
        val wq = new WildcardQuery(new Term(prefix + s.getAttributeName, s.getPrefix + "*"))
        partQuery.add(wq, BooleanClause.Occur.MUST)
      }
      case s:SetConstraint  => {
        val setMatchQuery = new BooleanQuery
        s.getValues.foreach(x => setMatchQuery.add(new TermQuery(new Term(prefix + s.getAttributeName, x)), BooleanClause.Occur.SHOULD))
        partQuery.add(setMatchQuery, BooleanClause.Occur.MUST)
      }
    }

    // We don't want to add our sub-query unless it has terms, since an empty MUST matches nothing.
    if (partQuery.clauses().length > 0) {
      if (allowMissing) {
        // Since we allow the participant values to be missing (ie, only the other participant has a value for this id),
        // then we need to insert an OR query where either the value is missing or it matches the constraints.

        val missingOrMatchingQuery = new BooleanQuery
        missingOrMatchingQuery.add(new TermQuery(new Term(partType.presenceIndicator, "0")), BooleanClause.Occur.SHOULD)
        missingOrMatchingQuery.add(partQuery, BooleanClause.Occur.SHOULD)

        query.add(missingOrMatchingQuery, BooleanClause.Occur.MUST)
      } else {
        // If we don't allow this part to be missing, then enforce that it's requirement is present and correct.
        partQuery.add(new TermQuery(new Term(partType.presenceIndicator, "1")), BooleanClause.Occur.MUST)
        query.add(partQuery, BooleanClause.Occur.MUST)
      }
    }
  }

  private def preventEmptyQuery(query: BooleanQuery): Query =
    if (query.getClauses.length == 0)
      new MatchAllDocsQuery()
    else
      query

  private class DocIdOnlyCollector extends org.apache.lucene.search.Collector {
    val docIds = new ListBuffer[Int]
    private var docBase:Int = 0

    def acceptsDocsOutOfOrder = true
    def setNextReader(reader: IndexReader, docBase: Int) = this.docBase = docBase
    def collect(doc: Int) = docIds.add(docBase + doc)
    def setScorer(scorer: Scorer) = {}   // Not needed

    def allCorrelations(searcher:IndexSearcher) = {
      docIds.map(id => {
        val doc = searcher.doc(id)
        docToCorrelation(doc, pair)
      })
    }

    def allSortedCorrelations(searcher:IndexSearcher) = allCorrelations(searcher).sortBy(c => c.id)
  }

  def close = {
    openWriter.close
    index.close
  }

  def reset() = {
    openWriter.reset
  }
}
