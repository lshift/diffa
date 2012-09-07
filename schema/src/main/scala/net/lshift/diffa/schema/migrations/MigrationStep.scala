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

/**
 * Performs a database migration
 */
trait MigrationStep {

  /**
   * The version that this step gets the database to.
   */
  def versionId:Int

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
}
