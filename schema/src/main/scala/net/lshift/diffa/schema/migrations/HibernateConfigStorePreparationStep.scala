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

package net.lshift.diffa.schema.migrations

import org.hibernate.jdbc.Work
import org.slf4j.{LoggerFactory, Logger}
import org.hibernate.tool.hbm2ddl.DatabaseMetadata
import org.hibernate.cfg.{Environment, Configuration}
import java.sql.{Types, Connection}

import net.lshift.diffa.schema.migrations.steps._
import net.lshift.diffa.schema.hibernate.SessionHelper.sessionFactoryToSessionHelper
import net.lshift.hibernate.migrations.MigrationBuilder

import scala.collection.JavaConversions._
import org.hibernate.`type`.IntegerType
import org.hibernate.dialect.Dialect
import net.lshift.hibernate.migrations.dialects.DialectExtensionSelector
import org.hibernate.{SessionFactory}

/**
 * Preparation step to ensure that the configuration for the Hibernate Config Store is in place.
 */
class HibernateConfigStorePreparationStep
    extends HibernatePreparationStep {

  val log:Logger = LoggerFactory.getLogger(getClass)

  val migrationSteps = HibernateConfigStorePreparationStep.migrationSteps

  def prepare(sf: SessionFactory, config: Configuration) : Unit = prepare(sf, config, false)

  /**
   * Install/upgrade the schema to the latest version.  In an empty schema, this will
   * install version 0, then upgrade sequentially through each intermediate version
   * til the latest, whereas for a schema at version k &lt; L, where L is the latest
   * version, the schema will be updated to k+1, k+2, ..., L.
   */
  def prepare(sf: SessionFactory, config: Configuration, applyVerification:Boolean) {
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

              if (applyVerification && step.isInstanceOf[VerifiedMigrationStep]) {

                val verifiedStep = step.asInstanceOf[VerifiedMigrationStep]
                val verification = verifiedStep.applyVerification(config)

                verification.apply(connection)


                log.info("Applied verification to version %s (%s)".format(step.versionId, step.name))
              }

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

  /**
   * Generates a statement to update the schema_version table
   */
  def schemaVersionUpdateStatement(version:Int) =  "update schema_version set version = %s".format(version)
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
    Step0024,
    Step0025,
    Step0026,
    Step0027,
    Step0028,
    Step0029,
    Step0030,
    Step0031,
    Step0032,
    Step0033,
    Step0034,
    Step0035,
    Step0036,
    Step0037,
    Step0038,
    Step0039,
    Step0040
  )
}