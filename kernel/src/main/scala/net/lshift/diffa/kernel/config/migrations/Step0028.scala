/**
 * Copyright (C) 2010-2012 LShift Ltd.
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
package net.lshift.diffa.kernel.config.migrations

import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder
import java.sql.Types
import net.lshift.diffa.kernel.config.HibernateMigrationStep

object Step0028 extends HibernateMigrationStep {

  def versionId = 28

  def name = "Widen sequence column type"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    val diffsWidener = migration.widenColumnInTable("diffs").column("seq_id")
    val pendingDiffswidener = migration.widenColumnInTable("pending_diffs").column("oid")

    CommonSteps.buildDiffsTable(diffsWidener.getTempTable)
    CommonSteps.buildPendingDiffsTable(pendingDiffswidener.getTempTable)

    if (diffsWidener.requiresNewTable()) {
      CommonSteps.applyConstraintsToDiffsTable(migration)
      CommonSteps.applyIndexesToDiffsTable(migration)
      CommonSteps.analyzeDiffsTable(migration)
    }

    if (pendingDiffswidener.requiresNewTable()) {
      CommonSteps.applyConstraintsToPendingDiffsTable(migration)
      CommonSteps.applyIndexesToPendingDiffsTable(migration)
    }

    migration
  }
}
