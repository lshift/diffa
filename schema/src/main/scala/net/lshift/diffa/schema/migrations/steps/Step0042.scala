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

import net.lshift.diffa.schema.migrations.VerifiedMigrationStep
import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder
import java.sql.Types
import scala.collection.JavaConversions._

object Step0042 extends VerifiedMigrationStep {

  def versionId = 42
  def name = "Add scan summary table"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.createTable("scan_statements").
      column("id", Types.BIGINT, false).
      column("domain", Types.VARCHAR, 50, false).
      column("pair", Types.VARCHAR, 50, false).
      column("intiated_by", Types.VARCHAR, 50, true).
      column("start_time", Types.TIMESTAMP, false).
      column("end_time", Types.TIMESTAMP, 50, true).
      column("state", Types.VARCHAR, 20, false, "STARTED").
      pk("domain", "pair", "id")

    migration
  }

  def applyVerification(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.insert("scan_statements").values(Map(
      "domain"      -> randomString(),
      "pair"        -> randomString(),
      "id"          -> randomInt(),
      "intiated_by" -> randomString(),
      "start_time"  -> randomTimestamp(),
      "end_time"    -> randomTimestamp(),
      "state"       -> "COMPLETED"
    ))

    migration
  }

}
