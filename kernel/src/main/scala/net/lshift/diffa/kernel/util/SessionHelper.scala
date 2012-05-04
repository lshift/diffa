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

package net.lshift.diffa.kernel.util

import org.hibernate.{Session, SessionFactory}
import org.slf4j.LoggerFactory
import java.sql.Connection
import org.hibernate.jdbc.Work

/**
 * Hibernate session convenience class/object
 */
class SessionHelper(val sessionFactory:SessionFactory) {

  val log = LoggerFactory.getLogger(getClass)

  def withSession[T](dbCommands:Function1[Session, T]) : T = withSessionInternal(dbCommands, None, None)

  def withSession[T](beforeCommit:Function1[Session,_],
                     dbCommands:Function1[Session, T],
                     afterCommit:Function0[_]) : T = withSessionInternal(dbCommands, Some(beforeCommit), Some(afterCommit))

  private def withSessionInternal[T](dbCommands:Function1[Session, T],
                                     beforeCommit:Option[Function1[Session,_]],
                                     afterCommit:Option[Function0[_]]) : T = {
    val session = sessionFactory.openSession

    try {

      val result = dbCommands(session)

      beforeCommit match {
        case Some(callback) => callback(session)
        case None           => // ignore
      }

      session.flush

      afterCommit match {
        case Some(callback) => callback()
        case None           => // ignore
      }

      result
    } catch {
      case ex: Exception => ex.getCause match {
        case recov: java.sql.SQLRecoverableException =>
          log.warn("Retrying failed operation: %s".format(recov.getMessage))
          try {
            val session = sessionFactory.openSession
            val result = dbCommands(session)
            session.flush

            result
          }
        case _ =>
          throw ex
      }
      case exc =>
        throw exc
    } finally {
      session.close
    }
  }

  def executeOnSession(work: (Connection => Unit)) {
    withSession(session => {
      session.doWork(new Work {
        def execute(connection: Connection) {
          work(connection)
        }
      })
    })
  }
  
  def executeStatements(statements: Seq[String], treatErrorsAsFatal: Boolean) {
    executeOnSession(connection => {
      val stmt = connection.createStatement
      statements foreach  { stmtText => {
        try {
          stmt.execute(stmtText)
        } catch {
          case ex =>
            println("Failed to execute prep stmt: %s".format(stmtText))
            if (treatErrorsAsFatal) throw ex
        }
      }}
      stmt.close
    })
  }
}

object SessionHelper {

  implicit def sessionFactoryToSessionHelper(sessionFactory:SessionFactory) =
    new SessionHelper(sessionFactory)
}
