package net.lshift.diffa.kernel.differencing

import org.hibernate.SessionFactory
import net.lshift.diffa.kernel.util.SessionHelper.sessionFactoryToSessionHelper
import org.hibernate.dialect.Dialect
import net.lshift.hibernate.migrations.dialects.{OracleDialectExtension, DialectExtensionSelector}


object IndexRebuilder {
  def dialectSpecificRebuilder(dialect: Dialect) = DialectExtensionSelector.select(dialect) match {
    case ext: OracleDialectExtension => new OracleIndexRebuilder
    case _ => new NullIndexRebuilder
  }
}

/**
 * Each database has it's own mechanics for detecting when indices need rebuilding and also how to rebuild them.
 * This interface provides a means to rebuild indexes by relying on dialect detection to choose the right strategy.
 */
trait IndexRebuilder {
  def rebuild(sessionFactory: SessionFactory): Unit
}

class NullIndexRebuilder extends IndexRebuilder {
  def rebuild(sessionFactory: SessionFactory) {}
}

class OracleIndexRebuilder extends IndexRebuilder {
  def rebuild(sessionFactory: SessionFactory) {
    val unusableIndexesQuery: String = "select index_name from user_indexes where status = 'UNUSABLE'"
    val alterIndexSql = "alter index %s rebuild"

    sessionFactory.executeOnSession(connection => {
      var indexNames: List[String] = Nil
      val stmt = connection.prepareStatement(unusableIndexesQuery)
      val rs = stmt.executeQuery
      while (rs.next) {
        indexNames = rs.getString("index_name") :: indexNames
      }

      indexNames.foreach(indexName => try {
        connection.prepareCall(alterIndexSql.format(indexName)).execute
      } catch {
        case ex: Exception =>
          println("Failed to rebuild index [%s]".format(indexName))
      })
    })
  }
}