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

import net.lshift.diffa.schema.migrations.MigrationStep
import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder
import net.lshift.diffa.schema.configs.InternalCollation
import scala.collection.JavaConversions._

object Step0040 extends MigrationStep {
  def versionId = 40
  def name = "Add the internal collation system property"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.insert("system_config_options").values(Map(
      "opt_key" -> InternalCollation.key,
      "opt_val" -> InternalCollation.defaultValue))

    migration
  }
}
