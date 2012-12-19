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
package net.lshift.diffa.schema.jooq

import java.sql.{Timestamp, Connection}
import javax.sql.DataSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.transaction.support.{TransactionCallback, TransactionTemplate}
import org.springframework.transaction.TransactionStatus
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.jooq.conf.{RenderNameStyle, Settings}
import org.jooq.impl.Factory
import org.jooq._
import org.joda.time.DateTime
import org.jadira.usertype.dateandtime.joda.columnmapper.TimestampColumnDateTimeMapper
import java.lang.reflect.UndeclaredThrowableException

class DatabaseFacade(dataSource: DataSource, dialect: String) {

  val resolvedDialect = SQLDialect.valueOf(dialect)
  private val settings = new Settings()
    .withRenderSchema(false)
    .withRenderNameStyle(RenderNameStyle.LOWER)

  private val jdbcTemplate = new JdbcTemplate(dataSource)
  private val txTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource))

  def execute[T](f: Factory => T): T =
    try {
      txTemplate.execute(new TransactionCallback[T] {
        def doInTransaction(status: TransactionStatus) =
          jdbcTemplate.execute(new ConnectionCallback[T] {
            def doInConnection(conn: Connection) = {
              f(new Factory(conn, resolvedDialect, settings))
            }
          })
      })
    } catch {
      // N.B. TransactionTemplate wraps checked exceptions in UndeclaredThrowableException
      case e: UndeclaredThrowableException if e.getCause != null =>
        throw e.getCause
    }

  def processAsStream[R <: Record](cursor: Cursor[R], handler: R => Unit) = try {
    while (cursor.hasNext) {
      handler(cursor.fetchOne())
    }
  } finally {
    if (! cursor.isClosed) cursor.close()
  }

  def getById[K, R <: Record, O](f: Factory, table: Table[R], keyColumn: Field[K], id: K, converter: R => O) =
    Option(f.selectFrom(table).where(keyColumn.equal(id)).fetchOne()).map(converter)

  def getById[K1, K2, R <: Record, O](f: Factory, table: Table[R], keyColumn1: Field[K1], keyColumn2: Field[K2], id1: K1, id2: K2, converter: R => O) =
    Option(f.selectFrom(table).where(keyColumn1.equal(id1).and(keyColumn2.equal(id2))).fetchOne()).map(converter)
  
}

object DatabaseFacade {

  private val columnMapper = new TimestampColumnDateTimeMapper()

  def timestampToDateTime(timestamp: Timestamp) = {
    if (timestamp == null) {
      null
    }
    else {
      columnMapper.fromNonNullValue(timestamp)
    }
  }

  def dateTimeToTimestamp(dateTime: DateTime) = {
    if (dateTime == null) {
      null
    }
    else {
      columnMapper.toNonNullValue(dateTime)
    }
  }
}
