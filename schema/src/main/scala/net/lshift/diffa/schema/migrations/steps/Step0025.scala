package net.lshift.diffa.schema.migrations.steps

import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder
import net.lshift.diffa.schema.servicelimits.{ScanConnectTimeout, ScanReadTimeout}
import net.lshift.diffa.schema.migrations.{MigrationUtil, HibernateMigrationStep}

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
