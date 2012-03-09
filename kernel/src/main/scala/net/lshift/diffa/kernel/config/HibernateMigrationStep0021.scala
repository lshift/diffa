package net.lshift.diffa.kernel.config

import net.lshift.hibernate.migrations.MigrationBuilder
import org.hibernate.cfg.Configuration
import scala.collection.JavaConversions._
import java.sql.Types

object HibernateMigrationStep0021 extends HibernateMigrationStep {
  def versionId = 21
  def name = "Add per-pair logging configuration"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.alterTable("config_options").
      replacePrimaryKey("opt_key", "domain")

    migration.alterTable("pair").
      addColumn("events_to_log", Types.INTEGER, 11, false, 0).
      addColumn("max_explain_files", Types.INTEGER, 11, false, 0)

    migration.insert("system_config_options").values(Map(
      "opt_key" -> ConfigOption.eventExplanationLimitKey,
      "opt_val" -> "100"))

    migration.insert("system_config_options").values(Map(
      "opt_key" -> ConfigOption.explainFilesLimitKey,
      "opt_val" -> "20"))

    migration
  }
}
