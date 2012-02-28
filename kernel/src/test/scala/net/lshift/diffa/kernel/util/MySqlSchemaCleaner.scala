package net.lshift.diffa.kernel.util

import net.lshift.diffa.kernel.util.SessionHelper.sessionFactoryToSessionHelper

/**
 * Implements SchemaCleaner for MySQL databases.
 */
object MySqlSchemaCleaner extends SchemaCleaner {
  override def clean(sysEnvironment: DatabaseEnvironment, appEnvironment: DatabaseEnvironment) {
    val configuration = sysEnvironment.getHibernateConfiguration
    val sessionFactory = configuration.buildSessionFactory

    val dropDbStatement = """drop schema %s""".format(appEnvironment.dbName)
    val createDbStatement = """create schema %s""".format(appEnvironment.dbName)
    val statements = (dropDbStatement :: createDbStatement :: Nil)

    sessionFactory.executeOnSession(connection => {
      val stmt = connection.createStatement
      statements foreach {
        stmtText => {
          try {
            stmt.execute(stmtText)
          } catch {
            case ex =>
              println("Failed to execute prepared statement: %s".format(stmtText))
          }
        }
      }
      stmt.close
    })
  }
}
