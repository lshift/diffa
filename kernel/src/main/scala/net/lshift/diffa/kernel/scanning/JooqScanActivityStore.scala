/**
 * Copyright (C) 2010-2012 LShift Ltd.
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
package net.lshift.diffa.kernel.scanning

import net.lshift.diffa.schema.jooq.DatabaseFacade
import net.lshift.diffa.schema.jooq.DatabaseFacade._
import net.lshift.diffa.schema.tables.ScanStatements.SCAN_STATEMENTS
import net.lshift.diffa.kernel.config.DiffaPairRef
import org.jooq.Record
import scala.collection.JavaConversions._

class JooqScanActivityStore(jooq:DatabaseFacade) extends ScanActivityStore {

  def createOrUpdateStatement(s:ScanStatement) = jooq.execute(t => {
    t.insertInto(SCAN_STATEMENTS).
        set(SCAN_STATEMENTS.ID, long2Long(s.id)).
        set(SCAN_STATEMENTS.DOMAIN, s.domain).
        set(SCAN_STATEMENTS.PAIR, s.pair).
        set(SCAN_STATEMENTS.INTIATED_BY, s.initiatedBy.orNull).
        set(SCAN_STATEMENTS.START_TIME, dateTimeToTimestamp(s.startTime)).
        set(SCAN_STATEMENTS.END_TIME, dateTimeToTimestamp(s.endTime.orNull)).
        set(SCAN_STATEMENTS.STATE, s.state).
      onDuplicateKeyUpdate().
        set(SCAN_STATEMENTS.INTIATED_BY, s.initiatedBy.orNull).
        set(SCAN_STATEMENTS.START_TIME, dateTimeToTimestamp(s.startTime)).
        set(SCAN_STATEMENTS.END_TIME, dateTimeToTimestamp(s.endTime.orNull)).
        set(SCAN_STATEMENTS.STATE, s.state).
      execute()
  })

  def getStatement(pair:DiffaPairRef, id:Long) : ScanStatement = jooq.execute(t => {
    val record =  t.select().
                    from(SCAN_STATEMENTS).
                    where(SCAN_STATEMENTS.DOMAIN.equal(pair.domain)).
                      and(SCAN_STATEMENTS.PAIR.equal(pair.key)).
                      and(SCAN_STATEMENTS.ID.equal(id)).
                    fetchOne()
    recordToStatement(record)
  })

  private def recordToStatement(record:Record) = ScanStatement(
    id = record.getValue(SCAN_STATEMENTS.ID),
    domain =  record.getValue(SCAN_STATEMENTS.DOMAIN),
    pair =  record.getValue(SCAN_STATEMENTS.PAIR),
    initiatedBy =  Option(record.getValue(SCAN_STATEMENTS.INTIATED_BY)),
    startTime =  timestampToDateTime(record.getValue(SCAN_STATEMENTS.START_TIME)),
    endTime =  Option(timestampToDateTime(record.getValue(SCAN_STATEMENTS.END_TIME))),
    state = record.getValue(SCAN_STATEMENTS.STATE)
  )
}
