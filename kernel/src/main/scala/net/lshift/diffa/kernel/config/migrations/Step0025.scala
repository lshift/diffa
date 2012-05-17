package net.lshift.diffa.kernel.config.migrations

import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder

import net.lshift.diffa.kernel.config.limits.{ScanReadTimeout, ScanConnectTimeout}
import net.lshift.diffa.kernel.config.{MigrationUtil, HibernateMigrationStep}

/**
 * This Step migrates a schema/database to version 25, adding default read and
 * connect timeouts for scans.
 */
object Step0025 extends HibernateMigrationStep {
  def versionId = 25

  def name = "Add default scan timeouts"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    MigrationUtil.insertLimit(migration, ScanConnectTimeout)
    MigrationUtil.insertLimit(migration, ScanReadTimeout)

    migration
  }
}
