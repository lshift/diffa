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
package net.lshift.diffa.schema.migrations.steps

import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder
import net.lshift.diffa.schema.servicelimits.{DiagnosticEventBufferSize, ExplainFiles}
import net.lshift.diffa.schema.migrations.{MigrationUtil, HibernateMigrationStep}

object Step0027 extends HibernateMigrationStep {

  def versionId = 27

  def name = "Remove deprecated system config values"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.delete("system_config_options").where("opt_key").is(Step0022.eventExplanationLimitKey)
    migration.delete("system_config_options").where("opt_key").is(Step0022.explainFilesLimitKey)

    MigrationUtil.insertLimit(migration, DiagnosticEventBufferSize)

    MigrationUtil.insertLimit(migration, ExplainFiles)

    migration
  }
}
