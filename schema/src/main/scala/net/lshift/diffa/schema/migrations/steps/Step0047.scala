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
import scala.collection.JavaConversions.mapAsJavaMap

/**
 * Create the entity for the new Category, RollingWindowCategory.
 */
object Step0047 extends VerifiedMigrationStep {
  def versionId = 47

  def name = "Endpoint View Rolling Windows"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.createTable("endpoint_view_rolling_windows").
      column("domain", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("view_name", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("period", Types.VARCHAR, 50, true).
      column("offset", Types.VARCHAR, 50, true).
      pk("domain", "endpoint", "name")

    // Beware of changing column order in referential constraint definitions.
    // Column names here are ignored when the schema is built on Oracle.
    migration.alterTable("endpoint_view_rolling_windows").
      addForeignKey("fk_evrw_epvw", Array("domain", "endpoint", "view_name"), "endpoint_views", Array("domain", "endpoint", "name")).
      addForeignKey("fk_evrw_ucvn",
      Array("domain", "endpoint", "name", "view_name"),
      "unique_category_view_names", Array("domain", "endpoint", "name", "view_name"))

    // These referential constraints were missing from the earlier steps.
    migration.alterTable("unique_category_view_names").
      addForeignKey("fk_ucvn_ucns", Array("domain", "endpoint", "name"), "unique_category_names", Array("domain", "endpoint", "name"))

    migration.alterTable("range_category_views").
      addForeignKey("fk_racv_racg", Array("domain", "endpoint", "name"), "range_categories", Array("domain", "endpoint", "name"))

    migration.alterTable("prefix_category_views").
      addForeignKey("fk_pfcv_pfcg", Array("domain", "endpoint", "name"), "prefix_categories", Array("domain", "endpoint", "name"))

    migration
  }

  def applyVerification(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    val domainName = randomString()
    val upstream = randomString()
    val viewName = randomString()
    val rollingWindowName = randomString()

    createDomain(migration, domainName)
    createEndpoint(migration, domainName, upstream)
    createEndpointView(migration, domainName, upstream, viewName)

    createRollingWindow(migration, domainName, upstream, viewName, rollingWindowName)

    migration
  }

  private def createDomain(migration: MigrationBuilder, name: String) {
    migration.insert("domains").values(Map(
      "name" -> name
    ))
  }

  private def createEndpoint(migration: MigrationBuilder, domain: String, name: String) {
    migration.insert("endpoint").values(Map(
      "domain" -> domain,
      "name" -> name))
  }

  private def createEndpointView(migration: MigrationBuilder, domain: String, endpoint: String, viewName: String) {
    migration.insert("endpoint_views").values(Map(
      "domain" -> domain,
      "endpoint" -> endpoint,
      "name" -> viewName
    ))
  }

  private def createRollingWindow(migration: MigrationBuilder, domainName: String, endpoint: String, viewName: String, name: String) {
    migration.insert("unique_category_names").values(Map(
      "domain" -> domainName,
      "endpoint" -> endpoint,
      "name" -> name
    ))
    migration.insert("unique_category_view_names").values(Map(
      "domain" -> domainName,
      "endpoint" -> endpoint,
      "view_name" -> viewName,
      "name" -> name
    ))
    migration.insert("endpoint_view_rolling_windows").values(Map(
      "domain" -> domainName,
      "endpoint" -> endpoint,
      "view_name" -> viewName,
      "name" -> name,
      "period" -> "P3M",
      "offset" -> "PT6H"
    ))
  }
}