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
package net.lshift.diffa.schema.migrations

import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder
import org.apache.commons.lang.RandomStringUtils
import java.sql.Timestamp
import scala.collection.JavaConversions.mapAsJavaMap

/**
 * Performs a database migration
 */
trait MigrationStep {

  /**
   * The version that this step gets the database to.
   */
  def versionId:Int

  /**
   * The minimum version that this step supports upgrade from.
   */
  def upgradeableFromVersion(fromVersion: Option[Int]) = true

  /**
   * The name of this migration step
   */
  def name:String

  /**
   * Requests that the step create migration builder for doing it's migration.
   */
  def createMigration(config:Configuration):MigrationBuilder

}

trait VerifiedMigrationStep extends MigrationStep {

  /**
   * This allows for a step to insert data into the database to prove this step works
   * and to provide an existing state for a subsequent migration to use
   */
  def applyVerification(config:Configuration):MigrationBuilder

  def randomString() = RandomStringUtils.randomAlphanumeric(10)
  def randomInt() = RandomStringUtils.randomNumeric(7)
  def randomTimestamp() = new Timestamp(System.currentTimeMillis())

  def createSpace(migration: MigrationBuilder, id: String, parentId: String, name: String) {
    migration.insert("spaces").values(Map(
      "id" -> id,
      "parent" -> parentId,
      "name" -> name,
      "config_version" -> "0"
    ))

    migration.insert("space_paths").values(Map(
      "ancestor"  -> id,
      "descendant"   -> id,
      "depth"   -> "0"
    ))

    migration.insert("space_paths").values(Map(
      "ancestor"  -> parentId,
      "descendant"   -> id,
      "depth"   -> "0"
    ))
  }

  def createEndpoint(migration:MigrationBuilder, spaceId:String, endpoint:String, endpointId: String) {
    migration.insert("endpoints").values(Map(
      "space" -> spaceId,
      "id" -> endpointId,
      "name" -> endpoint,
      "scan_url" -> randomString(),
      "content_retrieval_url" -> randomString(),
      "version_generation_url" -> randomString(),
      "inbound_url" -> randomString(),
      "collation_type" -> "ascii"
    ))
  }

  def createEndpointView(migration:MigrationBuilder, endpoint:String, name:String) {
    migration.insert("endpoint_views").values(Map(
      "endpoint" -> endpoint,
      "name" -> name
    ))
  }

  def createUniqueCategoryName(migration:MigrationBuilder, endpoint:String, name:String) {
    migration.insert("unique_category_names").values(Map(
      "endpoint" -> endpoint,
      "name" -> name
    ))
  }

  def createUniqueCategoryViewName(migration: MigrationBuilder, endpoint: String, view: String, name: String) {
    migration.insert("unique_category_view_names").values(Map(
      "endpoint" -> endpoint,
      "name" -> name,
      "view_name" -> view
    ))
  }
}
