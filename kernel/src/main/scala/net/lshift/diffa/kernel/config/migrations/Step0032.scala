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
package net.lshift.diffa.kernel.config.migrations

import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.config.{HibernateMigrationStep, MigrationUtil, ConfigOption}
import net.lshift.diffa.kernel.config.limits.{ScanResponseSizeLimit, ExplainFiles, DiagnosticEventBufferSize}

object Step0032 extends HibernateMigrationStep {

  def versionId = 32

  def name = "Add the scan Response Size limit"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    MigrationUtil.insertLimit(migration, ScanResponseSizeLimit)

    migration
  }
}
