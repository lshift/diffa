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
import net.lshift.diffa.schema.migrations.VerifiedMigrationStep
import java.sql.Types
import scala.collection.JavaConversions._

object Step0041 extends VerifiedMigrationStep {

  def versionId = 41

  def name = "Split out categories for endpoints and views"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    // 1, Create the new target tables for all of the view based data and wire in all of the constraints

    migration.createTable("unique_category_view_names").
      column("domain", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("view_name", Types.VARCHAR, 50, false).
      pk("domain", "endpoint", "name", "view_name")

    migration.alterTable("unique_category_view_names").
      addForeignKey("fk_ucvn_evws", Array("domain", "endpoint", "view_name"), "endpoint_views", Array("domain", "endpoint", "name"))

    migration.createTable("prefix_category_views").
      column("domain", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("view_name", Types.VARCHAR, 50, false).
      column("prefix_length", Types.INTEGER, true).
      column("max_length", Types.INTEGER, true).
      column("step", Types.INTEGER, true).
      pk("domain", "endpoint", "view_name", "name")

    migration.alterTable("prefix_category_views").
      addForeignKey("fk_pfcv_evws", Array("domain", "endpoint", "view_name"), "endpoint_views", Array("domain", "endpoint", "name"))

    migration.alterTable("prefix_category_views").
      addForeignKey("fk_pfcv_ucns", Array("domain", "endpoint", "name", "view_name"), "unique_category_view_names", Array("domain", "endpoint", "name", "view_name"))

    migration.createTable("set_category_views").
      column("domain", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("view_name", Types.VARCHAR, 50, false).
      column("value", Types.VARCHAR, 255, false).
      pk("domain", "endpoint",  "view_name", "name", "value")

    migration.alterTable("set_category_views").
      addForeignKey("fk_stcv_evws", Array("domain", "endpoint", "view_name"), "endpoint_views", Array("domain", "endpoint", "name"))

    migration.alterTable("set_category_views").
      addForeignKey("fk_stcv_ucns", Array("domain", "endpoint", "name", "view_name"), "unique_category_view_names", Array("domain", "endpoint", "name", "view_name"))

    migration.createTable("range_category_views").
      column("domain", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("view_name", Types.VARCHAR, 50, false).
      column("data_type", Types.VARCHAR, 20, false).
      column("lower_bound", Types.VARCHAR, 255, true).
      column("upper_bound", Types.VARCHAR, 255, true).
      column("max_granularity", Types.VARCHAR, 20, true).
      pk("domain", "endpoint", "name", "view_name")

    migration.alterTable("range_category_views").
      addForeignKey("fk_racv_evws", Array("domain", "endpoint", "view_name"), "endpoint_views", Array("domain", "endpoint", "name"))

    migration.alterTable("range_category_views").
      addForeignKey("fk_racv_ucns", Array("domain", "endpoint", "name", "view_name"), "unique_category_view_names", Array("domain", "endpoint", "name", "view_name"))


    // 2. Extract the data specific to views from the old table layout and populate the view specific tables with this

    migration.copyTableContents("unique_category_names", "unique_category_view_names",
                                Seq("domain", "endpoint", "name", "view_name"),
                                Seq("domain", "endpoint", "name", "view_name")).
              whereSource(Map("target_type" -> "endpoint_views"))

    migration.copyTableContents("prefix_categories", "prefix_category_views",
      Seq("domain", "endpoint", "name", "view_name", "prefix_length", "max_length", "step"),
      Seq("domain", "endpoint", "name", "view_name", "prefix_length", "max_length", "step")).
      whereSource(Map("target_type" -> "endpoint_views"))

    migration.copyTableContents("set_categories", "set_category_views",
      Seq("domain", "endpoint", "name", "view_name", "value"),
      Seq("domain", "endpoint", "name", "view_name", "value")).
      whereSource(Map("target_type" -> "endpoint_views"))

    migration.copyTableContents("range_categories", "range_category_views",
      Seq("domain", "endpoint", "name", "view_name", "data_type", "lower_bound", "upper_bound", "max_granularity"),
      Seq("domain", "endpoint", "name", "view_name", "data_type", "lower_bound", "upper_bound", "max_granularity")).
      whereSource(Map("target_type" -> "endpoint_views"))

    // 3. Nuke the view data specific to views from the old table layout

    migration.delete("prefix_categories").where("target_type").is("endpoint_views")
    migration.delete("set_categories").where("target_type").is("endpoint_views")
    migration.delete("range_categories").where("target_type").is("endpoint_views")
    migration.delete("unique_category_names").where("target_type").is("endpoint_views")

    // 4. Remove the view_name and target_type columns from the old table layout

    migration.alterTable("prefix_categories").dropForeignKey("fk_pfcg_ucns")
    migration.alterTable("prefix_categories").dropForeignKey("fk_pfcg_evws")
    migration.alterTable("prefix_categories").dropPrimaryKey()
    migration.alterTable("prefix_categories").dropColumn("target_type")
    migration.alterTable("prefix_categories").dropColumn("view_name")
    migration.alterTable("prefix_categories").addPrimaryKey("domain", "endpoint", "name")

    migration.alterTable("set_categories").dropForeignKey("fk_stcg_ucns")
    migration.alterTable("set_categories").dropForeignKey("fk_stcg_evws")
    migration.alterTable("set_categories").dropPrimaryKey()
    migration.alterTable("set_categories").dropColumn("target_type")
    migration.alterTable("set_categories").dropColumn("view_name")
    migration.alterTable("set_categories").addPrimaryKey("domain", "endpoint", "name", "value")

    migration.alterTable("range_categories").dropForeignKey("fk_racg_ucns")
    migration.alterTable("range_categories").dropForeignKey("fk_racg_evws")
    migration.alterTable("range_categories").dropPrimaryKey()
    migration.alterTable("range_categories").dropColumn("target_type")
    migration.alterTable("range_categories").dropColumn("view_name")
    migration.alterTable("range_categories").addPrimaryKey("domain", "endpoint", "name")

    migration.alterTable("unique_category_names").dropForeignKey("fk_ucns_evws")
    migration.alterTable("unique_category_names").dropPrimaryKey()
    migration.alterTable("unique_category_names").dropColumn("target_type")
    migration.alterTable("unique_category_names").dropColumn("view_name")
    migration.alterTable("unique_category_names").addPrimaryKey("domain", "endpoint", "name")

    migration.alterTable("prefix_categories").
      addForeignKey("fk_pfcg_ucns", Array("domain", "endpoint", "name"), "unique_category_names", Array("domain", "endpoint", "name"))

    migration.alterTable("set_categories").
      addForeignKey("fk_stcg_ucns", Array("domain", "endpoint", "name"), "unique_category_names", Array("domain", "endpoint", "name"))

    migration.alterTable("range_categories").
      addForeignKey("fk_racg_ucns", Array("domain", "endpoint", "name"), "unique_category_names", Array("domain", "endpoint", "name"))

    migration
  }

  def applyVerification(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    val domain = randomString()
    val upstreamEndpoint = randomString()
    val upstreamEndpointView = randomString()
    val downstreamEndpoint = randomString()
    val downstreamEndpointView = randomString()

    // 1. Set up the domain with an upstream and a downstream endpoint

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

    migration.insert("endpoint_views").values(Map(
      "domain"    -> domain,
      "endpoint"  -> upstreamEndpoint,
      "name"      -> upstreamEndpointView
    ))

    migration.insert("endpoint_views").values(Map(
      "domain"    -> domain,
      "endpoint"  -> downstreamEndpoint,
      "name"      -> downstreamEndpointView
    ))

    // 2. Add a range category to each of the upstream and downstream parents

    migration.insert("unique_category_names").values(Map(
      "domain"    -> domain,
      "endpoint"  -> upstreamEndpoint,
      "name"      -> "some-date-based-category"
    ))

    migration.insert("unique_category_names").values(Map(
      "domain"    -> domain,
      "endpoint"  -> downstreamEndpoint,
      "name"      -> "some-date-based-category"
    ))

    migration.insert("range_categories").values(Map(
      "domain"          -> domain,
      "endpoint"        -> upstreamEndpoint,
      "name"            -> "some-date-based-category",
      "data_type"       -> "date",
      "lower_bound"     -> "1999-10-10",
      "upper_bound"     -> "1999-10-11",
      "max_granularity" -> "daily"
    ))

    migration.insert("range_categories").values(Map(
      "domain"          -> domain,
      "endpoint"        -> downstreamEndpoint,
      "name"            -> "some-date-based-category",
      "data_type"       -> "date",
      "lower_bound"     -> "1999-10-10",
      "upper_bound"     -> "1999-10-11",
      "max_granularity" -> "daily"
    ))

    // 3. Add a range category to each of the respective view

    migration.insert("unique_category_view_names").values(Map(
      "domain"    -> domain,
      "endpoint"  -> upstreamEndpoint,
      "name"      -> "some-date-based-category",
      "view_name" -> upstreamEndpointView
    ))

    migration.insert("unique_category_view_names").values(Map(
      "domain"    -> domain,
      "endpoint"  -> downstreamEndpoint,
      "name"      -> "some-date-based-category",
      "view_name" -> downstreamEndpointView
    ))

    migration.insert("range_category_views").values(Map(
      "domain"          -> domain,
      "endpoint"        -> upstreamEndpoint,
      "name"            -> "some-date-based-category",
      "view_name"       -> upstreamEndpointView,
      "data_type"       -> "date",
      "lower_bound"     -> "1999-10-10",
      "upper_bound"     -> "1999-10-11",
      "max_granularity" -> "daily"
    ))

    migration.insert("range_category_views").values(Map(
      "domain"          -> domain,
      "endpoint"        -> downstreamEndpoint,
      "name"            -> "some-date-based-category",
      "view_name"       -> downstreamEndpointView,
      "data_type"       -> "date",
      "lower_bound"     -> "1999-10-10",
      "upper_bound"     -> "1999-10-11",
      "max_granularity" -> "daily"
    ))

    // Make sure that set and prefix categories can also still be migrated (each time just on one of the endpoints, this will probably suffice)

    // 4. Put a prefix category with a view onto the upstream

    migration.insert("unique_category_names").values(Map(
      "domain"    -> domain,
      "endpoint"  -> upstreamEndpoint,
      "name"      -> "some-prefix-based-category"
    ))

    migration.insert("prefix_categories").values(Map(
      "domain"          -> domain,
      "endpoint"        -> upstreamEndpoint,
      "name"            -> "some-prefix-based-category",
      "prefix_length"   -> "1",
      "max_length"      -> "2",
      "step"            -> "1"
    ))

    migration.insert("unique_category_view_names").values(Map(
      "domain"    -> domain,
      "endpoint"  -> upstreamEndpoint,
      "name"      -> "some-prefix-based-category",
      "view_name" -> upstreamEndpointView
    ))

    migration.insert("prefix_category_views").values(Map(
      "domain"          -> domain,
      "endpoint"        -> upstreamEndpoint,
      "name"            -> "some-prefix-based-category",
      "view_name"       -> upstreamEndpointView,
      "prefix_length"   -> "1",
      "max_length"      -> "2",
      "step"            -> "1"
    ))

    // 5. Put a set category with a view onto the downstream

    migration.insert("unique_category_names").values(Map(
      "domain"    -> domain,
      "endpoint"  -> downstreamEndpoint,
      "name"      -> "some-set-based-category"
    ))

    migration.insert("set_categories").values(Map(
      "domain"          -> domain,
      "endpoint"        -> downstreamEndpoint,
      "name"            -> "some-set-based-category",
      "value"           -> randomString()
    ))

    migration.insert("unique_category_view_names").values(Map(
      "domain"    -> domain,
      "endpoint"  -> downstreamEndpoint,
      "name"      -> "some-set-based-category",
      "view_name" -> downstreamEndpointView
    ))

    migration.insert("set_category_views").values(Map(
      "domain"          -> domain,
      "endpoint"        -> downstreamEndpoint,
      "name"            -> "some-set-based-category",
      "view_name"       -> downstreamEndpointView,
      "value"           -> randomString()
    ))

    migration
  }
}
