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

package net.lshift.diffa.kernel.config

import net.lshift.diffa.kernel.config.migrations._
import org.hibernate.SessionFactory
import org.hibernate.jdbc.Work
import org.slf4j.{LoggerFactory, Logger}
import net.lshift.diffa.kernel.util.SessionHelper._
import org.hibernate.tool.hbm2ddl.DatabaseMetadata
import org.hibernate.cfg.{Environment, Configuration}
import java.sql.{Types, Connection}
import net.lshift.diffa.kernel.differencing.VersionCorrelationStore

import net.lshift.hibernate.migrations.MigrationBuilder

import scala.collection.JavaConversions._
import org.hibernate.`type`.IntegerType
import org.hibernate.dialect.Dialect
import net.lshift.hibernate.migrations.dialects.DialectExtensionSelector

/**
 * Preparation step to ensure that the configuration for the Hibernate Config Store is in place.
 */
class HibernateConfigStorePreparationStep
    extends HibernatePreparationStep {

  val log:Logger = LoggerFactory.getLogger(getClass)

  val migrationSteps = HibernateConfigStorePreparationStep.migrationSteps

  /**
   * Install/upgrade the schema to the latest version.  In an empty schema, this will
   * install version 0, then upgrade sequentially through each intermediate version
   * til the latest, whereas for a schema at version k &lt; L, where L is the latest
   * version, the schema will be updated to k+1, k+2, ..., L.
   */
  def prepare(sf: SessionFactory, config: Configuration) {
    config.getProperties filterKeys(p => p.startsWith("hibernate")) foreach {
      prop => log.debug("Preparing database [%s: %s]".format(prop._1, prop._2))
    }

    val version = detectVersion(sf, config)
    val nextVersion = version match {
      case None =>
        log.info("Empty schema detected; version 0 will initially be installed, followed by upgrade to latest")
        0
      case Some(vsn) =>
        log.info("Current schema version is %d".format(vsn))
        vsn + 1
    }

    val migrations = migrationSteps.filter(s => s.versionId >= nextVersion)

    sf.withSession(s => {
      s.doWork(new Work {
        def execute(connection: Connection) {
          migrations.foreach(step => {
            val migration = step.createMigration(config)

            try {
              migration.apply(connection)
              if (step.versionId > 1) {

                // Make sure that this really does get written to the underlying DB

                val tx = s.beginTransaction()
                try {
                  s.createSQLQuery(HibernatePreparationUtils.schemaVersionUpdateStatement(step.versionId)).executeUpdate()
                  tx.commit()
                }
                catch {
                  case x => {
                    tx.rollback()
                    throw x
                  }
                }
              }
              log.info("Upgraded database to version %s (%s)".format(step.versionId, step.name))
            } catch {
              case ex =>
                println("Failed to prepare the database - attempted to execute the following statements for step " + step.versionId + ":")
                println("_" * 80)
                println()
                migration.getStatements.foreach(println(_))
                println("_" * 80)
                println()
                throw ex      // Higher level code will log the exception
            }
          })
        }
      })
    })
  }

  /**
   * Determines whether the given table exists in the underlying DB
   */
  def tableExists(sf: SessionFactory, config:Configuration, tableName:String) : Boolean = {
    var hasTable:Boolean = false

    sf.withSession(s => {
      s.doWork(new Work {
        def execute(connection: Connection) = {
          val props = config.getProperties
          val dialect = Dialect.getDialect(props)
          val dialectExtension = DialectExtensionSelector.select(dialect)

          val dbMetadata = new DatabaseMetadata(connection, dialect)

          val defaultCatalog = props.getProperty(Environment.DEFAULT_CATALOG)
          val defaultSchema = props.getProperty(dialectExtension.schemaPropertyName)

          hasTable = (dbMetadata.getTableMetadata(tableName, defaultSchema, defaultCatalog, false) != null)
        }
      })
    })

    hasTable
  }

  /**
   * Detects the version of the schema using native SQL
   */
  def detectVersion(sf: SessionFactory, config:Configuration) : Option[Int] = {
    // Attempt to read the schema_version table, if it exists
    if (tableExists(sf, config, "schema_version") ) {
      Some(sf.withSession(_.createSQLQuery("select max(version) as max_version from schema_version")
                           .addScalar("max_version", IntegerType.INSTANCE)
                           .uniqueResult().asInstanceOf[Int]))
    } else {
      // No known table was available to read a schema version
      None
    }
  }
}

/**
 * A set of helper functions to build portable SQL strings
 */
object HibernatePreparationUtils {

  val correlationStoreVersion = VersionCorrelationStore.currentSchemaVersion.toString
  val correlationStoreSchemaKey = VersionCorrelationStore.schemaVersionKey

  /**
   * Generates a statement to update the schema version for the correlation store
   */
  def correlationSchemaVersionUpdateStatement(version:String) =
    "update config_options set opt_val = '%s' where opt_key = '%s' and domain = 'root'".format(version, correlationStoreSchemaKey)

  /**
   * Generates a statement to update the schema_version table
   */
  def schemaVersionUpdateStatement(version:Int) =  "update schema_version set version = %s".format(version)
}

abstract class HibernateMigrationStep {

  /**
   * The version that this step gets the database to.
   */
  def versionId:Int

  /**
   * The name of this migration step
   */
  def name:String

  /**
   * Requests that the step create migration builder for doing it's migration.
   */
  def createMigration(config:Configuration):MigrationBuilder
}


/**
 * One-off definition of the partition information table.
 */
object DefinePartitionInformationTable {

  def defineTable(migration:MigrationBuilder) {    
    migration.createTable("partition_information").
        column("table_name", Types.VARCHAR, 50, false).
        column("version", Types.INTEGER, false).
        pk("table_name", "version")
  }

  def applyPartitionVersion(migration:MigrationBuilder, table:String, versionId:Int) {
    migration.insert("partition_information").
                    values(Map("version" -> new java.lang.Integer(versionId),
                               "table_name" -> table))
  }

  def preventPartitionReapplication(migration:MigrationBuilder, table:String, versionId:Int) {
    migration.addPrecondition("partition_information",
                              "where table_name = '%s' and version = %s".format(table, versionId), 0)
  }
}

object HibernateConfigStorePreparationStep {
  /**
   * This is the ordered list of migration steps to apply from any given schema version to bring a hibernate configuration up-to-date.
   * Note that these steps should be executed in strictly ascending order.
   */
  val migrationSteps = Seq(
    Step0022,
    Step0023,
    Step0024
  )
}