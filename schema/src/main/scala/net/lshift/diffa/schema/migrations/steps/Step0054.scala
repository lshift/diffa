/*
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

import net.lshift.diffa.schema.migrations.{MigrationUtil, VerifiedMigrationStep}
import net.lshift.hibernate.migrations.MigrationBuilder
import net.lshift.diffa.schema.configs.InternalCollation
import net.lshift.diffa.schema.servicelimits._

import java.sql.Types
import org.hibernate.cfg.Configuration
import scala.collection.JavaConversions._

/**
 * Add a surrogate key to the endpoint entity.
 */
object Step0054 extends VerifiedMigrationStep {
  /**
   * The version that this step gets the database to.
   */
  def versionId = 54

  /**
   * This is a breaking change. It can only be applied to an empty system.
   */
  override def upgradeableFromVersion(fromVersion: Option[Int]) = fromVersion.isEmpty

  /**
   * The name of this migration step
   */
  def name = "Add surrogate key to endpoints"

  /**
   * Requests that the step create migration builder for doing it's migration.
   */

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    // Step 0 (part 1) - make sure we have some tables to record DB meta data

    migration.createTable("schema_version").
      column("version", Types.INTEGER, false).
      pk("version")

    // Begin creating the schema, so start with spaces, since lots of things depend on them

    migration.createTable("spaces").
      column("id", Types.BIGINT, false).
      column("parent", Types.BIGINT, false, 0).
      column("name", Types.VARCHAR, 50, false).
      column("config_version", Types.INTEGER, 11, false).
      pk("id")

    migration.alterTable("spaces").addForeignKey("fk_uniq_chld", "parent", "spaces", "id")

    migration.createTable("space_paths").
      column("ancestor", Types.BIGINT, false).
      column("descendant", Types.BIGINT, false).
      column("depth", Types.INTEGER, false).
      pk("ancestor", "descendant")

    migration.alterTable("space_paths").addForeignKey("fk_space_par", "ancestor", "spaces", "id")
    migration.alterTable("space_paths").addForeignKey("fk_space_chd", "descendant", "spaces", "id")

    // Let's give ourselves some users and then make sure that they can become space cadets

    migration.createTable("users").
      column("name", Types.VARCHAR, 50, false).
      column("email", Types.VARCHAR, 1024, true).
      column("password_enc", Types.VARCHAR, 100, true).
      column("superuser", Types.BIT, 1, false, 0).
      column("token", Types.VARCHAR, 50, true).
      pk("name")

    migration.alterTable("users").addUniqueConstraint("token")

    migration.createTable("policies").
      column("space", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      pk("space", "name")
    migration.alterTable("policies").
      addForeignKey("fk_plcy_spcs", "space", "spaces", "id")

    // NOTE: A membership also needs to reference the space where the policy was defined.
    //       This allows a policy to be defined at a high level, and then be used and applied
    //       to specific child spaces (ie, define a top level "Admin" policy, but then only apply it to
    //       a specific subspace).
    migration.createTable("members").
      column("space", Types.BIGINT, false).
      column("username", Types.VARCHAR, 50, false).
      column("policy", Types.VARCHAR, 50, false).
      column("policy_space", Types.BIGINT, false).
      pk("space", "username", "policy_space", "policy")

    migration.alterTable("members").
      addForeignKey("fk_mmbs_dmns", "space", "spaces", "id").
      addForeignKey("fk_mmbs_user", "username", "users", "name").
      addForeignKey("fk_mmbs_plcy", Array("policy_space", "policy"), "policies", Array("space", "name"))

    // Now start to create things that are scoped on spaces

    migration.createTable("config_options").
      column("space", Types.BIGINT, false).
      column("opt_key", Types.VARCHAR, 255, false).
      column("opt_val", Types.VARCHAR, 255, true).
      pk("space", "opt_key")

    migration.alterTable("config_options").
      addForeignKey("fk_cfop_spcs", "space", "spaces", "id")

    migration.createTable("endpoints").
      column("space", Types.BIGINT, false).
      column("id", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      column("scan_url", Types.VARCHAR, 1024, true).
      column("content_retrieval_url", Types.VARCHAR, 1024, true).
      column("version_generation_url", Types.VARCHAR, 1024, true).
      column("inbound_url", Types.VARCHAR, 1024, true).
      column("collation_type", Types.VARCHAR, 16, false, "ascii").
      pk("id")

    migration.createIndex("endpoints_space_id_idx", "endpoints", "space", "id")

    migration.alterTable("endpoints").
      addForeignKey("fk_edpt_spcs", "space", "spaces", "id").
      addUniqueConstraint("uk_endpoints_space_id", "space", "id").
      addUniqueConstraint("uk_endpoints_space_name", "space", "name")

    migration.createTable("endpoint_views").
      column("endpoint", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      pk("endpoint", "name")

    migration.alterTable("endpoint_views").
      addForeignKey("fk_epvw_edpt", "endpoint", "endpoints", "id")

    migration.createTable("extents").
      column("id", Types.BIGINT, false).
      pk("id")

    migration.createTable("pairs").
      column("space", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      column("upstream", Types.BIGINT, false).
      column("downstream", Types.BIGINT, false).
      column("extent", Types.BIGINT, false).
      column("version_policy_name", Types.VARCHAR, 50, true).
      column("matching_timeout", Types.INTEGER, true).
      column("scan_cron_spec", Types.VARCHAR, 50, true).
      column("allow_manual_scans", Types.BIT, 1, true, 0).
      column("scan_cron_enabled", Types.BIT, 1, true, 0).
      pk("space", "name")

    migration.alterTable("pairs").
      addForeignKey("fk_pair_spcs", "space", "spaces", "id").
      addForeignKey("fk_pair_exts", "extent", "extents", "id").
      addForeignKey("fk_pair_upstream_edpt", "upstream", "endpoints", "id").
      addForeignKey("fk_pair_downstream_edpt", "downstream", "endpoints", "id")

    migration.alterTable("pairs")
      .addUniqueConstraint("uk_pair_exts", "extent")

    // See note below regarding fk_escl_pair fk constraint.
    migration.sql("alter table pairs add constraint fk_pair_upstream_spc foreign key (space, upstream) references endpoints (space, id)")
    migration.sql("alter table pairs add constraint fk_pair_downstream_spc foreign key (space, downstream) references endpoints (space, id)")

    migration.createTable("escalations").
      column("name", Types.VARCHAR, 50, false).
      column("extent", Types.BIGINT, false).
      column("action", Types.VARCHAR, 50, true).
      column("action_type", Types.VARCHAR, 255, false).
      column("delay", Types.INTEGER, 11, false, 0).
      pk("name", "extent")

    // If you use the builder, to do this, it renders
    // alter table escalations add constraint fk_escl_pair foreign key (extent) references pairs
    // which is uncool, so ultimately we need to patch the builder to handle this, but
    // I wanted to keep this patch small, and this __appears__ to be portable (QA should show this up)

    migration.sql("alter table escalations add constraint fk_escl_pair foreign key (extent) references pairs (extent)")

    migration.createTable("escalation_rules").
      column("id", Types.BIGINT, false).
      column("rule", Types.VARCHAR, 767, false, "*").
      column("extent", Types.BIGINT, true, null).
      column("escalation", Types.VARCHAR, 50, true, null).
      column("previous_extent", Types.BIGINT, false).
      column("previous_escalation", Types.VARCHAR, 50, false).
      pk("id")


    migration.alterTable("escalation_rules").
      addForeignKey("fk_rule_esc", Array("escalation", "extent"), "escalations", Array("name", "extent"))

    migration.alterTable("escalation_rules")
      .addUniqueConstraint("uk_esc_rules_ext", "rule", "previous_extent")

    migration.createTable("diffs").
      column("seq_id", Types.BIGINT, false).
      column("extent", Types.BIGINT, false).
      column("entity_id", Types.VARCHAR, 255, false).
      column("is_match", Types.BIT, false).
      column("detected_at", Types.TIMESTAMP, false).
      column("last_seen", Types.TIMESTAMP, false).
      column("upstream_vsn", Types.VARCHAR, 255, true).
      column("downstream_vsn", Types.VARCHAR, 255, true).
      column("ignored", Types.BIT, false).
      column("next_escalation", Types.BIGINT, true, null).
      column("next_escalation_time", Types.TIMESTAMP, true, null).
      pk("seq_id")

    migration.alterTable("diffs")
      .addForeignKey("fk_diff_ext", "extent", "extents", "id")

    migration.alterTable("diffs").
      addForeignKey("fk_next_esc", "next_escalation", "escalation_rules", "id")

    migration.alterTable("diffs")
      .addUniqueConstraint("uk_diffs", "extent", "entity_id")

    migration.createIndex("diff_last_seen", "diffs", "last_seen")
    migration.createIndex("diff_detection", "diffs", "detected_at")
    migration.createIndex("rdiff_is_matched", "diffs", "is_match")
    migration.createIndex("rdiff_is_ignored", "diffs", "ignored")

    migration.createTable("pending_diffs").
      column("space", Types.BIGINT, false).
      column("pair", Types.VARCHAR, 50, false).
      column("seq_id", Types.BIGINT, false).
      column("entity_id", Types.VARCHAR, 255, false).
      column("detected_at", Types.TIMESTAMP, false).
      column("last_seen", Types.TIMESTAMP, false).
      column("upstream_vsn", Types.VARCHAR, 255, true).
      column("downstream_vsn", Types.VARCHAR, 255, true).
      pk("space", "pair", "seq_id")

    migration.alterTable("pending_diffs")
      .addForeignKey("fk_pddf_pair", Array("space", "pair"), "pairs", Array("space", "name"))

    migration.alterTable("pending_diffs")
      .addUniqueConstraint("uk_pending_diffs", "entity_id", "space", "pair")

    migration.createTable("pair_views").
      column("space", Types.BIGINT, false).
      column("pair", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("scan_cron_spec", Types.VARCHAR, 50, true).
      column("scan_cron_enabled", Types.BIT, 1, true, 0).
      pk("space", "pair", "name")

    migration.alterTable("pair_views").
      addForeignKey("fk_prvw_pair", Array("space", "pair"), "pairs", Array("space", "name"))


    migration.createTable("pair_reports").
      column("space", Types.BIGINT, false).
      column("pair", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("report_type", Types.VARCHAR, 50, false).
      column("target", Types.VARCHAR, 1024, false).
      pk("space", "pair", "name")

    migration.alterTable("pair_reports").
      addForeignKey("fk_prep_pair", Array("space", "pair"), "pairs", Array("space", "name"))

    migration.createTable("unique_category_names").
      column("endpoint", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      pk("endpoint", "name")

    migration.alterTable("unique_category_names").
      addForeignKey("fk_ucns_edpt", "endpoint", "endpoints", "id")

    migration.createTable("prefix_categories").
      column("endpoint", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      column("offset", Types.INTEGER, false).
      pk("endpoint", "name", "offset")

    migration.alterTable("prefix_categories").
      addForeignKey("fk_pfcg_ucns", Array("endpoint", "name"), "unique_category_names", Array("endpoint", "name"))

    migration.createTable("set_categories").
      column("endpoint", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      column("value", Types.VARCHAR, 255, false).
      pk("endpoint", "name", "value")

    migration.alterTable("set_categories").
      addForeignKey("fk_stcg_ucns", Array("endpoint", "name"), "unique_category_names", Array("endpoint", "name"))

    migration.createTable("range_categories").
      column("endpoint", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      column("data_type", Types.VARCHAR, 20, false).
      column("lower_bound", Types.VARCHAR, 255, true).
      column("upper_bound", Types.VARCHAR, 255, true).
      column("max_granularity", Types.VARCHAR, 20, true).
      pk("endpoint", "name")

    migration.alterTable("range_categories").
      addForeignKey("fk_racg_ucns", Array("endpoint", "name"), "unique_category_names", Array("endpoint", "name"))

    migration.createTable("unique_category_view_names").
      column("endpoint", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      column("view_name", Types.VARCHAR, 50, false).
      pk("endpoint", "name", "view_name")

    migration.alterTable("unique_category_view_names").
      addForeignKey("fk_ucvn_evws", Array("endpoint", "view_name"), "endpoint_views", Array("endpoint", "name")).
      addForeignKey("fk_ucvn_ucns", Array("endpoint", "name"), "unique_category_names", Array("endpoint", "name"))

    migration.createTable("prefix_category_views").
      column("endpoint", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      column("view_name", Types.VARCHAR, 50, false).
      column("offset", Types.INTEGER, false).
      pk("endpoint", "name", "view_name", "offset")

    migration.alterTable("prefix_category_views").
      addForeignKey("fk_pfcv_evws", Array("endpoint", "view_name"), "endpoint_views", Array("endpoint", "name")).
      addForeignKey("fk_pfcv_pfcg", Array("endpoint", "name", "offset"), "prefix_categories", Array("endpoint", "name", "offset")).
      addForeignKey("fk_pfcv_ucns", Array("endpoint", "name", "view_name"), "unique_category_view_names", Array("endpoint", "name", "view_name"))

    migration.createTable("set_category_views").
      column("endpoint", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      column("view_name", Types.VARCHAR, 50, false).
      column("value", Types.VARCHAR, 255, false).
      pk("endpoint",  "view_name", "name", "value")

    migration.alterTable("set_category_views").
      addForeignKey("fk_stcv_evws", Array("endpoint", "view_name"), "endpoint_views", Array("endpoint", "name")).
      //addForeignKey("fk_stcv_stcg", Array("endpoint", "name", "value"), "set_categories", Array("endpoint", "name", "value")).
      addForeignKey("fk_stcv_ucns", Array("endpoint", "name", "view_name"), "unique_category_view_names", Array("endpoint", "name", "view_name"))

    migration.createTable("range_category_views").
      column("endpoint", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      column("view_name", Types.VARCHAR, 50, false).
      column("data_type", Types.VARCHAR, 20, false).
      column("lower_bound", Types.VARCHAR, 255, true).
      column("upper_bound", Types.VARCHAR, 255, true).
      column("max_granularity", Types.VARCHAR, 20, true).
      pk("endpoint", "name", "view_name")

    migration.alterTable("range_category_views").
      addForeignKey("fk_racv_evws", Array("endpoint", "view_name"), "endpoint_views", Array("endpoint", "name")).
      addForeignKey("fk_racv_racg", Array("endpoint", "name"), "range_categories", Array("endpoint", "name")).
      addForeignKey("fk_racv_ucns", Array("endpoint", "name", "view_name"), "unique_category_view_names", Array("endpoint", "name", "view_name"))

    migration.createTable("external_http_credentials").
      column("space", Types.BIGINT, false).
      column("url", Types.VARCHAR, 255, false).
      column("cred_key", Types.VARCHAR, 50, false).
      column("cred_value", Types.VARCHAR, 255, false).
      column("cred_type", Types.VARCHAR, 20, false).
      pk("space", "url")

    migration.createTable("breakers").
      column("space", Types.BIGINT, false).
      column("pair", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 255, false).
      pk("space", "pair", "name")

    migration.alterTable("breakers").
      addForeignKey("fk_brkrs_pair", Array("space", "pair"), "pairs", Array("space", "name"))

    migration.alterTable("external_http_credentials").
      addForeignKey("fk_domain_http_creds", "space", "spaces", "id")

    migration.createTable("repair_actions").
      column("space", Types.BIGINT, false).
      column("pair", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("url", Types.VARCHAR, 1024, true).
      column("scope", Types.VARCHAR, 20, true).
      pk("space", "pair", "name")

    migration.alterTable("repair_actions").
      addForeignKey("fk_rpac_pair", Array("space", "pair"), "pairs", Array("space", "name"))

    // Note that scan statements deliberately have no FKs, so that records in this table can outlive deleted pairs/domains
    migration.createTable("scan_statements").
      column("space", Types.BIGINT, false).
      column("pair", Types.VARCHAR, 50, false).
      column("id", Types.BIGINT, false).
      column("initiated_by", Types.VARCHAR, 50, true).
      column("start_time", Types.TIMESTAMP, false).
      column("end_time", Types.TIMESTAMP, 50, true).
      column("state", Types.VARCHAR, 20, false, "STARTED").
      pk("space", "pair", "id")

    migration.createTable("store_checkpoints").
      column("space", Types.BIGINT, false).
      column("pair", Types.VARCHAR, 50, false).
      column("latest_version", Types.BIGINT, false).
      pk("space", "pair")

    migration.alterTable("store_checkpoints").
      addForeignKey("fk_stcp_pair", Array("space", "pair"), "pairs", Array("space", "name"))

    migration.createTable("user_item_visibility").
      column("space", Types.BIGINT, false).
      column("pair", Types.VARCHAR, 50, false).
      column("username", Types.VARCHAR, 50, false).
      column("item_type", Types.VARCHAR, 20, false).
      pk("space", "pair", "username", "item_type")

    // With policies, it is no longer mandatory that a user be a member of a space to have item filtering
    // in that space.  These mechanics are governed by the relationship between members and policies.
    migration.alterTable("user_item_visibility").
      addForeignKey("fk_uiv_pair", Array("space", "pair"), "pairs", Array("space", "name")).
      addForeignKey("fk_uiv_user", Array("username"), "users", Array("name"))

    // Limits

    migration.createTable("limit_definitions").
      column("name", Types.VARCHAR, 50, false).
      column("description", Types.VARCHAR, 255, false).
      pk("name")

    migration.createTable("system_limits").
      column("name", Types.VARCHAR, 50, false).
      column("default_limit", Types.INTEGER, 11, false, 0).
      column("hard_limit", Types.INTEGER, 11, false, 0).
      pk("name")

    migration.createTable("space_limits").
      column("space", Types.BIGINT, false).
      column("name", Types.VARCHAR, 50, false).
      column("default_limit", Types.INTEGER, 11, false, 0).
      column("hard_limit", Types.INTEGER, 11, false, 0).
      pk("space", "name")

    migration.createTable("pair_limits").
      column("space", Types.BIGINT, false).
      column("pair", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("limit_value", Types.INTEGER, 11, false, 0).
      pk("space", "pair", "name")

    migration.alterTable("system_limits").
      addForeignKey("fk_system_limit_service_limit", "name", "limit_definitions", "name")

    migration.alterTable("space_limits").
      addForeignKey("fk_domain_limit_service_limit", "name", "limit_definitions", "name").
      addForeignKey("fk_domain_limit_space", "space", "spaces", "id")

    migration.alterTable("pair_limits").
      addForeignKey("fk_pair_limit_service_limit", "name", "limit_definitions", "name").
      addForeignKey("fk_pair_limit_pair", Array("space", "pair"), "pairs", Array("space", "name"))

    // Non-space-specific stuff

    migration.createTable("system_config_options").
      column("opt_key", Types.VARCHAR, 255, false).
      column("opt_val", Types.VARCHAR, 255, false).
      pk("opt_key")

    // Access control policies

    migration.createTable("privilege_names").
      column("name", Types.VARCHAR, 50, false).
      pk("name")

    migration.createTable("policy_statements").
      column("space", Types.BIGINT, false).
      column("policy", Types.VARCHAR, 50, false).
      column("privilege", Types.VARCHAR, 50, false).
      column("target", Types.VARCHAR, 50, true).
      pk("space", "policy", "privilege", "target")
    migration.alterTable("policy_statements").
      addForeignKey("fk_plcy_stmts_plcy", "space", "spaces", "id").
      addForeignKey("fk_plcy_stmts_priv", "privilege", "privilege_names", "name")

    // Endpoint View Rolling Windows
    migration.createTable("endpoint_view_rolling_windows").
      column("endpoint", Types.BIGINT, false).
      column("view_name", Types.VARCHAR, 50, false).
      column("name", Types.VARCHAR, 50, false).
      column("period", Types.VARCHAR, 50, true).
      column("offset", Types.VARCHAR, 50, true).
      pk("endpoint", "name")

    // Beware of changing column order in referential constraint definitions.
    // Column names here are ignored when the schema is built on Oracle.
    migration.alterTable("endpoint_view_rolling_windows").
      addForeignKey("fk_evrw_epvw", Array("endpoint", "view_name"), "endpoint_views", Array("endpoint", "name")).
      addForeignKey("fk_evrw_ucvn", Array("endpoint", "name", "view_name"), "unique_category_view_names", Array("endpoint", "name", "view_name"))


    // Prime with initial data

    migration.insert("system_config_options").values(Map(
      "opt_key" -> InternalCollation.key,
      "opt_val" -> InternalCollation.defaultValue))

    migration.insert("spaces").values(Map(
      "id" -> "0",
      "name" -> "diffa",
      "config_version" -> "0"
    ))

    migration.insert("space_paths").values(Map(
      "ancestor"    -> "0",
      "descendant"  -> "0",
      "depth"       -> "0"
    ))

    migration.insert("config_options").values(Map(
      "space" -> "0",
      "opt_key" -> "configStore.schemaVersion",
      "opt_val" -> "0"
    ))

    migration.insert("users").values(Map(
      "name" -> "guest",
      "email" -> "guest@diffa.io",
      "password_enc" -> "84983c60f7daadc1cb8698621f802c0d9f9a3c3c295c810748fb048115c186ec",
      "superuser" -> "1"
    ))

    migration.insert("schema_version").values(Map(
      "version" -> new java.lang.Integer(versionId)
    ))

    definePrivileges(migration,
      "space-user", "read-diffs", "configure", "initiate-scan", "cancel-scan", "post-change-event",
      "post-inventory", "view-scan-status", "view-diagnostics", "invoke-actions", "ignore-diffs", "view-explanations",
      "execute-report", "view-actions", "view-reports", "read-event-details")

    // Replacement policy for indicating a domain user
    createPolicy(migration, "0", "User", "space-user")

    // Full-access admin policy
    createPolicy(migration, "0", "Admin", "space-user", "read-diffs", "configure", "initiate-scan", "cancel-scan", "post-change-event",
      "post-inventory", "view-scan-status", "view-diagnostics", "invoke-actions", "ignore-diffs", "view-explanations",
      "execute-report", "view-actions", "view-reports", "read-event-details")

    migration.insert("members").values(Map(
      "username" -> "guest",
      "space"    -> "0",
      "policy_space" -> "0",
      "policy" -> "Admin"
    ))

    MigrationUtil.insertLimit(migration, ChangeEventRate)
    MigrationUtil.insertLimit(migration, DiagnosticEventBufferSize)
    MigrationUtil.insertLimit(migration, ExplainFiles)
    MigrationUtil.insertLimit(migration, ScanConnectTimeout)
    MigrationUtil.insertLimit(migration, ScanReadTimeout)
    MigrationUtil.insertLimit(migration, ScanResponseSizeLimit)

    if (migration.canAnalyze) {
      migration.analyzeTable("diffs")
    }

    migration
  }

  /**
   * This allows for a step to insert data into the database to prove this step works
   * and to provide an existing state for a subsequent migration to use
   */
  def applyVerification(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    // Shared setup

    createRandomSubspace(migration)

    val spaceName = randomString()
    val spaceId = randomInt()

    createSpace(migration, spaceId, "0", spaceName)

    val user = randomString()

    createConfigOption(migration, spaceId)

    val upstreamName = randomString()
    val upstream = randomInt()
    val downstreamName = randomString()
    val downstream = randomInt()

    val upstreamView = randomString()
    val downstreamView = randomString()

    createEndpoint(migration, spaceId, upstreamName, upstream)
    createEndpointView(migration, upstream, upstreamView)
    createEndpoint(migration, spaceId, downstreamName, downstream)
    createEndpointView(migration, downstream, downstreamView)

    createUser(migration, user)

    val extent = randomInt()

    createExtent(migration, extent)

    val pair = randomString()

    createPair(migration, spaceId, pair, extent, upstream, downstream)
    createPairView(migration, spaceId, pair)

    val escalationName = randomString()

    createEscalation(migration, extent, escalationName)

    val ruleId = randomInt()

    createEscalationRule(migration, ruleId, extent, escalationName)

    createDiff(migration, spaceId, pair, extent, ruleId)
    createPendingDiff(migration, spaceId, pair)

    createPairReport(migration, spaceId, pair)

    val limitName = randomString()

    createLimitDefinition(migration, limitName)
    createSystemLimit(migration, limitName)
    createSpaceLimit(migration, spaceId, limitName)
    createPairLimit(migration, spaceId, pair, limitName)

    val prefixCategoryName = randomString()

    createUniqueCategoryName(migration, upstream, prefixCategoryName)
    createUniqueCategoryViewName(migration, upstream, upstreamView, prefixCategoryName)
    createPrefixCategory(migration, upstream, prefixCategoryName)
    createPrefixCategoryView(migration, upstream, upstreamView, prefixCategoryName)

    val setCategoryName = randomString()

    createUniqueCategoryName(migration, upstream, setCategoryName)
    createUniqueCategoryViewName(migration, upstream, upstreamView, setCategoryName)
    createSetCategory(migration, upstream, setCategoryName)
    createSetCategoryView(migration, upstream, upstreamView, setCategoryName)

    val rangeCategoryName = randomString()

    createUniqueCategoryName(migration, downstream, rangeCategoryName)
    createUniqueCategoryViewName(migration, downstream, downstreamView, rangeCategoryName)
    createRangeCategory(migration, downstream, rangeCategoryName)
    createRangeCategoryView(migration, downstream, downstreamView, rangeCategoryName)

    createExternalHttpCredentials(migration, spaceId)

    createRepairAction(migration, spaceId, pair)

    createScanStatement(migration, spaceId, pair)

    createStoreCheckpoint(migration, spaceId, pair)

    // Policies
    val policy = randomString()
    val privilege1 = randomString()
    val privilege2 = randomString()

    definePrivileges(migration, privilege1, privilege2)
    createPolicy(migration, spaceId, policy, privilege1, privilege2)
    createMember(migration, spaceId, user, spaceId, policy)

    // User item visibility
    createUserItemVisibility(migration, spaceId, pair, user)

    // Endpoint View Rolling Windows
    val viewName = randomString()
    val rollingWindowName = randomString()
    createEndpointView(migration, upstream, viewName)
    createRollingWindow(migration, upstream, viewName, rollingWindowName)

    migration
  }

  def createBreaker(migration:MigrationBuilder, spaceId:String, pair:String) {
    migration.insert("breakers").values(Map(
      "space" -> spaceId,
      "pair" -> pair,
      "name" -> randomString()
    ))
  }

  def createUserItemVisibility(migration:MigrationBuilder, spaceId:String, pair:String, user:String) {
    migration.insert("user_item_visibility").values(Map(
      "space" -> spaceId,
      "pair" -> pair,
      "username" -> user,
      "item_type" -> "SWIM_LANE"
    ))
  }

  def createUser(migration:MigrationBuilder, user:String) {
    migration.insert("users").values(Map(
      "name" -> user,
      "email" -> "foo@bar.com",
      "password_enc" -> randomString(),
      "token" -> randomString(),
      "superuser" -> "1"
    ))
  }

  def createStoreCheckpoint(migration:MigrationBuilder, spaceId:String, pair:String) {
    migration.insert("store_checkpoints").values(Map(
      "space" -> spaceId,
      "pair" -> pair,
      "latest_version" -> randomInt()
    ))
  }

  def createScanStatement(migration:MigrationBuilder, spaceId:String, pair:String) {
    migration.insert("scan_statements").values(Map(
      "space" -> spaceId,
      "pair" -> pair,
      "id" -> randomInt(),
      "initiated_by" -> randomString(),
      "start_time" -> randomTimestamp(),
      "end_time" -> randomTimestamp(),
      "state" -> "COMPLETED"
    ))
  }

  def createRepairAction(migration:MigrationBuilder, spaceId:String, pair:String) {
    migration.insert("repair_actions").values(Map(
      "space" -> spaceId,
      "pair" -> pair,
      "name" -> randomString(),
      "url" -> "http://someurl.com/repair",
      "scope" -> "entity"
    ))
  }

  def createExternalHttpCredentials(migration:MigrationBuilder, spaceId:String) {
    migration.insert("external_http_credentials").values(Map(
      "space" -> spaceId,
      "url" -> "http://someurl.com/ajax",
      "cred_key" -> randomString(),
      "cred_value" -> randomString(),
      "cred_type" -> "basic_auth"
    ))
  }

  def createRangeCategory(migration:MigrationBuilder, endpoint:String, name:String) {
    migration.insert("range_categories").values(Map(
      "endpoint" -> endpoint,
      "name" -> name,
      "data_type" -> "date",
      "lower_bound" -> "2001-10-10",
      "upper_bound" -> "2001-10-11",
      "max_granularity" -> "yearly"
    ))
  }

  def createRangeCategoryView(migration:MigrationBuilder, endpoint:String, view:String, name:String) {
    migration.insert("range_category_views").values(Map(
      "endpoint" -> endpoint,
      "name" -> name,
      "view_name" -> view,
      "data_type" -> "date",
      "lower_bound" -> "2001-10-10",
      "upper_bound" -> "2001-10-11",
      "max_granularity" -> "yearly"
    ))
  }

  def createSetCategory(migration:MigrationBuilder, endpoint:String, name:String) {
    migration.insert("set_categories").values(Map(
      "endpoint" -> endpoint,
      "name" -> name,
      "value" -> randomString()
    ))
  }

  def createSetCategoryView(migration:MigrationBuilder, endpoint:String, view:String, name:String) {
    migration.insert("set_category_views").values(Map(
      "endpoint" -> endpoint,
      "name" -> name,
      "view_name" -> view,
      "value" -> randomString()
    ))
  }

  def createPrefixCategoryView(migration:MigrationBuilder, endpoint:String, view:String, name:String) {
    migration.insert("prefix_category_views").values(Map(
      "endpoint" -> endpoint,
      "name" -> name,
      "view_name" -> view,
      "offset" -> "1"
    ))
  }

  def createPrefixCategory(migration:MigrationBuilder, endpoint:String, name:String) {
    migration.insert("prefix_categories").values(Map(
      "endpoint" -> endpoint,
      "name" -> name,
      "offset" -> "1"
    ))
  }

  def createPairLimit(migration:MigrationBuilder, spaceId:String, pair:String, limit:String) {
    migration.insert("pair_limits").values(Map(
      "space" -> spaceId,
      "pair" -> pair,
      "name" -> limit,
      "limit_value" -> "100"
    ))
  }

  def createSpaceLimit(migration:MigrationBuilder, spaceId:String, limit:String) {
    migration.insert("space_limits").values(Map(
      "space" -> spaceId,
      "name" -> limit,
      "default_limit" -> "10",
      "hard_limit" -> "100"
    ))
  }

  def createSystemLimit(migration:MigrationBuilder, limit:String) {
    migration.insert("system_limits").values(Map(
      "name" -> limit,
      "default_limit" -> "10",
      "hard_limit" -> "100"
    ))
  }

  def createLimitDefinition(migration:MigrationBuilder, limit:String) {
    migration.insert("limit_definitions").values(Map(
      "name" -> limit,
      "description" -> randomString()
    ))
  }

  def createPairReport(migration:MigrationBuilder, spaceId:String, pair:String) {
    migration.insert("pair_reports").values(Map(
      "space" -> spaceId,
      "pair" -> pair,
      "name" -> randomString(),
      "report_type" -> "differences",
      "target" -> "http://example.com/bulk_diff_handler"
    ))
  }

  def createPendingDiff(migration:MigrationBuilder, spaceId:String, pair:String) {
    migration.insert("pending_diffs").values(Map(
      "space" -> spaceId,
      "pair" -> pair,
      "seq_id" -> randomInt(),
      "entity_id" -> randomString(),
      "upstream_vsn" -> randomString(),
      "downstream_vsn" -> randomString(),
      "detected_at" -> randomTimestamp(),
      "last_seen" -> randomTimestamp()
    ))
  }

  def createDiff(migration:MigrationBuilder, spaceId:String, pair:String, extent:String, ruleId:String) {
    migration.insert("diffs").values(Map(
      "seq_id" -> randomInt(),
      "entity_id" -> randomString(),
      "extent" -> extent,
      "is_match" -> "0",
      "ignored" -> "0",
      "upstream_vsn" -> randomString(),
      "downstream_vsn" -> randomString(),
      "detected_at" -> randomTimestamp(),
      "last_seen" -> randomTimestamp(),
      "next_escalation" -> ruleId,
      "next_escalation_time" -> randomTimestamp()
    ))

  }

  def createEscalationRule(migration:MigrationBuilder, ruleId:String, extent:String, escalation:String) {
    migration.insert("escalation_rules").values(Map(
      "id" -> ruleId,
      "rule" -> "mismatch",
      "previous_extent" -> extent,
      "previous_escalation" -> escalation
    ))

    val update = "update escalation_rules set extent = %s, previous_escalation = '%s'".format(extent, escalation)
    val predicate = " where previous_extent = %s and previous_escalation = '%s'".format(extent, escalation)

    migration.sql(update + predicate)
  }

  def createEscalation(migration:MigrationBuilder, extent:String, name:String) {
    migration.insert("escalations").values(Map(
      "extent" -> extent,
      "name" -> name,
      "action" -> randomString(),
      "action_type" -> "ignore",
      "delay" -> "10"
    ))
  }

  def createPairView(migration:MigrationBuilder, spaceId:String, parent:String) {
    migration.insert("pair_views").values(Map(
      "space" -> spaceId,
      "pair" -> parent,
      "name" -> randomString(),
      "scan_cron_spec" -> "0 0 * * * ?"
    ))
  }

  def createPair(migration:MigrationBuilder, spaceId:String, name:String, extent:String, upstream:String, downstream:String) {
    migration.insert("pairs").values(Map(
      "space" -> spaceId,
      "name" -> name,
      "extent" -> extent,
      "upstream" -> upstream,
      "downstream" -> downstream,
      "version_policy_name" -> "same",
      "matching_timeout" -> "0",
      "scan_cron_spec" -> "0 0 * * * ?"
    ))
  }

  def createConfigOption(migration:MigrationBuilder, spaceId:String) {
    migration.insert("config_options").values(Map(
      "space" -> spaceId,
      "opt_key" -> randomString(),
      "opt_val" -> randomString()
    ))
  }

  def createExtent(migration:MigrationBuilder, id:String) {
    migration.insert("extents").values(Map(
      "id" -> id
    ))
  }

  def createRandomSubspace(migration:MigrationBuilder) {
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
  }

  def definePrivileges(migration:MigrationBuilder, privileges:String*) {
    privileges.foreach(p =>
      migration.insert("privilege_names").values(Map(
        "name" -> p
      ))
    )
  }

  def createPolicy(migration:MigrationBuilder, spaceId:String, name:String, privileges:String*) {
    migration.insert("policies").values(Map(
      "space" -> spaceId,
      "name" -> name
    ))

    privileges.foreach(p =>
      migration.insert("policy_statements").values(Map(
        "space" -> spaceId,
        "policy" -> name,
        "privilege" -> p,
        "target" -> "*"
      ))
    )
  }

  def createMember(migration:MigrationBuilder, spaceId:String, user:String, policySpaceId:String, policy:String) {
    migration.insert("members").values(Map(
      "space" -> spaceId,
      "username" -> user,
      "policy_space" -> policySpaceId,
      "policy" -> policy
    ))
  }

  private def createRollingWindow(migration: MigrationBuilder, endpoint: String, viewName: String, name: String) {
    migration.insert("unique_category_names").values(Map(
      "endpoint" -> endpoint,
      "name" -> name
    ))
    migration.insert("unique_category_view_names").values(Map(
      "endpoint" -> endpoint,
      "view_name" -> viewName,
      "name" -> name
    ))
    migration.insert("endpoint_view_rolling_windows").values(Map(
      "endpoint" -> endpoint,
      "view_name" -> viewName,
      "name" -> name,
      "period" -> "P3M",
      "offset" -> "PT6H"
    ))
  }
}
