package net.lshift.diffa.schema.migrations.steps

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
import net.lshift.diffa.schema.migrations.VerifiedMigrationStep
import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder
import java.sql.Types
import scala.collection.JavaConversions._

object Step0046 extends VerifiedMigrationStep {

  def versionId = 46
  def name = "Add breakers"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.createTable("breakers").
      column("domain", Types.VARCHAR, 50, false).
      column("pair_key", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 255, false).
      pk("domain", "pair_key", "name")

    migration.alterTable("breakers").
      addForeignKey("fk_brkrs_pair", Array("domain", "pair_key"), "pair", Array("domain", "pair_key"))

    migration
  }

  def applyVerification(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    val domain = randomString()
    val upstreamEndpoint = randomString()
    val downstreamEndpoint = randomString()
    val pair = randomString()
    val breaker = randomString()

    migration.insert("domains").values(Map(
      "name"  -> domain
    ))

    migration.insert("endpoint").values(Map(
      "domain"  -> domain,
      "name"    -> upstreamEndpoint
    ))
    migration.insert("endpoint").values(Map(
      "domain"  -> domain,
      "name"    -> downstreamEndpoint
    ))
    migration.insert("pair").values(Map(
      "domain"      -> domain,
      "pair_key"    -> pair,
      "upstream"    -> upstreamEndpoint,
      "downstream"  -> downstreamEndpoint
    ))
    migration.insert("breakers").values(Map(
      "domain"      -> domain,
      "pair_key"    -> pair,
      "name"        -> breaker
    ))

    migration
  }

}
