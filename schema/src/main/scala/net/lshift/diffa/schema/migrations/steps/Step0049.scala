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
import net.lshift.diffa.schema.migrations.VerifiedMigrationStep
import net.lshift.hibernate.migrations.MigrationBuilder
import java.sql.Types
import scala.collection.JavaConversions._

object Step0049 extends VerifiedMigrationStep {

  def versionId = 49
  def name = "Introduce sub spaces"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.alterTable("spaces").
      addColumn("parent", Types.BIGINT, false, 0)

    migration.alterTable("spaces").addForeignKey("fk_uniq_chld", "parent", "spaces", "id")

    migration.createTable("space_paths").
      column("ancestor", Types.BIGINT, false).
      column("descendant", Types.BIGINT, false).
      column("depth", Types.INTEGER, false).
      pk("ancestor", "descendant")

    migration.alterTable("space_paths").addForeignKey("fk_space_par", "ancestor", "spaces", "id")
    migration.alterTable("space_paths").addForeignKey("fk_space_chd", "descendant", "spaces", "id")

    migration.insert("space_paths").values(Map(
      "ancestor"    -> "0",
      "descendant"  -> "0",
      "depth"       -> "0"
    ))

    migration
  }

  def applyVerification(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    val parentId = randomInt()
    val childId = randomInt()
    val parentName = randomString()
    val childName = randomString()

    migration.insert("spaces").values(Map(
      "id"                -> parentId,
      "name"              -> parentName,
      "parent"            -> "0",
      "config_version"    -> "0"
    ))

    migration.insert("space_paths").values(Map(
      "ancestor"  -> parentId,
      "descendant"   -> parentId,
      "depth"   -> "0"
    ))

    migration.insert("spaces").values(Map(
      "id"                -> childId,
      "name"              -> childName,
      "config_version"    -> "0"
    ))

    migration.insert("space_paths").values(Map(
      "ancestor"  -> childId,
      "descendant"   -> childId,
      "depth"   -> "0"
    ))

    migration.insert("space_paths").values(Map(
      "ancestor"  -> parentId,
      "descendant"   -> childId,
      "depth"   -> "1"
    ))

    migration
  }
}
