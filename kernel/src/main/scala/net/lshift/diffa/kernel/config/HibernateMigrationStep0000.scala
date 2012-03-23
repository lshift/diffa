package net.lshift.diffa.kernel.config

import org.hibernate.cfg.Configuration
import java.sql.Types
import scala.collection.JavaConversions._
import net.lshift.hibernate.migrations.MigrationBuilder

/**
 * This Step 'migrates' a schema/database to version 0 -
 * that is, it creates the base schema from scratch.
 */
object HibernateMigrationStep0000 extends HibernateMigrationStep {
  def versionId = 0
  def name = "Create schema"
  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.createTable("category_descriptor").
      column("category_id", Types.INTEGER, false).
      column("constraint_type", Types.VARCHAR, 20, false).
      column("prefix_length", Types.INTEGER, true).
      column("max_length", Types.INTEGER, true).
      column("step", Types.INTEGER, true).
      pk("category_id").
      withNativeIdentityGenerator()

    migration.createTable("config_options").
      column("domain", Types.VARCHAR, 50, false, Domain.DEFAULT_DOMAIN.name).
      column("opt_key", Types.VARCHAR, 255, false).
      column("opt_val", Types.VARCHAR, 255, true).
      pk("opt_key", "domain")

    val diffsTable = migration.createTable("diffs").
      column("seq_id", Types.INTEGER, false).
      column("domain", Types.VARCHAR, 50, false).
      column("pair", Types.VARCHAR, 50, false).
      column("entity_id", Types.VARCHAR, 255, false).
      column("is_match", Types.BIT, false).
      column("detected_at", Types.TIMESTAMP, false).
      column("last_seen", Types.TIMESTAMP, false).
      column("upstream_vsn", Types.VARCHAR, 255, true).
      column("downstream_vsn", Types.VARCHAR, 255, true).
      column("ignored", Types.BIT, false).
      pk("seq_id", "domain", "pair").
      withNativeIdentityGenerator()

    // N.B. include the partition info table on all DBs (support may be added in future)
    DefinePartitionInformationTable.defineTable(migration)

    if (migration.canUseListPartitioning) {
      diffsTable.virtualColumn("partition_name", Types.VARCHAR, 512, "domain || '_' || pair").
        listPartitioned("partition_name").
        listPartition("part_dummy_default", "default")

      DefinePartitionInformationTable.applyPartitionVersion(migration, "diffs", versionId)

      migration.executeDatabaseScript("sync_pair_diff_partitions", "net.lshift.diffa.kernel.config.procedures")
    }

    migration.createTable("domains").
      column("name", Types.VARCHAR, 50, false).
      pk("name")

    migration.createTable("endpoint").
      column("domain", Types.VARCHAR, 50, false, Domain.DEFAULT_DOMAIN.name).
      column("name", Types.VARCHAR, 50, false).
      column("scan_url", Types.VARCHAR, 1024, true).
      column("content_retrieval_url", Types.VARCHAR, 1024, true).
      column("version_generation_url", Types.VARCHAR, 1024, true).
      column("inbound_url", Types.VARCHAR, 1024, true).
      pk("name", "domain")

    migration.createTable("endpoint_categories").
      column("domain", Types.VARCHAR, 50, false, Domain.DEFAULT_DOMAIN.name).
      column("id", Types.VARCHAR, 50, false).
      column("category_descriptor_id", Types.INTEGER, false).
      column("name", Types.VARCHAR, 50, false).
      pk("id", "name")

    migration.createTable("endpoint_views").
      column("name", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("domain", Types.VARCHAR, 50, false).
      pk("name", "endpoint", "domain")

    migration.createTable("endpoint_views_categories").
      column("name", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("domain", Types.VARCHAR, 50, false).
      column("category_descriptor_id", Types.INTEGER, false).
      column("category_name", Types.VARCHAR, 50, false).
      pk("name", "endpoint", "domain", "category_name")
    
    migration.createTable("escalations").
      column("domain", Types.VARCHAR, 50, false, Domain.DEFAULT_DOMAIN.name).
      column("name", Types.VARCHAR, 50, false).
      column("pair_key", Types.VARCHAR, 50, false).
      column("action", Types.VARCHAR, 50, false).
      column("action_type", Types.VARCHAR, 255, false).
      column("event", Types.VARCHAR, 255, false).
      column("origin", Types.VARCHAR, 255, true).
      pk("name", "pair_key")

    migration.createTable("members").
      column("domain_name", Types.VARCHAR, 50, false).
      column("user_name", Types.VARCHAR, 50, false).
      pk("user_name", "domain_name")
    
    migration.createTable("pair").
      column("domain", Types.VARCHAR, 50, false, Domain.DEFAULT_DOMAIN.name).
      column("pair_key", Types.VARCHAR, 50, false).
      column("upstream", Types.VARCHAR, 50, false).
      column("downstream", Types.VARCHAR, 50, false).
      column("version_policy_name", Types.VARCHAR, 50, true).
      column("matching_timeout", Types.INTEGER, true).
      column("scan_cron_spec", Types.VARCHAR, 50, true).
      column("allow_manual_scans", Types.BIT, 1, true, 0).
      column("events_to_log", Types.INTEGER, 11, false, 0).
      column("max_explain_files", Types.INTEGER, 11, false, 0).
      pk("pair_key", "domain")

    migration.createTable("pair_reports").
      column("name", Types.VARCHAR, 50, false).
      column("pair_key", Types.VARCHAR, 50, false).
      column("domain", Types.VARCHAR, 50, false).
      column("report_type", Types.VARCHAR, 50, false).
      column("target", Types.VARCHAR, 1024, false).
      pk("name", "pair_key", "domain")

    migration.createTable("pair_views").
      column("name", Types.VARCHAR, 50, false).
      column("pair", Types.VARCHAR, 50, false).
      column("domain", Types.VARCHAR, 50, false).
      column("scan_cron_spec", Types.VARCHAR, 50, true).
      pk("name", "pair", "domain")

    migration.createTable("pending_diffs").
      column("oid", Types.INTEGER, false).
      column("domain", Types.VARCHAR, 50, false).
      column("pair", Types.VARCHAR, 50, false).
      column("entity_id", Types.VARCHAR, 50, false).
      column("detected_at", Types.TIMESTAMP, false).
      column("last_seen", Types.TIMESTAMP, false).
      column("upstream_vsn", Types.VARCHAR, 255, true).
      column("downstream_vsn", Types.VARCHAR, 255, true).
      pk("oid").
      withNativeIdentityGenerator()

    migration.createTable("prefix_category_descriptor").
      column("id", Types.INTEGER, false).
      pk("id")

    migration.createTable("range_category_descriptor").
      column("id", Types.INTEGER, false).
      column("data_type", Types.VARCHAR, 20, true).
      column("upper_bound", Types.VARCHAR, 255, true).
      column("lower_bound", Types.VARCHAR, 255, true).
      column("max_granularity", Types.VARCHAR, 20, true).
      pk("id")

    migration.createTable("repair_actions").
      column("domain", Types.VARCHAR, 50, false, Domain.DEFAULT_DOMAIN.name).
      column("name", Types.VARCHAR, 50, false).
      column("pair_key", Types.VARCHAR, 50, false).
      column("url", Types.VARCHAR, 1024, true).
      column("scope", Types.VARCHAR, 20, true).
      pk("name", "pair_key")

    migration.createTable("schema_version").
      column("version", Types.INTEGER, false).
      pk("version")

    migration.createTable("set_category_descriptor").
      column("id", Types.INTEGER, false).
      pk("id")

    migration.createTable("set_constraint_values").
      column("value_id", Types.INTEGER, false).
      column("value_name", Types.VARCHAR, 255, false).
      pk("value_id", "value_name")

    migration.createTable("store_checkpoints").
      column("domain", Types.VARCHAR, 50, false).
      column("pair", Types.VARCHAR, 50, false).
      column("latest_version", Types.BIGINT, false).
      pk("domain", "pair")

    migration.createTable("system_config_options").
      column("opt_key", Types.VARCHAR, 255, false).
      column("opt_val", Types.VARCHAR, 255, false).
      pk("opt_key")

    migration.createTable("users").
      column("name", Types.VARCHAR, 50, false).
      column("email", Types.VARCHAR, 1024, true).
      column("password_enc", Types.VARCHAR, 100, false, "LOCKED").
      column("superuser", Types.BIT, 1, false, 0).
      column("token", Types.VARCHAR, 50, true).
      pk("name")
    

    migration.alterTable("config_options").
      addForeignKey("FK80C74EA1C3C204DC", "domain", "domains", "name")

    migration.alterTable("diffs")
      .addForeignKey("FK5AA9592F53F69C16", Array("pair", "domain"), "pair", Array("pair_key", "domain"))

    migration.alterTable("endpoint").
      addForeignKey("FK67C71D95C3C204DC", "domain", "domains", "name")

    migration.alterTable("endpoint_categories").
      addForeignKey("FKEE1F9F066D6BD5C8", Array("id", "domain"), "endpoint", Array("name", "domain")).
      addForeignKey("FKEE1F9F06B6D4F2CB", "category_descriptor_id", "category_descriptor", "category_id")

    migration.alterTable("endpoint_views").
      addForeignKey("FKBE0A5744D532E642", Array("endpoint", "domain"), "endpoint", Array("name", "domain"))

    migration.alterTable("endpoint_views_categories").
      addForeignKey("FKF03ED1F7B6D4F2CB", Array("category_descriptor_id"), "category_descriptor", Array("category_id"))

    migration.alterTable("escalations").
      addForeignKey("FK2B3C687E2E298B6C", Array("pair_key", "domain"), "pair", Array("pair_key", "domain"))

    migration.alterTable("pair").
      addForeignKey("FK3462DAC3C204DC", "domain", "domains", "name").
      addForeignKey("FK3462DAF68A3C7", Array("upstream", "domain"), "endpoint", Array("name", "domain")).
      addForeignKey("FK3462DAF2DA557F", Array("downstream", "domain"), "endpoint", Array("name", "domain"))

    migration.alterTable("pair_reports").
      addForeignKey("FKCEC6E15A2E298B6C", Array("pair_key", "domain"), "pair", Array("pair_key", "domain"))

    migration.alterTable("pair_views").
      addForeignKey("FKE0BDD4C9F6FDBACC", Array("pair", "domain"), "pair", Array("pair_key", "domain"))

    migration.alterTable("members").
      addForeignKey("FK388EC9191902E93E", "domain_name", "domains", "name").
      addForeignKey("FK388EC9195A11FA9E", "user_name", "users", "name")

    migration.alterTable("pending_diffs")
      .addForeignKey("FK75E457E44AD37D84", Array("pair", "domain"), "pair", Array("pair_key", "domain"))

    migration.alterTable("prefix_category_descriptor").
      addForeignKey("FK46474423466530AE", "id", "category_descriptor", "category_id")

    migration.alterTable("range_category_descriptor").
      addForeignKey("FKDC53C74E7A220B71", "id", "category_descriptor", "category_id")

    migration.alterTable("repair_actions").
      addForeignKey("FKF6BE324B2E298B6C", Array("pair_key", "domain"), "pair", Array("pair_key", "domain"))

    migration.alterTable("set_category_descriptor").
      addForeignKey("FKA51D45F39810CA56", "id", "category_descriptor", "category_id")

    migration.alterTable("set_constraint_values").
      addForeignKey("FK96C7B32744035BE4", "value_id", "category_descriptor", "category_id")

    migration.alterTable("store_checkpoints").
      addForeignKey("FK50EE698DF6FDBACC", Array("pair", "domain"), "pair", Array("pair_key", "domain"))

    migration.alterTable("users").
      addUniqueConstraint("token")


    migration.createIndex("diff_last_seen", "diffs", "last_seen")
    migration.createIndex("diff_detection", "diffs", "detected_at")
    migration.createIndex("rdiff_is_matched", "diffs", "is_match")
    migration.createIndex("rdiff_domain_idx", "diffs", "entity_id", "domain", "pair")

    migration.createIndex("pdiff_domain_idx", "pending_diffs", "entity_id", "domain", "pair")


    migration.insert("domains").values(Map("name" -> Domain.DEFAULT_DOMAIN.name))
    
    migration.insert("config_options").
      values(Map("domain" -> Domain.DEFAULT_DOMAIN.name, "opt_key" -> "configStore.schemaVersion", "opt_val" -> "0"))

    migration.insert("system_config_options").values(Map(
      "opt_key" -> ConfigOption.eventExplanationLimitKey,
      "opt_val" -> "100"))

    migration.insert("system_config_options").values(Map(
      "opt_key" -> ConfigOption.explainFilesLimitKey,
      "opt_val" -> "20"))

    migration.insert("users").
      values(Map(
      "name" -> "guest", "email" -> "guest@diffa.io",
      "password_enc" -> "84983c60f7daadc1cb8698621f802c0d9f9a3c3c295c810748fb048115c186ec",
      "superuser" -> Boolean.box(true)))

    migration.insert("schema_version").
      values(Map("version" -> new java.lang.Integer(versionId)))

    
    if (migration.canAnalyze) {
      migration.analyzeTable("diffs");
    }


    migration
  }
}
