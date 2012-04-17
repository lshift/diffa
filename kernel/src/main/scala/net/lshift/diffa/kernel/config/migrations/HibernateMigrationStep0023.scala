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
import net.lshift.diffa.kernel.config.{ConfigOption, HibernateMigrationStep}
import net.lshift.hibernate.migrations.{SelectBuilder, MigrationBuilder}
import java.sql.Types

/**
 * The introduction of the system wide limit for the event log buffer set the value to zero,
 * although the primary objective of this change was to limit the amount of explain log files on disk,
 * as opposed to limiting the size of the diagnostic log buffer.
 */
object HibernateMigrationStep0023 extends HibernateMigrationStep {

  def versionId = 23

  def name = "Change the default per pair event log buffer size to the system maximum"

  def createMigration(config: Configuration) = {

    val migration = new MigrationBuilder(config)

    migration.alterTable("pair")
             .setColumnNullable("events_to_log", Types.INTEGER, 11, true)
             .setColumnNullable("max_explain_files", Types.INTEGER, 11, true);

    migration.updateTable("pair")
             .updateColumn("events_to_log")
             .withNullValue()
             .predicate("opt_key", ConfigOption.eventExplanationLimitKey)
             .predicate("opt_val", "0")

    migration.updateTable("system_config_options")
             .updateColumn("opt_key")
             .withValue(ConfigOption.DIAGNOSTIC_LOG_BUFFER_SIZE)
             .predicate("opt_key", ConfigOption.eventExplanationLimitKey)

    migration.updateTable("system_config_options")
             .updateColumn("opt_key")
             .withValue(ConfigOption.EXPLAIN_FILES_LIMIT)
             .predicate("opt_key", ConfigOption.explainFilesLimitKey)

    migration
  }

}
