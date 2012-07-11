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

import java.sql._
import org.junit.runner.RunWith
import org.junit.Assert._
import org.junit.experimental.theories.Theories
import org.junit.Test
import org.slf4j.LoggerFactory
import org.hibernate.dialect.Dialect
import org.hibernate.cfg.{Configuration, Environment}
import org.hibernate.jdbc.Work
import org.hibernate.tool.hbm2ddl.DatabaseMetadata

import org.hibernate.{Session, SessionFactory}
import net.lshift.diffa.schema.cleaner.SchemaCleaner
import net.lshift.diffa.schema.environment.{DatabaseEnvironment, TestDatabaseEnvironments}
import net.lshift.diffa.schema.migrations.HibernateConfigStorePreparationStep
import net.lshift.diffa.schema.hibernate.SessionHelper.sessionFactoryToSessionHelper

/**
 * Test cases for ensuring that preparation steps apply to database schemas at various levels, and allow us to upgrade
 * any version to any other.
 */

@RunWith(classOf[Theories])
class HibernatePreparationTest {

  val log = LoggerFactory.getLogger(getClass)

  // The Hibernate validateSchema method won't check for too-many tables being present, presumably since this won't
  // adversely affect it's operation. Since we do care that we delete some objects, we'll have a specific ban-list of
  // objects that we don't want to be present.
  val invalidTables = Seq(
    "pair_group",                      // Removed as of the v1 migration
    "prefix_category_descriptor",      // Removed as of the v38 migration
    "set_category_descriptor",         // Removed as of the v38 migration
    "set_constraint_values",           // Removed as of the v38 migration
    "range_category_descriptor",       // Removed as of the v38 migration
    "endpoint_categories",             // Removed as of the v38 migration
    "endpoint_views_categories"        // Removed as of the v38 migration
  )
  val invalidColumns = Map(
    "pair" -> Seq(
      "name",               // Removed as part of the v1 migration
      "events_to_log",      // Removed as part of the v31 migration
      "max_explain_files"   // Removed as part of the v31 migration
    ),
    "config_options" -> Seq(
      "is_internal"    // Removed as part of the v3 migration
    ),
    "endpoint" -> Seq(
      "inbound_content_type",   // Removed as part of the v13 migration
      "content_type"   // Removed as of the v15 migration
    )
  )

  val startingVersion = 22
  
  @Test
  def migrationStepsShouldBeOrderedCorrectly = {
    val steps = HibernateConfigStorePreparationStep.migrationSteps
    for (i <- startingVersion until steps.length) {
      val msg = "Attempting to verify version id of step [%s]".format(steps(i).name)
      assertEquals(msg, i, steps(i).versionId)
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

    // When
    log.info("Installing schema and upgrading to latest version")
    (new HibernateConfigStorePreparationStep).prepare(sessionFactory, dbConfig, true)

    // Then
    log.info("Validating the correctness of the schema")
    validateSchema(sessionFactory, dbConfig)

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

    // Then
    log.info("Validating the correctness of the schema")
    validateSchema(sessionFactory, dbConfig)

    sessionFactory.close
  }

  @Test
  def verifyExternalDatabase() {
    if(System.getProperty("verifyExternalDB") != null) {
      val config = new Configuration().
      addResource("net/lshift/diffa/kernel/config/Config.hbm.xml").
      addResource("net/lshift/diffa/kernel/config/SystemConfig.hbm.xml").
      addResource("net/lshift/diffa/kernel/config/ServiceLimits.hbm.xml").
      addResource("net/lshift/diffa/kernel/differencing/DifferenceEvents.hbm.xml").
      addResource("net/lshift/diffa/kernel/differencing/Differences.hbm.xml").
      setProperty("hibernate.dialect", DatabaseEnvironment.HIBERNATE_DIALECT).
      setProperty("hibernate.connection.url", DatabaseEnvironment.substitutableURL("configStore-export")).
      setProperty("hibernate.connection.driver_class", DatabaseEnvironment.DRIVER).
      setProperty("hibernate.connection.username", DatabaseEnvironment.USERNAME).
      setProperty("hibernate.connection.password", DatabaseEnvironment.PASSWORD).
      setProperty("hibernate.cache.region.factory_class", "net.sf.ehcache.hibernate.EhCacheRegionFactory").
      setProperty("hibernate.cache.use_second_level_cache", "true")
      val sf = config.buildSessionFactory
      (new HibernateConfigStorePreparationStep).prepare(sf, config)
    }
  }

  private def validateSchema(sessionFactory: SessionFactory, config: Configuration) {
    val dialect = Dialect.getDialect(config.getProperties)
    val metadata = sessionFactory.withSession(s => retrieveMetadata(s, dialect))

    assertNotSame(None, metadata)
    config.validateSchema(dialect, metadata.get)
    validateNotTooManyObjects(config, metadata.get)
  }

  /**
   * Drop and recreate the named schema/database in order to provide a clean slate to test from.
   */
  private def cleanSchema(sysenv: DatabaseEnvironment, appenv: DatabaseEnvironment) {
    val sysConfig = sysenv.getHibernateConfigurationWithoutMappingResources

    val cleaner = SchemaCleaner.forDialect(Dialect.getDialect(sysConfig.getProperties))
    cleaner.clean(sysenv, appenv)
  }
  
  private def retrieveMetadata(session: Session, dialect: Dialect): Option[DatabaseMetadata] = {
    var metadata: Option[DatabaseMetadata] = None

    session.doWork(new Work() {
      def execute(connection: Connection) {
        metadata = Some(new DatabaseMetadata(connection, dialect))
      }
    })

    metadata
  }

  /**
   * Since hibernate only validates for presence, check for things we know should be gone
   */
  private def validateNotTooManyObjects(config:Configuration, dbMetadata:DatabaseMetadata) {
    val defaultCatalog = config.getProperties.getProperty(Environment.DEFAULT_CATALOG)
    val defaultSchema = config.getProperties.getProperty(Environment.DEFAULT_SCHEMA)

    invalidTables.foreach(invalidTable =>
      assertNull("Table '" + invalidTable + "' should not be present in the database",
        dbMetadata.getTableMetadata(invalidTable, defaultSchema, defaultCatalog, false)))
    invalidColumns.keys.foreach(owningTable =>
      invalidColumns(owningTable).foreach(col =>
        assertNull("Column '" + col + "' should not be present in table '" + owningTable + "'",
        dbMetadata.getTableMetadata(owningTable, defaultSchema, defaultCatalog, false).getColumnMetadata(col))))
  }
}
