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

import net.lshift.diffa.kernel.events.VersionID
import org.apache.lucene.document.Document
import net.lshift.diffa.kernel.config.DiffaPairRef
import net.lshift.diffa.kernel.differencing.Correlation
import net.lshift.diffa.kernel.differencing.Correlation._
import collection.mutable.HashMap
import org.apache.lucene.search.{BooleanClause, TermQuery, BooleanQuery, IndexSearcher}
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{LocalDate, DateTime, DateTimeZone}
import scala.collection.JavaConversions._
import org.apache.lucene.index.{Term, IndexReader}

/**
 * Utility that provides common Lucene document to Diffa Correlation routines.
 */
object LuceneVersionCorrelationHandler {

  trait StoreParticipantType {
    def prefix:String
    def presenceIndicator:String
  }
  case object Upstream extends StoreParticipantType {
    val prefix = "up."
    val presenceIndicator = "hasUpstream"
  }
  case object Downstream extends StoreParticipantType {
    val prefix = "down."
    val presenceIndicator = "hasDownstream"
  }

  def withSearcher[T](writer:LuceneWriter, f:IndexSearcher => T) = {
    val searcher = new IndexSearcher(writer.getReader)
    val result = f(searcher)
    searcher.close()
    result
  }

  def retrieveCurrentDoc(writer:LuceneWriter, id:VersionID) : Option[Document] = withSearcher(writer, s => {
    val hits = s.search(queryForId(id), 1)
    if (hits.scoreDocs.size == 0) {
      None
    } else {
      Some(s.doc(hits.scoreDocs(0).doc))
    }
  })

  def docToCorrelation(doc:Document, pair:DiffaPairRef) : Correlation = docToCorrelation(doc,pair.key,pair.domain)
  def docToCorrelation(doc:Document, id:VersionID) : Correlation = docToCorrelation(doc,id.pair.key,id.pair.domain)

  def docToCorrelation(doc:Document, pairKey:String, domain:String) = {
    Correlation(
      pairing = pairKey, domain = domain,
      id = doc.get("id"),
      upstreamAttributes = findAttributes(doc, "up."),
      downstreamAttributes = findAttributes(doc, "down."),
      lastUpdate = parseDate(doc.get("lastUpdated")),
      timestamp = parseDate(doc.get("timestamp")),
      upstreamVsn = doc.get("uvsn"),
      storeVersion = parseLong(doc.get("store.version")),
      downstreamUVsn = doc.get("duvsn"),
      downstreamDVsn = doc.get("ddvsn"),
      isMatched = parseBool(doc.get("isMatched"))
    )
  }

  def findAttributes(doc:Document, prefix:String) = {
    val attrs = new HashMap[String, String]
    doc.getFields.foreach(f => {
      if (f.name.startsWith(prefix)) {
        attrs(f.name.substring(prefix.size)) = f.stringValue
      }
    })
    attrs
  }

  def queryForId(id:VersionID) = {
    val query = new BooleanQuery
    query.add(new TermQuery(new Term("id", id.id)), BooleanClause.Occur.MUST)
    query
  }

  private def parseBool(bs:String) = bs != null && bs.equals("1")
  private def parseLong(number:String) = if (number == null) 0L else number.toLong

  def parseDate(ds:String) = {
    if (ds != null) {
      ISODateTimeFormat.dateTimeParser.parseDateTime(ds).withZone(DateTimeZone.UTC)
    } else {
      null
    }
  }

  def formatDateTime(dt:DateTime) = if (dt != null) dt.withZone(DateTimeZone.UTC).toString() else null
  def formatDate(dt:LocalDate) = if (dt != null) dt.toString else null

  def addTombstoneClauses(query:BooleanQuery) {
    query.add(new TermQuery(new Term(Upstream.presenceIndicator, "0")), BooleanClause.Occur.MUST)
    query.add(new TermQuery(new Term(Downstream.presenceIndicator, "0")), BooleanClause.Occur.MUST)
  }

  def createTombstoneQuery = {
    var tombstonedQuery = new BooleanQuery
    addTombstoneClauses(tombstonedQuery)
    tombstonedQuery
  }

  def hasUpstream(doc:Document) = parseBool(doc.get(Upstream.presenceIndicator))
  def hasDownstream(doc:Document) = parseBool(doc.get(Downstream.presenceIndicator))
}