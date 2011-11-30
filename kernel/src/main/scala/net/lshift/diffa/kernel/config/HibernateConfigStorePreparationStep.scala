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
import org.hibernate.tool.hbm2ddl.{DatabaseMetadata, SchemaExport}
import org.hibernate.cfg.{Environment, Configuration}
import java.sql.{Types, Connection}
import net.lshift.diffa.kernel.differencing.VersionCorrelationStore
import scala.collection.JavaConversions._
import org.hibernate.`type`.IntegerType
import net.lshift.hibernate.migrations.MigrationBuilder

/**
 * Preparation step to ensure that the configuration for the Hibernate Config Store is in place.
 */
class HibernateConfigStorePreparationStep
    extends HibernatePreparationStep {

  val log:Logger = LoggerFactory.getLogger(getClass)

  val migrationSteps = HibernateConfigStorePreparationStep.migrationSteps

  def prepare(sf: SessionFactory, config: Configuration) {
    val migrations = detectVersion(sf, config) match {
      case None          => {
        log.info("Going to apply initial database schema")

        val freshStep = new FreshMigrationStep(migrationSteps.last.versionId)
        freshStep.initialSetup(config)
        Seq(freshStep)
      }
      case Some(version) => {
        log.info("Current database version is " + version)
        val firstStepIdx = migrationSteps.indexWhere(step => step.versionId > version)
        if (firstStepIdx != -1) {
          migrationSteps.slice(firstStepIdx, migrationSteps.length)
        } else {
          Seq()
        }
      }
    }

    sf.withSession(s => {
      s.doWork(new Work {
        def execute(connection: Connection) {
          migrations.foreach(step => {
            val migration = step.createMigration(config)

            try {
              migration.apply(connection)
              if (step.versionId > 1) {
                s.createSQLQuery(HibernatePreparationUtils.schemaVersionUpdateStatement(step.versionId)).executeUpdate()
                s.flush
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
      Some(sf.withSession(_.createSQLQuery("select max(version) as max_version from schema_version")
                           .addScalar("max_version", IntegerType.INSTANCE)
                           .uniqueResult().asInstanceOf[Int]))
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
   * The name of this migration step
   */
  def name:String

  /**
   * Requests that the step create migration builder for doing it's migration.
   */
  def createMigration(config:Configuration):MigrationBuilder
}

class FreshMigrationStep(currentMaxVersionId:Int) extends HibernateMigrationStep {
  val log:Logger = LoggerFactory.getLogger(classOf[HibernateConfigStorePreparationStep])
  
  val steps = HibernateConfigStorePreparationStep.migrationSteps

  def versionId = currentMaxVersionId

  def name = "Fresh database migration"

  def createMigration(config: Configuration) = {
    // Create a migration for fresh databases, since there are steps that we need to apply on top of hibernate
    // doing the export
    val freshMigration = new MigrationBuilder(config)
    freshMigration.insert("system_config_options").
      values(Map("opt_key" -> HibernatePreparationUtils.correlationStoreSchemaKey, "opt_val" -> HibernatePreparationUtils.correlationStoreVersion))

    type R = ReferenceDataMigrationStep
    type C = StandaloneConstraintMigrationStep

    steps.filter(_.isInstanceOf[R]).map(_.asInstanceOf[R]).foreach(_.applyReferenceData(freshMigration))
    steps.filter(_.isInstanceOf[C]).map(_.asInstanceOf[C]).foreach(_.applyConstraint(freshMigration))

    DefineSchemaVersionTable.defineTableAndInitialEntry(freshMigration, versionId)

    freshMigration
  }

  def initialSetup(config: Configuration) {
    val export = new SchemaExport(config)
    export.setHaltOnError(true).create(true, true)

    // Note to debuggers: The schema export tool is very annoying from a diagnostics perspective
    // because all SQL errors that occur as a result of a DROP statement are silently swallowed, but they
    // are added to the public list of exceptions that the export tool has seen, but in a completely
    // undifferentiated fashion. This means that you can't tell from the outside whether something blew up
    // as a result of a create statement that you should care about, or whether a bunch of irrelevant DROP
    // exceptions were collected for posterity's sake. In any case, any real exception that you would care about
    // is swallowed and mixed in with exceptions that you probably don't care about.
    // @see https://hibernate.onjira.com/browse/HHH-6633
  }
}


// Special migration steps can be applied independently of the main migration step,
// but if it is filtered from the master list, they can also be applied in the same order.

/**
 * A special type of migration step that in addition to applying DDL also applies new reference data to the database.
 */
trait ReferenceDataMigrationStep {
  def applyReferenceData(migration:MigrationBuilder)
}

/**
 * A special type of migration step that in addition to applying DDL also adds a constraint that may
 * not have been possible with an inline constraint definition.
 */
trait StandaloneConstraintMigrationStep {
  def applyConstraint(migration:MigrationBuilder)
}

/**
 * One-off definition of the schema version table.
 */
object DefineSchemaVersionTable {

  def defineTableAndInitialEntry(migration:MigrationBuilder, targetVersion:Int) {
    migration.createTable("schema_version").
        column("version", Types.INTEGER, false).
        pk("version")
    migration.insert("schema_version").
        values(Map("version" -> new java.lang.Integer(targetVersion)))
  }

}

object HibernateConfigStorePreparationStep {
  /**
   * This is the ordered list of migration steps to apply from any given schema version to bring a hibernate configuration up-to-date.
   * Note that these steps should be executed in strictly ascending order.
   */
  val migrationSteps = Seq(

    new HibernateMigrationStep {
      def versionId = 1
      def name = "Remove groups"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)
        migration.alterTable("pair").
          dropConstraint("FK3462DAF4F4CA7C").
          dropColumn("NAME")
        migration.dropTable("pair_group")

        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 2
      def name = "Add schema version"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)
        DefineSchemaVersionTable.defineTableAndInitialEntry(migration, versionId)

        migration
      }
    },

    new HibernateMigrationStep with ReferenceDataMigrationStep {
      def versionId = 3
      def name = "Add domains"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        // Add our new tables (domains and system config options)
        migration.createTable("domains").
          column("name", Types.VARCHAR, 255, false).
          pk("name")
        migration.createTable("system_config_options").
          column("opt_key", Types.VARCHAR, 255, false).
          column("opt_val", Types.VARCHAR, 255, false).
          pk("opt_key")

        // Add standard reference data
        applyReferenceData(migration)

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

        migration
      }

      def applyReferenceData(migration:MigrationBuilder) {
        // Make sure the default domain is in the DB
        migration.insert("domains").values(Map("name" -> Domain.DEFAULT_DOMAIN.name))
      }
    },

    new HibernateMigrationStep {
      def versionId = 4
      def name = "Add max granularity"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        migration.alterTable("range_category_descriptor").
          addColumn("max_granularity", Types.VARCHAR, 255, true, null)

        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 5
      def name = "Expand primary keys"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)
        migration.alterTable("endpoint_categories").
          dropConstraint("FKEE1F9F06BC780104")
        migration.alterTable("pair").
          dropConstraint("FK3462DA25F0B1C4").
          dropConstraint("FK3462DA4242E68B")
        migration.alterTable("escalations").
          dropConstraint("FK2B3C687E7D35B6A8")
        migration.alterTable("repair_actions").
          dropConstraint("FKF6BE324B7D35B6A8")

        migration.alterTable("endpoint").
          dropPrimaryKey().
          addPrimaryKey("name", "domain")
        migration.alterTable("pair").
          dropPrimaryKey().
          addPrimaryKey("pair_key", "domain")

        migration.alterTable("endpoint_categories").
          addColumn("domain", Types.VARCHAR, 255, false, "diffa").
          addForeignKey("FKEE1F9F066D6BD5C8", Array("id", "domain"), "endpoint", Array("name", "domain"))
        migration.alterTable("escalations").
          addColumn("domain", Types.VARCHAR, 255, false, "diffa").
          addForeignKey("FK2B3C687E2E298B6C", Array("pair_key", "domain"), "pair", Array("pair_key", "domain"))
        migration.alterTable("pair").
          addColumn("uep_domain", Types.VARCHAR, 255, true, null).
          addColumn("dep_domain", Types.VARCHAR, 255, true, null).
          addForeignKey("FK3462DAF2DA557F", Array("downstream, dep_domain"), "endpoint", Array("name", "domain")).
          addForeignKey("FK3462DAF68A3C7", Array("upstream, uep_domain"), "endpoint", Array("name", "domain"))
        migration.alterTable("repair_actions").
          addColumn("domain", Types.VARCHAR, 255, false, "diffa").
          addForeignKey("FKF6BE324B2E298B6C", Array("pair_key", "domain"), "pair", Array("pair_key", "domain"))

        // Where we currently have an upstream or downstream domain, bring in the current pair domain
        migration.sql("update pair set uep_domain=domain where upstream is not null")
        migration.sql("update pair set dep_domain=domain where downstream is not null")

        migration
      }
    },

    new HibernateMigrationStep with ReferenceDataMigrationStep {
      def versionId = 6
      def name = "Add superuser and default users"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)
        migration.alterTable("users").
          addColumn("password_enc", Types.VARCHAR, 255, false, "LOCKED").
          addColumn("superuser", Types.BIT, 1, false, 0)
        applyReferenceData(migration)

        migration
      }
      def applyReferenceData(migration:MigrationBuilder) {
        migration.insert("users").
          values(Map(
            "name" -> "guest", "email" -> "guest@diffa.io",
            "password_enc" -> "84983c60f7daadc1cb8698621f802c0d9f9a3c3c295c810748fb048115c186ec",
            "superuser" -> Boolean.box(true)))
      }
    },

    new HibernateMigrationStep with StandaloneConstraintMigrationStep {
      def versionId = 7
      def name = "Add persistent diffs"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        migration.createTable("diffs").
          column("seq_id", Types.INTEGER, false).
          column("domain", Types.VARCHAR, 255, false).
          column("pair", Types.VARCHAR, 255, false).
          column("entity_id", Types.VARCHAR, 255, false).
          column("is_match", Types.BIT, false).
          column("detected_at", Types.TIMESTAMP, false).
          column("last_seen", Types.TIMESTAMP, false).
          column("upstream_vsn", Types.VARCHAR, 255, true).
          column("downstream_vsn", Types.VARCHAR, 255, true).
          column("ignored", Types.BIT, false).
          pk("seq_id").
          withNativeIdentityGenerator()

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
          withNativeIdentityGenerator()

        applyConstraint(migration)

        migration.createIndex("diff_last_seen", "diffs", "last_seen")
        migration.createIndex("diff_detection", "diffs", "detected_at")
        migration.createIndex("rdiff_is_matched", "diffs", "is_match")
        migration.createIndex("rdiff_domain_idx", "diffs", "entity_id", "domain", "pair")
        migration.createIndex("pdiff_domain_idx", "pending_diffs", "entity_id", "domain", "pair")

        migration
      }

      def applyConstraint(migration: MigrationBuilder) {
        // alter table diffs add constraint FK5AA9592F53F69C16 foreign key (pair, domain) references pair (pair_key, domain);
        migration.alterTable("diffs")
          .addForeignKey("FK5AA9592F53F69C16", Array("pair", "domain"), "pair", Array("pair_key", "domain"))

        migration.alterTable("pending_diffs")
          .addForeignKey("FK75E457E44AD37D84", Array("pair", "domain"), "pair", Array("pair_key", "domain"))
      }
    },

    new HibernateMigrationStep {
      def versionId = 8
      def name = "Add domain sequence index"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)
        migration.createIndex("seq_id_domain_idx", "diffs", "seq_id", "domain")
        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 9
      def name = "Add store checkpoints"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        migration.createTable("store_checkpoints").
          column("domain", Types.VARCHAR, 255, false).
          column("pair", Types.VARCHAR, 255, false).
          column("latest_version", Types.BIGINT, false).
          pk("domain", "pair")

        migration.alterTable("store_checkpoints").
          addForeignKey("FK50EE698DF6FDBACC", Array("pair", "domain"), "pair", Array("pair", "domain"))

        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 10
      def name = "Revise url length"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        migration.alterTable("endpoint").
          alterColumn("scan_url", Types.VARCHAR, 1024, true, null).
          alterColumn("content_retrieval_url", Types.VARCHAR, 1024, true, null).
          alterColumn("version_generation_url", Types.VARCHAR, 1024, true, null).
          alterColumn("inbound_url", Types.VARCHAR, 1024, true, null)

        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 11
      def name = "Add endpoint views"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        migration.createTable("endpoint_views").
          column("name", Types.VARCHAR, 255, false).
          column("endpoint", Types.VARCHAR, 255, false).
          column("domain", Types.VARCHAR, 255, false).
          pk("name", "endpoint", "domain")
        migration.createTable("endpoint_views_categories").
          column("name", Types.VARCHAR, 255, false).
          column("endpoint", Types.VARCHAR, 255, false).
          column("domain", Types.VARCHAR, 255, false).
          column("category_descriptor_id", Types.INTEGER, false).
          column("category_name", Types.VARCHAR, 255, false).
          pk("name", "endpoint", "domain", "category_name")
        migration.createTable("pair_views").
          column("name", Types.VARCHAR, 255, false).
          column("pair", Types.VARCHAR, 255, false).
          column("domain", Types.VARCHAR, 255, false).
          column("scan_cron_spec", Types.VARCHAR, 255, true).
          pk("name", "pair", "domain")

        migration.alterTable("endpoint_views").
          addForeignKey("FKBE0A5744D532E642", Array("endpoint", "domain"), "endpoint", Array("name", "domain"))
        migration.alterTable("endpoint_views_categories").
          addForeignKey("FKF03ED1F7B6D4F2CB", Array("category_descriptor_id"), "category_descriptor", Array("id"))
        migration.alterTable("pair_views").
          addForeignKey("FKE0BDD4C9F6FDBACC", Array("pair", "domain"), "pair", Array("pair_key", "domain"))

        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 12
      def name = "Add allow manual scan flag to pair"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        migration.alterTable("pair").
          addColumn("allow_manual_scans", Types.BIT, 1, true, 0)

        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 13
      def name = "Remove inbound content type from endpoint"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        migration.alterTable("endpoint").dropColumn("inbound_content_type")

        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 14
      def name = "Add pair reports"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        migration.createTable("pair_reports").
          column("name", Types.VARCHAR, 255, false).
          column("pair_key", Types.VARCHAR, 255, false).
          column("domain", Types.VARCHAR, 255, false).
          column("report_type", Types.VARCHAR, 255, false).
          column("target", Types.VARCHAR, 1024, false).
          pk("name", "pair_key", "domain")

        migration.alterTable("pair_reports").
          addForeignKey("FKCEC6E15A2E298B6C", Array("pair_key", "domain"), "pair", Array("key", "domain"))

        // Report escalations don't have a configured origin, so relax the constraint on origin being mandatory
        migration.alterTable("escalations").
          alterColumn("origin", Types.VARCHAR, 255, true, null)

        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 15
      def name = "Remove content type from endpoint"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        migration.alterTable("endpoint").dropColumn("content_type")

        migration
      }
    }
  )
}