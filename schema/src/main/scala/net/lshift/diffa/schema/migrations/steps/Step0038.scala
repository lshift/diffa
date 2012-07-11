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
import java.sql.Types
import scala.collection.JavaConversions._

object Step0038 extends MigrationStep {

  def versionId = 38

  def name = "De-hibernatize category storage"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    // Create the parent table that needs to get inserted into to make sure that each child category has a unique name

    migration.createTable("unique_category_names").
      column("domain", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("view_name", Types.VARCHAR, 50, true).
      column("target_type", Types.VARCHAR, 20, false, "endpoint").
      pk("domain", "endpoint", "name", "target_type")

    migration.alterTable("unique_category_names").
      addForeignKey("fk_ucns_edpt", Array("domain", "endpoint"), "endpoint", Array("domain", "name"))

    migration.alterTable("unique_category_names").
      addForeignKey("fk_ucns_evws", Array("domain", "endpoint", "view_name"), "endpoint_views", Array("domain", "endpoint", "name"))

    // Create the new table for prefix categories

    migration.createTable("prefix_categories").
      column("domain", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("prefix_length", Types.INTEGER, true).
      column("max_length", Types.INTEGER, true).
      column("step", Types.INTEGER, true).
      column("view_name", Types.VARCHAR, 50, true).
      column("target_type", Types.VARCHAR, 20, false, "endpoint").
      pk("domain", "endpoint", "name", "target_type")

    migration.alterTable("prefix_categories").
      addForeignKey("fk_pfcg_evws", Array("domain", "endpoint", "view_name"), "endpoint_views", Array("domain", "endpoint", "name"))

    // Make sure names across all category tables are unique

    migration.alterTable("prefix_categories").
      addForeignKey("fk_pfcg_ucns", Array("domain", "endpoint", "name", "target_type"), "unique_category_names", Array("domain", "endpoint", "name", "target_type"))

    // Create a parent record for all to-be-migrated prefix categories on endpoints proper

    migration.copyTableContents("category_descriptor", "unique_category_names",
      Seq("domain", "name", "endpoint")).
      join("endpoint_categories", "category_descriptor_id", "category_id", Seq("domain", "name", "id")).
      whereSource(Map("constraint_type" -> "prefix")).
      withConstant("target_type", "endpoint")

    // Migrate all prefix categories on endpoints proper

    migration.copyTableContents("category_descriptor", "prefix_categories",
      Seq("prefix_length", "max_length", "step"),
      Seq("prefix_length", "max_length", "step", "domain", "name", "endpoint")).
      join("endpoint_categories", "category_descriptor_id", "category_id", Seq("domain", "name", "id")).
      whereSource(Map("constraint_type" -> "prefix")).
      withConstant("target_type", "endpoint")

    // Create a parent record for all to-be-migrated prefix categories on endpoint views

    migration.copyTableContents("category_descriptor", "unique_category_names",
      Seq("domain", "name", "endpoint")).
      join("endpoint_views_categories", "category_descriptor_id", "category_id", Seq("domain", "category_name", "endpoint")).
      whereSource(Map("constraint_type" -> "prefix")).
      withConstant("target_type", "endpoint_view")

    // Migrate all prefix categories on endpoint views

    migration.copyTableContents("category_descriptor", "prefix_categories",
      Seq("prefix_length", "max_length", "step"),
      Seq("prefix_length", "max_length", "step", "domain", "name", "endpoint", "view_name")).
      join("endpoint_views_categories", "category_descriptor_id", "category_id", Seq("domain", "category_name", "endpoint", "name")).
      whereSource(Map("constraint_type" -> "prefix")).
      withConstant("target_type", "endpoint_view")

    // Nuke old prefix descriptors

    migration.dropTable("prefix_category_descriptor")

    // Create the new table for set categories

    migration.createTable("set_categories").
      column("domain", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("value", Types.VARCHAR, 255, false).
      column("view_name", Types.VARCHAR, 50, true).
      column("target_type", Types.VARCHAR, 20, false, "endpoint").
      pk("domain", "endpoint", "name", "target_type", "value")

    migration.alterTable("set_categories").
      addForeignKey("fk_stcg_evws", Array("domain", "endpoint", "view_name"), "endpoint_views", Array("domain", "endpoint", "name"))

    // Make sure names across all category tables are unique

    migration.alterTable("set_categories").
      addForeignKey("fk_stcg_ucns", Array("domain", "endpoint", "name", "target_type"), "unique_category_names", Array("domain", "endpoint", "name", "target_type"))

    // Create a parent record for all to-be-migrated set categories on endpoints proper

    migration.copyTableContents("category_descriptor", "unique_category_names",
      Seq("domain", "name", "endpoint")).
      join("endpoint_categories", "category_descriptor_id", "category_id", Seq("domain", "name", "id")).
      whereSource(Map("constraint_type" -> "set")).
      withConstant("target_type", "endpoint")

    // Migrate all set categories on endpoints proper

    migration.copyTableContents("category_descriptor", "set_categories",
      Seq("domain", "name", "endpoint", "value")).
      join("endpoint_categories", "category_descriptor_id", "category_id", Seq("domain", "name", "id")).
      join("set_constraint_values", "value_id", "category_id", Seq("value_name")).
      whereSource(Map("constraint_type" -> "set")).
      withConstant("target_type", "endpoint")

    // Create a parent record for all to-be-migrated set categories on endpoint views

    migration.copyTableContents("category_descriptor", "unique_category_names",
      Seq("domain", "name", "endpoint")).
      join("endpoint_views_categories", "category_descriptor_id", "category_id", Seq("domain", "category_name", "endpoint")).
      whereSource(Map("constraint_type" -> "set")).
      withConstant("target_type", "endpoint_view")

    // Migrate all set categories on endpoint views

    migration.copyTableContents("category_descriptor", "set_categories",
      Seq("domain", "name", "endpoint", "view_name", "value")).
      join("endpoint_views_categories", "category_descriptor_id", "category_id", Seq("domain", "category_name", "endpoint", "name")).
      join("set_constraint_values", "value_id", "category_id", Seq("value_name")).
      whereSource(Map("constraint_type" -> "set")).
      withConstant("target_type", "endpoint_view")

    // Nuke old set descriptors and their associated values

    migration.dropTable("set_category_descriptor")
    migration.dropTable("set_constraint_values")

    migration.createTable("range_categories").
      column("domain", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("data_type", Types.VARCHAR, 20, false).
      column("lower_bound", Types.VARCHAR, 255, true).
      column("upper_bound", Types.VARCHAR, 255, true).
      column("max_granularity", Types.VARCHAR, 20, true).
      column("view_name", Types.VARCHAR, 50, true).
      column("target_type", Types.VARCHAR, 20, false, "endpoint").
      pk("domain", "endpoint", "name", "target_type")

    migration.alterTable("range_categories").
      addForeignKey("fk_racg_evws", Array("domain", "endpoint", "view_name"), "endpoint_views", Array("domain", "endpoint", "name"))

    // Make sure names across all category tables are unique

    migration.alterTable("range_categories").
      addForeignKey("fk_racg_ucns", Array("domain", "endpoint", "name", "target_type"), "unique_category_names", Array("domain", "endpoint", "name", "target_type"))

    // Create a parent record for all to-be-migrated range categories on endpoints proper

    migration.copyTableContents("category_descriptor", "unique_category_names",
      Seq("domain", "name", "endpoint")).
      join("endpoint_categories", "category_descriptor_id", "category_id", Seq("domain", "name", "id")).
      whereSource(Map("constraint_type" -> "range")).
      withConstant("target_type", "endpoint")

    // Migrate all range categories on endpoints proper

    migration.copyTableContents("category_descriptor", "range_categories",
      Seq("domain", "name", "endpoint", "data_type","lower_bound", "upper_bound", "max_granularity")).
      join("endpoint_categories", "category_descriptor_id", "category_id", Seq("domain", "name", "id")).
      join("range_category_descriptor", "id", "category_id", Seq("data_type","lower_bound", "upper_bound", "max_granularity")).
      whereSource(Map("constraint_type" -> "range")).
      withConstant("target_type", "endpoint")

    // Create a parent record for all to-be-migrated range categories on endpoint views

    migration.copyTableContents("category_descriptor", "unique_category_names",
      Seq("domain", "name", "endpoint")).
      join("endpoint_views_categories", "category_descriptor_id", "category_id", Seq("domain", "category_name", "endpoint")).
      whereSource(Map("constraint_type" -> "range")).
      withConstant("target_type", "endpoint_view")

    // Migrate all range categories on endpoint views

    migration.copyTableContents("category_descriptor", "range_categories",
      Seq("domain", "name", "endpoint", "view_name", "data_type","lower_bound", "upper_bound", "max_granularity")).
      join("endpoint_views_categories", "category_descriptor_id", "category_id", Seq("domain", "category_name", "endpoint", "name")).
      join("range_category_descriptor", "id", "category_id", Seq("data_type","lower_bound", "upper_bound", "max_granularity")).
      whereSource(Map("constraint_type" -> "range")).
      withConstant("target_type", "endpoint_view")

    // Nuke old range descriptors

    migration.dropTable("range_category_descriptor")

    // Blast away the n:m tables that linked the various category tables with the respective endpoint and endpoint views tables

    migration.dropTable("endpoint_categories")
    migration.dropTable("endpoint_views_categories")

    migration
  }
}
