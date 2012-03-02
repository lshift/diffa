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
import org.slf4j.{LoggerFactory, Logger}
import net.lshift.diffa.kernel.util.SessionHelper._
import org.hibernate.tool.hbm2ddl.{DatabaseMetadata, SchemaExport}
import org.hibernate.cfg.{Environment, Configuration}
import java.sql.{Types, Connection}
import net.lshift.diffa.kernel.differencing.VersionCorrelationStore

import net.lshift.hibernate.migrations.MigrationBuilder

import scala.collection.JavaConversions._
import org.hibernate.`type`.IntegerType
import org.hibernate.dialect.{Oracle10gDialect, Dialect}
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
    val version = detectVersion(sf, config)
    val nextVersion = version match {
      case None =>
        log.info("Empty schema detected; version 0 will initially be installed, followed by upgrade to latest")
        0
      case Some(vsn) =>
        log.info("Current schema version is %d".format(vsn))
        vsn + 1
    }
    val migrations = migrationSteps.slice(nextVersion, migrationSteps.length)

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

/**
 * One-off definition of the partition information table.
 */
object DefinePartitionInformationTable {

  def defineTable(migration:MigrationBuilder) {    
    migration.createTable("partition_information").
        column("table_name", Types.VARCHAR, false).
        column("version", Types.INTEGER, false).
        pk("table_name","version")
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
    HibernateMigrationStep0,

    new HibernateMigrationStep {
      def versionId = 1
      def name = "Remove groups"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)
        migration.alterTable("pair").
          dropForeignKey("FK3462DAF4F4CA7C").
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

    new HibernateMigrationStep {
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
          addForeignKey("FK2B3C687E7D35B6A8", "pair_key", "pair", "pair_key")

        //alter table repair_actions add constraint FKF6BE324B7D35B6A8 foreign key (pair_key) references pair;
        migration.alterTable("repair_actions").
          addForeignKey("FKF6BE324B7D35B6A8", "pair_key", "pair", "pair_key")

        migration
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
          dropForeignKey("FKEE1F9F06BC780104")
        migration.alterTable("pair").
          dropForeignKey("FK3462DA25F0B1C4").
          dropForeignKey("FK3462DA4242E68B")
        migration.alterTable("escalations").
          dropForeignKey("FK2B3C687E7D35B6A8")
        migration.alterTable("repair_actions").
          dropForeignKey("FKF6BE324B7D35B6A8")

        migration.alterTable("endpoint").
          replacePrimaryKey("name", "domain")
        migration.alterTable("pair").
          replacePrimaryKey("pair_key", "domain")

        migration.alterTable("endpoint_categories").
          addColumn("domain", Types.VARCHAR, 255, false, "diffa").
          addForeignKey("FKEE1F9F066D6BD5C8", Array("id", "domain"), "endpoint", Array("name", "domain"))
        migration.alterTable("escalations").
          addColumn("domain", Types.VARCHAR, 255, false, "diffa").
          addForeignKey("FK2B3C687E2E298B6C", Array("pair_key", "domain"), "pair", Array("pair_key", "domain"))
        migration.alterTable("pair").
          addColumn("uep_domain", Types.VARCHAR, 255, false, "diffa").
          addColumn("dep_domain", Types.VARCHAR, 255, false, "diffa").
          addForeignKey("FK3462DAF2DA557F", Array("downstream", "dep_domain"), "endpoint", Array("name", "domain")).
          addForeignKey("FK3462DAF68A3C7", Array("upstream", "uep_domain"), "endpoint", Array("name", "domain"))
        migration.alterTable("repair_actions").
          addColumn("domain", Types.VARCHAR, 255, false, "diffa").
          addForeignKey("FKF6BE324B2E298B6C", Array("pair_key", "domain"), "pair", Array("pair_key", "domain"))

        // Where we currently have an upstream or downstream domain, bring in the current pair domain
        migration.sql("update pair set uep_domain=domain where upstream is not null")
        migration.sql("update pair set dep_domain=domain where downstream is not null")

        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 6
      def name = "Add superuser and default users"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)
        migration.alterTable("users").
          addColumn("password_enc", Types.VARCHAR, 255, false, "LOCKED").
          addColumn("superuser", Types.BIT, 1, false, 0)

        migration.insert("users").
          values(Map(
          "name" -> "guest", "email" -> "guest@diffa.io",
          "password_enc" -> "84983c60f7daadc1cb8698621f802c0d9f9a3c3c295c810748fb048115c186ec",
          "superuser" -> Boolean.box(true)))

        migration
      }
    },

    new HibernateMigrationStep {
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

        // alter table diffs add constraint FK5AA9592F53F69C16 foreign key (pair, domain) references pair (pair_key, domain);
        migration.alterTable("diffs")
          .addForeignKey("FK5AA9592F53F69C16", Array("pair", "domain"), "pair", Array("pair_key", "domain"))

        migration.alterTable("pending_diffs")
          .addForeignKey("FK75E457E44AD37D84", Array("pair", "domain"), "pair", Array("pair_key", "domain"))

        migration.createIndex("diff_last_seen", "diffs", "last_seen")
        migration.createIndex("diff_detection", "diffs", "detected_at")
        migration.createIndex("rdiff_is_matched", "diffs", "is_match")
        migration.createIndex("rdiff_domain_idx", "diffs", "entity_id", "domain", "pair")
        migration.createIndex("pdiff_domain_idx", "pending_diffs", "entity_id", "domain", "pair")

        migration
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
          addForeignKey("FK50EE698DF6FDBACC", Array("pair", "domain"), "pair", Array("pair_key", "domain"))

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
          column("domain", Types.VARCHAR, 50, false).
          pk("name", "endpoint", "domain")
        migration.createTable("endpoint_views_categories").
          column("name", Types.VARCHAR, 255, false).
          column("endpoint", Types.VARCHAR, 255, false).
          column("domain", Types.VARCHAR, 50, false).
          column("category_descriptor_id", Types.INTEGER, false).
          column("category_name", Types.VARCHAR, 255, false).
          pk("name", "endpoint", "domain", "category_name")
        migration.createTable("pair_views").
          column("name", Types.VARCHAR, 255, false).
          column("pair", Types.VARCHAR, 255, false).
          column("domain", Types.VARCHAR, 50, false).
          column("scan_cron_spec", Types.VARCHAR, 255, true).
          pk("name", "pair", "domain")

        migration.alterTable("endpoint_views").
          addForeignKey("FKBE0A5744D532E642", Array("endpoint", "domain"), "endpoint", Array("name", "domain"))
        migration.alterTable("endpoint_views_categories").
          addForeignKey("FKF03ED1F7B6D4F2CB", Array("category_descriptor_id"), "category_descriptor", Array("category_id"))
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
          addForeignKey("FKCEC6E15A2E298B6C", Array("pair_key", "domain"), "pair", Array("pair_key", "domain"))

        // Report escalations don't have a configured origin, so relax the constraint on origin being mandatory
        migration.alterTable("escalations").
          setColumnNullable("origin", Types.VARCHAR, 255, true)

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
    },

    new HibernateMigrationStep {
        def versionId = 16
        def name = "Partition diffs table"

      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        // If this step is being executed, then by definition, the partition information table cannot exist
        DefinePartitionInformationTable.defineTable(migration)

        applyPartitioning(migration)
      }

      def applyPartitioning(migration:MigrationBuilder) = {
        if (migration.canUseHashPartitioning) {

          DefinePartitionInformationTable.preventPartitionReapplication(migration, "diffs", versionId)

          // This number is quite arbitrary
          val arbitraryPartitionCount = 16
  
          migration.createTable("diffs_tmp").
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
                    pk("seq_id", "domain", "pair").
                    hashPartitions(arbitraryPartitionCount, "domain", "pair")

          val columns = Seq("seq_id", "domain", "pair", "entity_id", "is_match", "detected_at",
                            "last_seen","upstream_vsn","downstream_vsn","ignored")
          
          migration.copyTableContents("diffs", "diffs_tmp", columns)
          migration.dropTable("diffs")
          migration.alterTable("diffs_tmp").renameTo("diffs")

          // Copy the constraint that was applied in step 7
          migration.alterTable("diffs")
            .addForeignKey("FK5AA9592F53F69C16", Array("pair", "domain"), "pair", Array("pair_key", "domain"))
  
          // If this partitioning function is being executed, then by definition, the partition information table must exist
          DefinePartitionInformationTable.applyPartitionVersion(migration, "diffs", versionId)

          migration.createIndex("diff_last_seen", "diffs", "last_seen")
          migration.createIndex("diff_detection", "diffs", "detected_at")
          migration.createIndex("rdiff_is_matched", "diffs", "is_match")
          migration.createIndex("rdiff_domain_idx", "diffs", "entity_id", "domain", "pair")

          if (migration.canAnalyze) {
            migration.analyzeTable("diffs");
          }
        }
        
        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 17
      def name = "Add token to users"
      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)
        migration.alterTable("users").
          addColumn("token", Types.VARCHAR, 255, true, null).
          addUniqueConstraint("token")
        migration
      }
    },

  new HibernateMigrationStep {
        def versionId = 18
        def name = "Partition diffs table with list partitions"

      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        applyPartitioning(migration)
      }

      def applyPartitioning(migration:MigrationBuilder) = {
        if (migration.canUseListPartitioning) {

          DefinePartitionInformationTable.preventPartitionReapplication(migration, "diffs", versionId)
          
          migration.createTable("diffs_tmp").
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
                    pk("seq_id", "domain", "pair").
                    virtualColumn("partition_name", Types.VARCHAR, 512, "domain || '_' || pair").
                    listPartitioned("partition_name").
                    listPartition("part_dummy_default", "default")

          val columns = Seq("seq_id", "domain", "pair", "entity_id", "is_match", "detected_at",
                            "last_seen","upstream_vsn","downstream_vsn","ignored")

          // Load the appropriate stored procedure into the database, and execute it
          migration.executeDatabaseScript("sync_pair_diff_partitions", "net.lshift.diffa.kernel.config.procedures")
          migration.executeStoredProcedure("sync_pair_diff_partitions('diffs_tmp')")

          migration.copyTableContents("diffs", "diffs_tmp", columns)
          migration.dropTable("diffs")
          migration.alterTable("diffs_tmp").renameTo("diffs")

          // Copy the constraint that was applied in step 7
          migration.alterTable("diffs")
            .addForeignKey("FK5AA9592F53F69C16", Array("pair", "domain"), "pair", Array("pair_key", "domain"))

          // If this partitioning function is being executed, then by definition, the partition information table must exist
          DefinePartitionInformationTable.applyPartitionVersion(migration, "diffs", versionId)

          migration.createIndex("diff_last_seen", "diffs", "last_seen")
          migration.createIndex("diff_detection", "diffs", "detected_at")
          migration.createIndex("rdiff_is_matched", "diffs", "is_match")
          migration.createIndex("rdiff_domain_idx", "diffs", "entity_id", "domain", "pair")

          if (migration.canAnalyze) {
            migration.analyzeTable("diffs");
          }
        }

        migration
      }
    },

    new HibernateMigrationStep {
      def versionId = 19
      def name = "Reference endpoints by name only"

      def createMigration(config: Configuration) = {
        val migration = new MigrationBuilder(config)

        // Remove the existing constraints on domain/endpoint pairs, and then remove the endpoint domain columns
        migration.alterTable("pair").
          dropForeignKey("FK3462DAF68A3C7").
          dropForeignKey("FK3462DAF2DA557F").
          dropColumn("dep_domain").
          dropColumn("uep_domain")

        migration.alterTable("pair").
          addForeignKey("FK3462DAF68A3C7", Array("upstream", "domain"), "endpoint", Array("name", "domain")).
          addForeignKey("FK3462DAF2DA557F", Array("downstream", "domain"), "endpoint", Array("name", "domain"))

        migration
      }
    },

    HibernateMigrationStep20
  )
}