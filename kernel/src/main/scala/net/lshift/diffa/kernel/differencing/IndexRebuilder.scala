package net.lshift.diffa.kernel.differencing

import org.hibernate.dialect.Dialect
import net.lshift.hibernate.migrations.dialects.{OracleDialectExtension, DialectExtensionSelector}
import org.slf4j.LoggerFactory
import org.jooq.impl.Factory
import scala.collection.JavaConversions._


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
  def rebuild(t:Factory, tableName:String): Unit
}

class NullIndexRebuilder extends IndexRebuilder {
  def rebuild(t:Factory, tableName:String) {}
}

class OracleIndexRebuilder extends IndexRebuilder {
  val log = LoggerFactory.getLogger(getClass)

  val INDEX_NAME = Factory.field("index_name")

  def rebuild(t:Factory, tableName:String) {



    val indexNames = t.select(INDEX_NAME).
                       from("user_indexes").
                       where("status = 'UNUSABLE'").
                         and("table_name = ?", tableName.toUpperCase). // N.B. Oracle requires table names to be upper-case
                       fetch().
                       iterator().
                       map(r => r.getValueAsString(INDEX_NAME))

    indexNames.foreach(index => {
      try {
        t.execute("alter index " + index + " rebuild")
      }
      catch {
        case ex: Exception =>
          log.error("Failed to rebuild index [%s]".format(index), ex)
          // TODO not sure whether a rebuild failure show be propgated to the caller or just logged
      }
    })

  }
}