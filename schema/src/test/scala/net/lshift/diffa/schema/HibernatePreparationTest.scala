/**
 * Copyright (C) 2010-2011 LShift Ltd.
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

package net.lshift.diffa.kernel.config

import org.junit.runner.RunWith
import org.junit.Assert._
import org.junit.experimental.theories.Theories
import org.junit.Test
import org.slf4j.LoggerFactory
import org.hibernate.dialect.Dialect
import net.lshift.diffa.schema.cleaner.SchemaCleaner
import net.lshift.diffa.schema.environment.{DatabaseEnvironment, TestDatabaseEnvironments}
import net.lshift.diffa.schema.migrations.HibernateConfigStorePreparationStep

/**
 * Test cases for ensuring that preparation steps apply to database schemas at various levels, and allow us to upgrade
 * any version to any other.
 */

@RunWith(classOf[Theories])
class HibernatePreparationTest {

  val log = LoggerFactory.getLogger(getClass)

  val startingVersion = 51
  
  @Test
  def migrationStepsShouldBeOrderedCorrectly = {
    val steps = HibernateConfigStorePreparationStep.migrationSteps.toList
    for (i <- 0 until steps.length) {
      val msg = "Attempting to verify version id of step [%s]".format(steps(i).name)
      assertEquals(msg, i + startingVersion, steps(i).versionId)
    }
  }

  /**
   * From an empty schema, the deployer should be able to upgrade any supported system to the latest version.
   *
   * The associated DDL statements are named (differentiated by suffix) accordingly and are located at run-time.
   */
  @Test
  def shouldBeAbleToUpgradeToLatestDatabaseVersion {
    val adminEnvironment = TestDatabaseEnvironments.adminEnvironment
    val databaseEnvironment = TestDatabaseEnvironments.uniqueEnvironment("target/configStore")

    // Given
    cleanSchema(adminEnvironment, databaseEnvironment)
    
    val dbConfig = databaseEnvironment.getHibernateConfigurationWithoutMappingResources
    val sessionFactory = dbConfig.buildSessionFactory

    // Then
    log.info("Installing schema and upgrading to latest version")
    (new HibernateConfigStorePreparationStep).prepare(sessionFactory, dbConfig, true)

    sessionFactory.close
  }

  /**
   * Attempting to upgrade a schema at the latest version should be a silent no op.
   */
  @Test
  def rerunUpgradeOnLatestVersionShouldSilentlyPassWithoutEffect {
    val adminEnvironment = TestDatabaseEnvironments.adminEnvironment
    val databaseEnvironment = TestDatabaseEnvironments.uniqueEnvironment("target/configStore")

    // Given
    cleanSchema(adminEnvironment, databaseEnvironment)

    val dbConfig = databaseEnvironment.getHibernateConfigurationWithoutMappingResources
    val sessionFactory = dbConfig.buildSessionFactory

    log.info("Installing schema and upgrading to latest version")
    (new HibernateConfigStorePreparationStep).prepare(sessionFactory, dbConfig)

    // When
    log.info("A further attempt to upgrade to the latest version silently passes")
    (new HibernateConfigStorePreparationStep).prepare(sessionFactory, dbConfig)

    sessionFactory.close
  }

  /**
   * Drop and recreate the named schema/database in order to provide a clean slate to test from.
   */
  private def cleanSchema(sysenv: DatabaseEnvironment, appenv: DatabaseEnvironment) {
    val sysConfig = sysenv.getHibernateConfigurationWithoutMappingResources

    val cleaner = SchemaCleaner.forDialect(Dialect.getDialect(sysConfig.getProperties))
    cleaner.clean(sysenv, appenv)
  }

}
