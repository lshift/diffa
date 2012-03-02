package net.lshift.diffa.kernel.config

import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder
import java.sql.Types

/**
 * Upgrade the diffa database schema to version 20.
 */
object HibernateMigrationStep20 extends HibernateMigrationStep {
  def versionId = 20
  def name = "Shorten columns lengths for more complete MySQL support"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    // This is required in order to avoid ORA-01404:
    // "ALTER COLUMN will make an index too large."
    // The index is recreated after the diffs.pair column is resized
    // NOTE: this may take considerable time if the diffs table is large.
    // TODO: possibly only do this if running against an Oracle DB.
    migration.alterTable("diffs").dropPrimaryKey

    migration.alterTable("category_descriptor").
      alterColumn("constraint_type", Types.VARCHAR, 20, true, null)

    migration.alterTable("config_options").
      alterColumn("domain", Types.VARCHAR, 50, true, null)

    migration.alterTable("diffs").
      alterColumn("domain", Types.VARCHAR, 50, true, null).
      alterColumn("pair", Types.VARCHAR, 50, true, null)

    migration.alterTable("domains").
      alterColumn("name", Types.VARCHAR, 50, true, null)

    migration.alterTable("endpoint").
      alterColumn("name", Types.VARCHAR, 50, true, null).
      alterColumn("domain", Types.VARCHAR, 50, true, null)

    migration.alterTable("endpoint_categories").
      alterColumn("id", Types.VARCHAR, 50, true, null).
      alterColumn("domain", Types.VARCHAR, 50, true, null).
      alterColumn("name", Types.VARCHAR, 50, true, null)

    migration.alterTable("endpoint_views").
      alterColumn("name", Types.VARCHAR, 50, true, null).
      alterColumn("endpoint", Types.VARCHAR, 50, true, null).
      alterColumn("domain", Types.VARCHAR, 50, true, null)

    migration.alterTable("endpoint_views_categories").
      alterColumn("name", Types.VARCHAR, 50, true, null).
      alterColumn("endpoint", Types.VARCHAR, 50, true, null).
      alterColumn("domain", Types.VARCHAR, 50, true, null).
      alterColumn("category_name", Types.VARCHAR, 50, true, null)

    migration.alterTable("escalations").
      alterColumn("name", Types.VARCHAR, 50, true, null).
      alterColumn("pair_key", Types.VARCHAR, 50, true, null).
      alterColumn("domain", Types.VARCHAR, 50, true, null).
      alterColumn("action", Types.VARCHAR, 50, true, null)

    migration.alterTable("members").
      alterColumn("domain_name", Types.VARCHAR, 50, true, null).
      alterColumn("user_name", Types.VARCHAR, 50, true, null)

    migration.alterTable("pair").
      alterColumn("pair_key", Types.VARCHAR, 50, true, null).
      alterColumn("domain", Types.VARCHAR, 50, true, null).
      alterColumn("upstream", Types.VARCHAR, 50, true, null).
      alterColumn("downstream", Types.VARCHAR, 50, true, null).
      alterColumn("version_policy_name", Types.VARCHAR, 50, true, null).
      alterColumn("scan_cron_spec", Types.VARCHAR, 50, true, null)

    migration.alterTable("pair_reports").
      alterColumn("name", Types.VARCHAR, 50, true, null).
      alterColumn("pair_key", Types.VARCHAR, 50, true, null).
      alterColumn("domain", Types.VARCHAR, 50, true, null).
      alterColumn("report_type", Types.VARCHAR, 50, true, null)

    migration.alterTable("pair_views").
      alterColumn("name", Types.VARCHAR, 50, true, null).
      alterColumn("pair", Types.VARCHAR, 50, true, null).
      alterColumn("domain", Types.VARCHAR, 50, true, null).
      alterColumn("scan_cron_spec", Types.VARCHAR, 50, true, null)

    migration.alterTable("pending_diffs").
      alterColumn("entity_id", Types.VARCHAR, 50, true, null).
      alterColumn("domain", Types.VARCHAR, 50, true, null).
      alterColumn("pair", Types.VARCHAR, 50, true, null)

    migration.alterTable("range_category_descriptor").
      alterColumn("data_type", Types.VARCHAR, 20, true, null).
      alterColumn("max_granularity", Types.VARCHAR, 20, true, null)

    migration.alterTable("repair_actions").
      alterColumn("name", Types.VARCHAR, 50, true, null).
      alterColumn("pair_key", Types.VARCHAR, 50, true, null).
      alterColumn("domain", Types.VARCHAR, 50, true, null).
      alterColumn("url", Types.VARCHAR, 1024, true, null).
      alterColumn("scope", Types.VARCHAR, 20, true, null)

    migration.alterTable("store_checkpoints").
      alterColumn("pair", Types.VARCHAR, 50, true, null).
      alterColumn("domain", Types.VARCHAR, 50, true, null)

    migration.alterTable("users").
      alterColumn("name", Types.VARCHAR, 50, true, null).
      alterColumn("token", Types.VARCHAR, 50, true, null).
      alterColumn("email", Types.VARCHAR, 1024, true, null).
      alterColumn("password_enc", Types.VARCHAR, 100, true, null)

    migration.alterTable("partition_information").
      alterColumn("table_name", Types.VARCHAR, 50, true, null)

    // See note against dropping the primary key.
    migration.alterTable("diffs").
      addPrimaryKey("seq_id", "domain", "pair")

    if (migration.canAnalyze) {
      migration.analyzeTable("diffs")
    }

    migration
  }
}
