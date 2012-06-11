package net.lshift.diffa.schema.environment

import net.lshift.hibernate.migrations.dialects.OracleDialectExtension

/**
 * These database environments are intended for use in testing.
 */
object TestDatabaseEnvironments {
  def adminEnvironment: DatabaseEnvironment = AdminEnvironment
  def hsqldbEnvironment(path: String): DatabaseEnvironment = new HsqldbEnvironment(path)
  def uniqueEnvironment(path: String): DatabaseEnvironment = DatabaseEnvironment.customEnvironment(path)
}

class UniqueEnvironment(path: String) extends DatabaseEnvironment(path) {
  override def username = {
    if (hibernateDialect.toUpperCase.contains((new OracleDialectExtension).getDialectName.toUpperCase))
      makeUniqueUsername(super.username)
    else
      super.username
  }

  private def makeUniqueUsername(username: String): String = {
    val scale = 100000000L
    val suffix = math.round(scale * scala.math.random)
    "%s_%d".format(username, suffix)
  }
}

/**
 * This is intended for testing purposes only.
 */
object AdminEnvironment extends DatabaseEnvironment("") {
  override def username = System.getProperty("diffa.jdbc.sys.username", "sys")
  override def password = System.getProperty("diffa.jdbc.sys.password", "")
  override def url = {
    val _url = System.getProperty("diffa.jdbc.sys.url")
    if (_url == null)
      System.getProperty("diffa.jdbc.url", "")
    else
      _url
  }
}

/**
 * This is intended for testing purposes only.
 */
class HsqldbEnvironment(path: String) extends DatabaseEnvironment(path) {
  override def url = substitutableURL(path, """jdbc:hsqldb:mem:%s""")
  override def hibernateDialect = "org.hibernate.dialect.HSQLDialect"
  override def driver = "org.hsqldb.jdbc.JDBCDriver"
  override def username = System.getProperty("diffa.jdbc.username", "SA")
  override def password = System.getProperty("diffa.jdbc.password", "")
}
