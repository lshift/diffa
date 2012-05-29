package net.lshift.diffa.kernel.differencing

import net.lshift.diffa.kernel.util.db.SessionHelper.sessionFactoryToSessionHelper
import org.hibernate.dialect.Dialect
import net.lshift.hibernate.migrations.dialects.{OracleDialectExtension, DialectExtensionSelector}
import org.slf4j.LoggerFactory
import org.hibernate.Session
import java.sql.Connection
import org.hibernate.jdbc.Work


object IndexRebuilder {
  def dialectSpecificRebuilder(dialect: Dialect) = DialectExtensionSelector.select(dialect) match {
    case ext: OracleDialectExtension => new OracleIndexRebuilder
    case _ => new NullIndexRebuilder
  }
}

/**
 * Each database has its own mechanics for detecting when indices need rebuilding and also how to rebuild them.
 * This interface provides a means to rebuild indexes by relying on dialect detection to choose the right strategy.
 */
trait IndexRebuilder {
  def rebuild(session: Session, tableName:String): Unit
}

class NullIndexRebuilder extends IndexRebuilder {
  def rebuild(session: Session, tableName:String) {}
}

class OracleIndexRebuilder extends IndexRebuilder {
  val log = LoggerFactory.getLogger(getClass)

  def rebuild(session: Session, tableName:String) {
    val unusableIndexesQuery = "select index_name from user_indexes where status = 'UNUSABLE' and table_name = ?"
    val alterIndexSql = "alter index %s rebuild"

    session.doWork(new Work {
      def execute(connection: Connection) {
        var indexNames: List[String] = Nil
        val stmt = connection.prepareStatement(unusableIndexesQuery)
        stmt.setString(1, tableName.toUpperCase) // N.B. Oracle requires table names to be upper-case
        val rs = stmt.executeQuery
        while (rs.next) {
          indexNames = rs.getString("index_name") :: indexNames
        }

        indexNames.foreach(indexName => try {
          connection.prepareCall(alterIndexSql.format(indexName)).execute
        } catch {
          case ex: Exception =>
            log.error("Failed to rebuild index [%s]".format(indexName), ex)
          // TODO not sure whether a rebuild failure show be propgated to the caller or just logged
        })
      }
    })
  }
}