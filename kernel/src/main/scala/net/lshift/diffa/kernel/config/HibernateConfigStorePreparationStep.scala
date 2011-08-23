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

import org.hibernate.SessionFactory
import org.hibernate.jdbc.Work
import org.hibernate.dialect.Dialect
import org.slf4j.{LoggerFactory, Logger}
import net.lshift.diffa.kernel.util.SessionHelper._
import org.hibernate.mapping.{Column, Table, PrimaryKey, ForeignKey}
import org.hibernate.tool.hbm2ddl.{DatabaseMetadata, SchemaExport}
import org.hibernate.cfg.{Environment, Configuration}
import java.sql.{Types, Connection}
import net.lshift.diffa.kernel.differencing.VersionCorrelationStore
import net.lshift.hibernate.migrations.MigrationBuilder
import scala.collection.JavaConversions._

/**
 * Preparation step to ensure that the configuration for the Hibernate Config Store is in place.
 */
class HibernateConfigStorePreparationStep
    extends HibernatePreparationStep {

  val log:Logger = LoggerFactory.getLogger(getClass)

  // The migration steps necessary to bring a hibernate configuration up-to-date. Note that these steps should be
  // in strictly ascending order.
  val migrationSteps:Seq[HibernateMigrationStep] = Seq(
    RemoveGroupsMigrationStep,
    AddSchemaVersionMigrationStep,
    AddDomainsMigrationStep,
    AddMaxGranularityMigrationStep,
    AddPersistentDiffsMigrationStep
  )

  def prepare(sf: SessionFactory, config: Configuration) {

    detectVersion(sf, config) match {
      case None          => {
        (new SchemaExport(config)).create(false, true)

        // Since we are creating a fresh schema, we need to populate the schema version as well as inserting the default domain
        val freshMigration = new MigrationBuilder(config)
        freshMigration.insert("domains").
          values(Map("name" -> Domain.DEFAULT_DOMAIN.name))
        freshMigration.insert("system_config_options").
          values(Map("opt_key" -> HibernatePreparationUtils.correlationStoreSchemaKey, "opt_val" -> HibernatePreparationUtils.correlationStoreVersion))

        // Also need to add foreign key constraint from diffs.pair to pair.pair_key
        AddPersistentDiffsMigrationStep.addForeignKeyConstraintForPairColumnOnDiffsTables(freshMigration)

        sf.withSession(s => {
          s.doWork(new Work() {
            def execute(connection: Connection) {
              val stmt = connection.createStatement()

              try {
                // Run the schema version migration step to get our schema version in place
                AddSchemaVersionMigrationStep.migrate(config, connection, migrationSteps.last.versionId)

                // Run our other first start migration steps
                freshMigration.apply(connection)
              } catch {
                case ex =>
                  println("Failed to prepare the schema_version table")
                  throw ex      // Higher level code will log the exception
              }

              stmt.close()
            }
          })
        })

        log.info("Applied initial database schema")
      }
      case Some(version) => {
        // Upgrade the schema if the current version is older than the last known migration step
        sf.withSession(s => {

          log.info("Current database version is " + version)

          val firstStepIdx = migrationSteps.indexWhere(step => step.versionId > version)
          if (firstStepIdx != -1) {
            s.doWork(new Work {
              def execute(connection: Connection) {
                migrationSteps.slice(firstStepIdx, migrationSteps.length).foreach(step => {
                  step.migrate(config, connection)
                  log.info("Upgraded database to version " + step.versionId)
                  if (step.versionId > 1) {
                    s.createSQLQuery(HibernatePreparationUtils.schemaVersionUpdateStatement(step.versionId)).executeUpdate()
                    s.flush
                  }
                })
              }
            })
          }
        })
      }
    }
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
          val dbMetadata = new DatabaseMetadata(connection, Dialect.getDialect(props))

          val defaultCatalog = props.getProperty(Environment.DEFAULT_CATALOG)
          val defaultSchema = props.getProperty(Environment.DEFAULT_SCHEMA)

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
      Some(sf.withSession(_.createSQLQuery("select max(version) from schema_version").uniqueResult().asInstanceOf[Int]))
    }
    // The schema_version table doesn't exist, so look at the config_options table
    else if (tableExists(sf, config, "config_options") ) {
      //Prior to version 2 of the database, the schema version was kept in the ConfigOptions table
      val query = "select opt_val from config_options where opt_key = 'configStore.schemaVersion'"
      Some(sf.withSession(_.createSQLQuery(query).uniqueResult().asInstanceOf[String].toInt))
    }
    else {
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
   * Requests that the step perform it's necessary migration.
   */
  def migrate(config:Configuration, connection:Connection)
}

object RemoveGroupsMigrationStep extends HibernateMigrationStep {
  def versionId = 1
  def migrate(config: Configuration, connection: Connection) {
    val migration = new MigrationBuilder(config)
    migration.alterTable("pair").
      dropColumn("NAME")
    migration.dropTable("pair_group")

    migration.apply(connection)
  }
}

object AddSchemaVersionMigrationStep extends HibernateMigrationStep {
  def versionId = 2
  def migrate(config: Configuration, connection: Connection) {
    migrate(config, connection, versionId)
  }
  def migrate(config: Configuration, connection: Connection, targetVersionId:Int) {
    val migration = new MigrationBuilder(config)
    migration.createTable("schema_version").
        column("version", Types.INTEGER, false).
        pk("version")
    migration.insert("schema_version").
        values(Map("version" -> new java.lang.Integer(targetVersionId)))
    
    migration.apply(connection)
  }

  def migration(config:Configuration) = {

  }
}
object AddDomainsMigrationStep extends HibernateMigrationStep {
  def versionId = 3
  def migrate(config: Configuration, connection: Connection) {
    val migration = new MigrationBuilder(config)

    // Add our new tables (domains and system config options)
    migration.createTable("domains").
      column("name", Types.VARCHAR, 255, false).
      pk("name")
    migration.createTable("system_config_options").
      column("opt_key", Types.VARCHAR, 255, false).
      column("opt_val", Types.VARCHAR, 255, false).
      pk("opt_key")

    // Make sure the default domain is in the DB
    migration.insert("domains").values(Map("name" -> Domain.DEFAULT_DOMAIN.name))

    // create table members (domain_name varchar(255) not null, user_name varchar(255) not null, primary key (domain_name, user_name));
    migration.createTable("members").
      column("domain_name", Types.VARCHAR, 255, false).
      column("user_name", Types.VARCHAR, 255, false).
      pk("user_name", "domain_name")
    migration.alterTable("members").
      addForeignKey("FK388EC9191902E93E", "domain_name", "domains", "name").
      addForeignKey("FK388EC9195A11FA9E", "user_name", "users", "name")

    // alter table config_options drop column is_internal
    migration.alterTable("config_options").
        dropColumn("is_internal")

    // Add domain column to config_option, endpoint and pair
    migration.alterTable("config_options").
      addColumn("domain", Types.VARCHAR, 255, false, Domain.DEFAULT_DOMAIN.name).
      addForeignKey("FK80C74EA1C3C204DC", "domain", "domains", "name")
    migration.alterTable("endpoint").
      addColumn("domain", Types.VARCHAR, 255, false, Domain.DEFAULT_DOMAIN.name).
      addForeignKey("FK67C71D95C3C204DC", "domain", "domains", "name")
    migration.alterTable("pair").
      addColumn("domain", Types.VARCHAR, 255, false, Domain.DEFAULT_DOMAIN.name).
      addForeignKey("FK3462DAC3C204DC", "domain", "domains", "name")

    // Upgrade the schema version for the correlation store
    migration.sql(HibernatePreparationUtils.correlationSchemaVersionUpdateStatement("1"))

    //alter table escalations add constraint FK2B3C687E7D35B6A8 foreign key (pair_key) references pair;
    migration.alterTable("escalations").
      addForeignKey("FK2B3C687E7D35B6A8", "pair_key", "pair", "name")

    //alter table repair_actions add constraint FKF6BE324B7D35B6A8 foreign key (pair_key) references pair;
    migration.alterTable("repair_actions").
      addForeignKey("FKF6BE324B7D35B6A8", "pair_key", "pair", "name")
    
    migration.apply(connection)
  }
}
object AddMaxGranularityMigrationStep extends HibernateMigrationStep {
  def versionId = 4
  def migrate(config: Configuration, connection: Connection) {
    val migration = new MigrationBuilder(config)

    migration.alterTable("range_category_descriptor").
      addColumn("max_granularity", Types.VARCHAR, 255, true, null)

    migration.apply(connection)
  }
}
object AddPersistentDiffsMigrationStep extends HibernateMigrationStep {
  def versionId = 5
  def migrate(config: Configuration, connection: Connection) {
    val migration = new MigrationBuilder(config)

    migration.createTable("diffs").
      column("seq_id", Types.INTEGER, false).
      column("domain", Types.VARCHAR, 255, false).
      column("pair", Types.VARCHAR, 255, false).
      column("entity_id", Types.VARCHAR, 255, false).
      column("is_match", Types.SMALLINT, false).
      column("detected_at", Types.TIMESTAMP, false).
      column("last_seen", Types.TIMESTAMP, false).
      column("upstream_vsn", Types.VARCHAR, 255, true).
      column("downstream_vsn", Types.VARCHAR, 255, true).
      pk("seq_id").
      withIdentityCol()

    migration.createTable("pending_diffs").
      column("oid", Types.INTEGER, false).
      column("domain", Types.VARCHAR, 255, false).
      column("pair", Types.VARCHAR, 255, false).
      column("entity_id", Types.VARCHAR, 255, false).
      column("detected_at", Types.TIMESTAMP, false).
      column("last_seen", Types.TIMESTAMP, false).
      column("upstream_vsn", Types.VARCHAR, 255, true).
      column("downstream_vsn", Types.VARCHAR, 255, true).
      pk("oid").
      withIdentityCol()

    addForeignKeyConstraintForPairColumnOnDiffsTables(migration)

    migration.createIndex("diff_last_seen", "diffs", "last_seen")
    migration.createIndex("diff_detection", "diffs", "detected_at")
    migration.createIndex("rdiff_is_matched", "diffs", "is_match")
    migration.createIndex("rdiff_domain_idx", "diffs", "entity_id", "domain", "pair")
    migration.createIndex("pdiff_domain_idx", "pending_diffs", "entity_id", "domain", "pair")

    migration.apply(connection)
  }

  def addForeignKeyConstraintForPairColumnOnDiffsTables(migration: MigrationBuilder) {
    // alter table diffs add constraint FK5AA9592F53F69C16 foreign key (pair) references pair (pair_key);
    migration.alterTable("diffs")
      .addForeignKey("FK5AA9592F53F69C16", "pair", "pair", "pair_key")

    migration.alterTable("pending_diffs")
      .addForeignKey("FK75E457E44AD37D84", "pair", "pair", "pair_key")
  }
}