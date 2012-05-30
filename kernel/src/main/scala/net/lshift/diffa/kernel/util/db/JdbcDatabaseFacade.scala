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

package net.lshift.diffa.kernel.util.db

import javax.sql.DataSource
import org.slf4j.LoggerFactory
import java.sql.{ResultSet, PreparedStatement, Connection}
import net.lshift.diffa.kernel.util.AlertCodes._


class JdbcDatabaseFacade(ds:DataSource) extends NextGenerationDatabaseFacade {

  val log = LoggerFactory.getLogger(getClass)

  def forEachRow(queryString: String, callback: ResultSetCallback) = {

    var connection:Connection = null
    var statement:PreparedStatement = null
    var rs:ResultSet = null

    try {
      connection = ds.getConnection
      statement = connection.prepareStatement(queryString)

      // No parameters to set here - this is quite a limited utility, but at least it is pure JDBC

      rs = statement.executeQuery()

      while (rs.next) {
        callback.onRow(rs)
      }
    }
    catch {
      case x:Exception => {
        log.error(formatAlertCode(DB_EXECUTION_ERROR), x)
        throw x
      }
    }
    finally {
      releaseResource(rs, (x:ResultSet) => x.close)
      releaseResource(statement, (x:PreparedStatement) => x.close)
      releaseResource(connection, (x:Connection) => x.close, true)
    }
  }

  private def releaseResource[T](t:T, close:T => Unit, shouldThrowFurther:Boolean = false) {

    try {
      if (t != null) {
        close(t)
      }
    }
    catch {
      case x:Exception => {
        log.error(formatAlertCode(DB_RELEASE_ERROR), x)
        if (shouldThrowFurther) {
          throw x
        }
      }
    }

  }

}
