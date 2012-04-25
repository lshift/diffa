package net.lshift.diffa.kernel.config

import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder
import java.sql.Types

/**
 * Add configuration tables for enforcing limits on the Diffa service.
 */
object HibernateMigrationStep0023 extends HibernateMigrationStep {
  def versionId = 23

  def name = "Configure Service Limits"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.createTable("limit_definitions").
      column("name", Types.VARCHAR, 50, false).
      column("description", Types.VARCHAR, 255, false).
      pk("name")

    migration.createTable("system_limits").
      column("name", Types.VARCHAR, 50, false).
      column("default_limit", Types.INTEGER, 11, false, 0).
      column("hard_limit", Types.INTEGER, 11, false, 0).
      pk("name")

    migration.createTable("domain_limits").
      column("domain", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("default_limit", Types.INTEGER, 11, false, 0).
      column("hard_limit", Types.INTEGER, 11, false, 0).
      pk("domain", "name")

    migration.createTable("pair_limits").
      column("domain", Types.VARCHAR, 50, false).
      column("pair_key", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("limit_value", Types.INTEGER, 11, false, 0).
      pk("domain", "pair_key", "name")

    migration.alterTable("system_limits").
      addForeignKey("fk_system_limit_service_limit", "name", "limit_definitions", "name")

    migration.alterTable("domain_limits").
      addForeignKey("fk_domain_limit_service_limit", "name", "limit_definitions", "name").
      addForeignKey("fk_domain_limit_domain", "domain", "domains", "name")

    migration.alterTable("pair_limits").
      addForeignKey("fk_pair_limit_service_limit", "name", "limit_definitions", "name").
      addForeignKey("fk_pair_limit_pair", Array("domain", "pair_key"), "pair", Array("domain", "pair_key"))

    migration
  }
}
