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
import org.apache.commons.lang.RandomStringUtils
import scala.collection.JavaConversions._

object Step0039 extends VerifiedMigrationStep {
  def versionId = 39
  def name = "Add the 'guest' user as a member of domain 'diffa'"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    migration.sql(
      """insert into members (domain_name, user_name)
        select d.name, u.name
        from domains d, users u
        where d.name = '%s'
        and u.name = '%s'
        group by d.name, u.name""".format(
        Step0022.defaultDomainName, Step0022.defaultUserName))

    migration
  }

  /**
   * This allows for a step to insert data into the database to prove this step works
   * and to provide an existing state for a subsequent migration to use
   */
  def applyVerification(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    val user = Step0022.defaultUserName
    val domain = Step0022.defaultDomainName
    val (up, down) = (randomString(10), randomString(10))
    val pair = randomString(10)
    val itemType = randomString(10)

    Seq(up,down) foreach { ep =>
      migration.insert("endpoint").values(Map(
        "domain" -> domain,
        "name" -> ep
      ))
    }

    migration.insert("pair").values(Map(
      "domain" -> domain,
      "pair_key" -> pair,
      "upstream" -> up,
      "downstream" -> down
    ))

    migration.insert("user_item_visibility").values(Map(
      "domain" -> domain,
      "pair" -> pair,
      "username" -> user,
      "item_type" -> itemType)
    )

    migration
  }

  def randomString(length: Int) = RandomStringUtils.randomAlphanumeric(length)
}
