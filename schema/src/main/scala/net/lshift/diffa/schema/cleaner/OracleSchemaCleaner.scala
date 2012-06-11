package net.lshift.diffa.schema.cleaner

import net.lshift.diffa.schema.environment.DatabaseEnvironment
import net.lshift.diffa.schema.hibernate.SessionHelper.sessionFactoryToSessionHelper
import org.hibernate.jdbc.Work
import java.sql.Connection
import org.hibernate.SessionFactory
import org.slf4j.LoggerFactory

/**
 * Implements the SchemaCleaner for Oracle databases.
 */
object OracleSchemaCleaner extends SchemaCleaner {
  val log = LoggerFactory.getLogger(getClass)

  override def drop(sysUserEnvironment: DatabaseEnvironment, appEnvironment: DatabaseEnvironment) {
    val schemaName = appEnvironment.username
    val dbaConfig = sysUserEnvironment.getHibernateConfiguration
    val dbaSessionFactory = dbaConfig.buildSessionFactory

    conditionalDrop(dbaSessionFactory, schemaName)
  }

  override def clean(sysUserEnvironment: DatabaseEnvironment, appEnvironment: DatabaseEnvironment) {
    val schemaName = appEnvironment.username
    val password = appEnvironment.password
    val dbaConfig = sysUserEnvironment.getHibernateConfiguration
    val dbaSessionFactory = dbaConfig.buildSessionFactory

    conditionalDrop(dbaSessionFactory, schemaName)

    createSchemaWithPrivileges(dbaSessionFactory, schemaName, password)

    dbaSessionFactory.close()
    waitForSchemaCreation(appEnvironment, pollIntervalMs = 100L, timeoutMs = 10000L)
  }

  private def conditionalDrop(dbaSessionFactory: SessionFactory, schemaName: String) {
    if (userExists(dbaSessionFactory, schemaName)) {
      disconnectActiveSessions(schemaName, dbaSessionFactory)
      dropSchema(dbaSessionFactory, schemaName)
    }
  }

  private def disconnectActiveSessions(username: String, sessionFactory: SessionFactory) {
    sessionFactory.withSession(session => {
      session.doWork(new Work {
        def execute(connection: Connection) {
          val getSessionInfo = "select sid, serial# from v$session where username = '%s'".format(username.toUpperCase)
          val sessionInfoStmt = connection.prepareStatement(getSessionInfo)
          val rs = sessionInfoStmt.executeQuery
          var sessions: List[(Int, Int)] = Nil
          while (rs.next()) {
            sessions = (rs.getInt("sid"), rs.getInt("serial#")) :: sessions
          }

          sessions foreach {
            sessionInfoPair: (Int, Int) =>
              val (sid, serialnum) = sessionInfoPair
              val disconnectUser = "alter system disconnect session '%d,%d' immediate".format(sid, serialnum)
              val disconnectStmt = connection.prepareStatement(disconnectUser)
              try {
                disconnectStmt.execute
                log.debug("Disconnected user: %s".format(disconnectUser))
              } catch {
                case ex =>
                  log.info("Failed to disconnect session [%d/%d]".format(sid, serialnum))
              }
          }
        }
      })
    })
  }

  private def dropSchema(sessionFactory: SessionFactory, schemaName: String) {
    val dropSchemaStatement = "drop user %s cascade".format(schemaName)
    val recreateAttemptThreshold = 3
    val retryIntervalMs = 1000L
    var recreateAttemptCount = 0
    var userExists = true

    // Disconnecting a user can succeed, but the effect may not be immediate.  Retry this a few times.
    while (userExists && recreateAttemptCount < recreateAttemptThreshold) {
      try {
        sessionFactory.executeOnSession(connection => {
          val stmt = connection.createStatement
          (dropSchemaStatement :: Nil) foreach {
            stmtText => {
              try {
                stmt.execute(stmtText)
                userExists = false
              } catch {
                case ex =>
                  log.debug("Failed to execute prepared statement: %s".format(stmtText))
                  throw ex
              }
            }
          }
          stmt.close()
        })
      } catch {
        case ex: Exception =>
          recreateAttemptCount += 1
          Thread.sleep(retryIntervalMs)
          if (recreateAttemptCount >= recreateAttemptThreshold) {
            log.info("Failed to drop user [%s]".format(schemaName))
            log.info(ex.getMessage)
            throw ex
          }
      }
    }
  }

  private def userExists(sessionFactory: SessionFactory, username: String): Boolean = {
    val userExistsQuery = "select username from dba_users where upper(username) = upper('%s')".format(username)
    var exists = false

    sessionFactory.executeOnSession(connection => {
      val stmt = connection.createStatement()
      val rs = stmt.executeQuery(userExistsQuery)
      if (rs.next()) {
        log.info("User %s exists".format(username))
        exists = true
      }
    })

    exists
  }

  private def createSchemaWithPrivileges(sessionFactory: SessionFactory, schemaName: String, password: String) {
    val createSchemaStatement = "create user %s identified by %s".format(schemaName, password)
    val grantPrivilegesStatement = "grant create session, dba to %s".format(schemaName)

    sessionFactory.executeOnSession(connection => {
      val stmt = connection.createStatement
      (createSchemaStatement :: grantPrivilegesStatement :: Nil) foreach {
        stmtText => {
          try {
            stmt.execute(stmtText)
            log.debug("Executed: %s".format(stmtText))
          } catch {
            case ex =>
              log.info("Failed to execute prepared statement: %s".format(stmtText))
              throw ex
          }
        }
      }
      stmt.close()
    })
  }

  private def waitForSchemaCreation(newDbEnviron: DatabaseEnvironment, pollIntervalMs: Long, timeoutMs: Long) {
    val config = newDbEnviron.getHibernateConfiguration
    val sessionFactory = config.buildSessionFactory
    var connected = false
    var failCount = 0
    val failThreshold = timeoutMs / pollIntervalMs

    while (!connected) {
      try {
        sessionFactory.openSession
        connected = true
      } catch {
        case ex =>
          Thread.sleep(pollIntervalMs)
          failCount += 1
          if (failCount >= failThreshold) {
            log.info("Timed out waiting for schema creation. Waited %lms".format(timeoutMs))
            throw ex
          }
      }
    }
    
    sessionFactory.close()
  }
}
