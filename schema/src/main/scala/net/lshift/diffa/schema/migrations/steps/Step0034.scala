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

import net.lshift.diffa.schema.migrations.HibernateMigrationStep
import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder
import java.sql.Types


object Step0034 extends HibernateMigrationStep {

  def versionId = 34
  def name = "Add the user item visibility table"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.createTable("user_item_visibility").
      column("domain", Types.VARCHAR, 50, false).
      column("pair", Types.VARCHAR, 50, false).
      column("username", Types.VARCHAR, 50, false).
      column("item_type", Types.VARCHAR, 20, false).
      pk("domain", "pair", "username", "item_type")

    migration.alterTable("user_item_visibility").
      addForeignKey("fk_uiv_pair", Array("domain", "pair"), "pair", Array("domain", "pair_key")).
      addForeignKey("fk_uiv_mmbs", Array("domain", "username"), "members", Array("domain_name", "user_name"))

    migration
  }
}
