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
import java.sql.Types
import scala.collection.JavaConversions._
import net.lshift.hibernate.migrations.MigrationBuilder
import net.lshift.diffa.schema.migrations.{DefinePartitionInformationTable, CommonSteps, HibernateMigrationStep}

/**
 * This Step 'migrates' a schema/database to version 22 -
 * that is, it creates the base schema from scratch.
 */
object Step0022 extends HibernateMigrationStep {
  def versionId = 22

  def name = "Create schema"

  @Deprecated val eventExplanationLimitKey = "maxEventsToExplainPerPair"
  @Deprecated val explainFilesLimitKey = "maxExplainFilesPerPair"
  val defaultDomainName = "diffa"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.createTable("category_descriptor").
      column("category_id", Types.INTEGER, false).
      column("constraint_type", Types.VARCHAR, 20, false).
      column("prefix_length", Types.INTEGER, true).
      column("max_length", Types.INTEGER, true).
      column("step", Types.INTEGER, true).
      pk("category_id").
      withNativeIdentityGenerator()

    migration.createTable("config_options").
      column("domain", Types.VARCHAR, 50, false, defaultDomainName).
      column("opt_key", Types.VARCHAR, 255, false).
      column("opt_val", Types.VARCHAR, 255, true).
      pk("opt_key", "domain")

    // In this DB version, the PK of this table is an integer -> defaults to bigint otherwise
    val diffsTable = CommonSteps.buildDiffsTable(migration.createTable("diffs"), Types.INTEGER)

    // N.B. include the partition info table on all DBs (support may be added in future)
    DefinePartitionInformationTable.defineTable(migration)

    if (migration.canUseListPartitioning) {
      diffsTable.virtualColumn("partition_name", Types.VARCHAR, 512, "domain || '_' || pair").
        listPartitioned("partition_name").
        listPartition("part_dummy_default", "default")

      DefinePartitionInformationTable.applyPartitionVersion(migration, "diffs", versionId)

      migration.executeDatabaseScript("sync_pair_diff_partitions", "net.lshift.diffa.schema.procedures")
    }

    migration.createTable("domains").
      column("name", Types.VARCHAR, 50, false).
      pk("name")

    migration.createTable("endpoint").
      column("domain", Types.VARCHAR, 50, false, defaultDomainName).
      column("name", Types.VARCHAR, 50, false).
      column("scan_url", Types.VARCHAR, 1024, true).
      column("content_retrieval_url", Types.VARCHAR, 1024, true).
      column("version_generation_url", Types.VARCHAR, 1024, true).
      column("inbound_url", Types.VARCHAR, 1024, true).
      pk("domain", "name")

    migration.createTable("endpoint_categories").
      column("domain", Types.VARCHAR, 50, false, defaultDomainName).
      column("id", Types.VARCHAR, 50, false).
      column("category_descriptor_id", Types.INTEGER, false).
      column("name", Types.VARCHAR, 50, false).
      pk("id", "name")

    migration.createTable("endpoint_views").
      column("domain", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      pk("domain", "endpoint", "name")

    migration.createTable("endpoint_views_categories").
      column("domain", Types.VARCHAR, 50, false).
      column("endpoint", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("category_name", Types.VARCHAR, 50, false).
      column("category_descriptor_id", Types.INTEGER, false).
      pk("domain", "endpoint", "category_name", "name")

    migration.createTable("escalations").
      column("domain", Types.VARCHAR, 50, false, defaultDomainName).
      column("name", Types.VARCHAR, 50, false).
      column("pair_key", Types.VARCHAR, 50, false).
      column("action", Types.VARCHAR, 50, false).
      column("action_type", Types.VARCHAR, 255, false).
      column("event", Types.VARCHAR, 255, false).
      column("origin", Types.VARCHAR, 255, true).
      pk("pair_key", "name")

    migration.createTable("members").
      column("domain_name", Types.VARCHAR, 50, false).
      column("user_name", Types.VARCHAR, 50, false).
      pk("domain_name", "user_name")

    migration.createTable("pair").
      column("domain", Types.VARCHAR, 50, false, defaultDomainName).
      column("pair_key", Types.VARCHAR, 50, false).
      column("upstream", Types.VARCHAR, 50, false).
      column("downstream", Types.VARCHAR, 50, false).
      column("version_policy_name", Types.VARCHAR, 50, true).
      column("matching_timeout", Types.INTEGER, true).
      column("scan_cron_spec", Types.VARCHAR, 50, true).
      column("allow_manual_scans", Types.BIT, 1, true, 0).
      column("events_to_log", Types.INTEGER, 11, false, 0).
      column("max_explain_files", Types.INTEGER, 11, false, 0).
      pk("domain", "pair_key")

    migration.createTable("pair_reports").
      column("domain", Types.VARCHAR, 50, false).
      column("pair_key", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("report_type", Types.VARCHAR, 50, false).
      column("target", Types.VARCHAR, 1024, false).
      pk("domain", "pair_key", "name")

    migration.createTable("pair_views").
      column("domain", Types.VARCHAR, 50, false).
      column("pair", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("scan_cron_spec", Types.VARCHAR, 50, true).
      pk("domain", "pair", "name")

    // In this DB version, the PK of this table is an integer -> defaults to bigint otherwise
    CommonSteps.buildPendingDiffsTable(migration.createTable("pending_diffs"), Types.INTEGER)

    migration.createTable("prefix_category_descriptor").
      column("id", Types.INTEGER, false).
      pk("id")

    migration.createTable("range_category_descriptor").
      column("id", Types.INTEGER, false).
      column("data_type", Types.VARCHAR, 20, true).
      column("upper_bound", Types.VARCHAR, 255, true).
      column("lower_bound", Types.VARCHAR, 255, true).
      column("max_granularity", Types.VARCHAR, 20, true).
      pk("id")

    migration.createTable("repair_actions").
      column("domain", Types.VARCHAR, 50, false, defaultDomainName).
      column("pair_key", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("url", Types.VARCHAR, 1024, true).
      column("scope", Types.VARCHAR, 20, true).
      pk("pair_key", "name")

    migration.createTable("schema_version").
      column("version", Types.INTEGER, false).
      pk("version")

    migration.createTable("set_category_descriptor").
      column("id", Types.INTEGER, false).
      pk("id")

    migration.createTable("set_constraint_values").
      column("value_id", Types.INTEGER, false).
      column("value_name", Types.VARCHAR, 255, false).
      pk("value_id", "value_name")

    migration.createTable("store_checkpoints").
      column("domain", Types.VARCHAR, 50, false).
      column("pair", Types.VARCHAR, 50, false).
      column("latest_version", Types.BIGINT, false).
      pk("domain", "pair")

    migration.createTable("system_config_options").
      column("opt_key", Types.VARCHAR, 255, false).
      column("opt_val", Types.VARCHAR, 255, false).
      pk("opt_key")

    migration.createTable("users").
      column("name", Types.VARCHAR, 50, false).
      column("email", Types.VARCHAR, 1024, true).
      column("password_enc", Types.VARCHAR, 100, false, "LOCKED").
      column("superuser", Types.BIT, 1, false, 0).
      column("token", Types.VARCHAR, 50, true).
      pk("name")


    migration.alterTable("config_options").
      addForeignKey("fk_cfop_dmns", "domain", "domains", "name")

    CommonSteps.applyConstraintsToDiffsTable(migration)

    migration.alterTable("endpoint").
      addForeignKey("fk_edpt_dmns", "domain", "domains", "name")

    migration.alterTable("endpoint_categories").
      addForeignKey("fk_epct_edpt", Array("domain", "id"), "endpoint", Array("domain", "name")).
      addForeignKey("fk_epct_ctds", "category_descriptor_id", "category_descriptor", "category_id")

    migration.alterTable("endpoint_views").
      addForeignKey("fk_epvw_edpt", Array("domain", "endpoint"), "endpoint", Array("domain", "name"))

    migration.alterTable("endpoint_views_categories").
      addForeignKey("fk_epvc_ctds", Array("category_descriptor_id"), "category_descriptor", Array("category_id"))

    migration.alterTable("escalations").
      addForeignKey("fk_escl_pair", Array("domain", "pair_key"), "pair", Array("domain", "pair_key"))

    migration.alterTable("pair").
      addForeignKey("fk_pair_dmns", "domain", "domains", "name").
      addForeignKey("fk_pair_upstream_edpt", Array("domain", "upstream"), "endpoint", Array("domain", "name")).
      addForeignKey("fk_pair_downstream_edpt", Array("domain", "downstream"), "endpoint", Array("domain", "name"))

    migration.alterTable("pair_reports").
      addForeignKey("fk_prep_pair", Array("domain", "pair_key"), "pair", Array("domain", "pair_key"))

    migration.alterTable("pair_views").
      addForeignKey("fk_prvw_pair", Array("domain", "pair"), "pair", Array("domain", "pair_key"))

    migration.alterTable("members").
      addForeignKey("fk_mmbs_dmns", "domain_name", "domains", "name").
      addForeignKey("fk_mmbs_user", "user_name", "users", "name")

    CommonSteps.applyConstraintsToPendingDiffsTable(migration)

    migration.alterTable("prefix_category_descriptor").
      addForeignKey("fk_pfcd_ctds", "id", "category_descriptor", "category_id")

    migration.alterTable("range_category_descriptor").
      addForeignKey("fk_rctd_ctds", "id", "category_descriptor", "category_id")

    migration.alterTable("repair_actions").
      addForeignKey("fk_rpac_pair", Array("domain", "pair_key"), "pair", Array("domain", "pair_key"))

    migration.alterTable("set_category_descriptor").
      addForeignKey("fk_sctd_ctds", "id", "category_descriptor", "category_id")

    migration.alterTable("set_constraint_values").
      addForeignKey("fk_sctv_ctds", "value_id", "category_descriptor", "category_id")

    migration.alterTable("store_checkpoints").
      addForeignKey("fk_stcp_pair", Array("domain", "pair"), "pair", Array("domain", "pair_key"))

    migration.alterTable("users").
      addUniqueConstraint("token")


    CommonSteps.applyIndexesToDiffsTable(migration)
    CommonSteps.applyIndexesToPendingDiffsTable(migration)


    migration.insert("domains").values(Map("name" -> defaultDomainName))

    migration.insert("config_options").
      values(Map("domain" -> defaultDomainName, "opt_key" -> "configStore.schemaVersion", "opt_val" -> "0"))

    migration.insert("system_config_options").values(Map(
      "opt_key" -> eventExplanationLimitKey,
      "opt_val" -> "100"))

    migration.insert("system_config_options").values(Map(
      "opt_key" -> explainFilesLimitKey,
      "opt_val" -> "20"))

    migration.insert("users").
      values(Map(
      "name" -> "guest", "email" -> "guest@diffa.io",
      "password_enc" -> "84983c60f7daadc1cb8698621f802c0d9f9a3c3c295c810748fb048115c186ec",
      "superuser" -> Boolean.box(true)))

    migration.insert("schema_version").
      values(Map("version" -> new java.lang.Integer(versionId)))


    CommonSteps.analyzeDiffsTable(migration)


    migration
  }
}
