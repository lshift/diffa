package net.lshift.diffa.schema.cleaner

import net.lshift.diffa.schema.environment.DatabaseEnvironment
import net.lshift.diffa.schema.hibernate.SessionHelper.sessionFactoryToSessionHelper
import org.hibernate.jdbc.Work
import java.sql.{SQLException, Connection}
import org.hibernate.{HibernateException, SessionFactory}
import org.slf4j.LoggerFactory
import org.joda.time.DateTime
import org.joda.time.Interval

/**
 * Implements the SchemaCleaner for Oracle databases.
 */
object OracleSchemaCleaner extends SchemaCleaner {
  val log = LoggerFactory.getLogger(getClass)

  override def drop(sysUserEnvironment: DatabaseEnvironment, appEnvironment: DatabaseEnvironment) {
  }

  override def clean(sysUserEnvironment: DatabaseEnvironment, appEnvironment: DatabaseEnvironment) {
    val schemaName = appEnvironment.username
    val password = appEnvironment.password
    val dbaConfig = sysUserEnvironment.getHibernateConfigurationWithoutMappingResources
    val dbaSessionFactory = dbaConfig.buildSessionFactory

    if (userExists(sessionFactory = dbaSessionFactory, username = schemaName)) {
      dropAllObjects(sessionFactory = dbaSessionFactory, schemaName = schemaName)
    } else {
      createSchemaWithPrivileges(dbaSessionFactory, schemaName, password)
      dbaSessionFactory.close()
      waitForSchemaCreation(appEnvironment, pollIntervalMs = 100L, timeoutMs = 10000L)
    }
  }

  def dropAllObjects(sessionFactory: SessionFactory, schemaName: String) {
    val tableNamesQuery = "select table_name from all_tables where owner = upper(?)"
    val dropTableStmtTemplate = "drop table %s.%s cascade constraints purge"

    sessionFactory.executeOnSession(connection => {
      val qryStmt = connection.prepareStatement(tableNamesQuery)
      qryStmt.setString(1, schemaName)
      val rs = qryStmt.executeQuery()
      while (rs.next()) {
        val dropStmt = connection.createStatement()
        dropStmt.execute(dropTableStmtTemplate.format(schemaName, rs.getString(1)))
      }
    })
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
            case ex: SQLException =>
              log.error("Failed to execute prepared statement: %s".format(stmtText))
              throw ex
          }
        }
      }
      stmt.close()
    })
  }

  private def waitForSchemaCreation(newDbEnviron: DatabaseEnvironment, pollIntervalMs: Long, timeoutMs: Long) {
    val config = newDbEnviron.getHibernateConfigurationWithoutMappingResources
    val sessionFactory = config.buildSessionFactory
    var connected = false
    var failCount = 0
    val failThreshold = timeoutMs / pollIntervalMs

    while (!connected) {
      try {
        sessionFactory.openSession
        connected = true
      } catch {
        case ex: HibernateException =>
          Thread.sleep(pollIntervalMs)
          failCount += 1
          if (failCount >= failThreshold) {
            log.error("Timed out waiting for schema creation. Waited %lms".format(timeoutMs))
            throw ex
          }
      }
    }

    sessionFactory.close()
  }
}
