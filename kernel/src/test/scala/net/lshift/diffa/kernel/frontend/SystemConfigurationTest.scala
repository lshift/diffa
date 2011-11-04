package net.lshift.diffa.kernel.frontend

import net.lshift.diffa.kernel.config.system.HibernateSystemConfigStore
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.differencing.DifferencesManager
import org.junit.Test
import org.junit.Assert._
import net.sf.ehcache.CacheManager
import net.lshift.diffa.kernel.util.DatabaseEnvironment
import org.hibernate.cfg.{Configuration => HibernateConfig}
import net.lshift.diffa.kernel.config._

/**
 * Test cases for apply System Configuration.
 */
class SystemConfigurationTest {

  private val pairCache = new PairCache(new CacheManager())
  private val systemConfigStore = new HibernateSystemConfigStore(SystemConfigurationTest.sessionFactory,pairCache)

  private val differencesManager = createMock("differencesManager", classOf[DifferencesManager])

  private val systemConfiguration = new SystemConfiguration(systemConfigStore, differencesManager)

  @Test
  def shouldBeAbleToCreateUserWithUnencryptedPassword() {
    val userDef = UserDef(name = "user1", email = "user1@diffa.io", superuser = true, password = "foo")
    systemConfiguration.createOrUpdateUser(userDef)

    assertEquals(
      UserDef(name = userDef.name, email = userDef.email,
        superuser = userDef.superuser, password = "sha256:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae"),
      systemConfiguration.getUser("user1"))
  }

  @Test
  def shouldBeAbleToCreateUserWithAlreadyEncryptedPassword() {
    val userDef = UserDef(name = "user2", email = "user2@diffa.io",
        superuser = true,
        password = "sha256:fcde2b2edba56bf408601fb721fe9b5c338d10ee429ea04fae5511b68fbf8fb9")
    systemConfiguration.createOrUpdateUser(userDef)

    assertEquals(userDef, systemConfiguration.getUser("user2"))
  }

  val validUserDef = UserDef(name = "user3", email = "user3@diffa.io", superuser = true, password = "foo")

  @Test(expected = classOf[ConfigValidationException])
  def shouldRejectUserDefinitionWithoutName() {
    systemConfiguration.createOrUpdateUser(
      UserDef(name = null, email = validUserDef.email, superuser = validUserDef.superuser, password = validUserDef.password))
  }

  @Test(expected = classOf[ConfigValidationException])
  def shouldRejectUserDefinitionWithoutEmail() {
    systemConfiguration.createOrUpdateUser(
      UserDef(name = validUserDef.name, email = null, superuser = validUserDef.superuser, password = validUserDef.password))
  }

  @Test(expected = classOf[ConfigValidationException])
  def shouldRejectUserDefinitionWithoutPassword() {
    systemConfiguration.createOrUpdateUser(
      UserDef(name = validUserDef.name, email = validUserDef.email, superuser = validUserDef.superuser, password = null))
  }
}

object SystemConfigurationTest {
  private lazy val config =
      new HibernateConfig().
        addResource("net/lshift/diffa/kernel/config/Config.hbm.xml").
        addResource("net/lshift/diffa/kernel/differencing/DifferenceEvents.hbm.xml").
        setProperty("hibernate.dialect", DatabaseEnvironment.DIALECT).
        setProperty("hibernate.connection.url", DatabaseEnvironment.substitutableURL("target/systemConfigTest")).
        setProperty("hibernate.connection.driver_class", DatabaseEnvironment.DRIVER).
        setProperty("hibernate.connection.username", DatabaseEnvironment.USERNAME).
        setProperty("hibernate.connection.password", DatabaseEnvironment.PASSWORD).
        setProperty("hibernate.cache.region.factory_class", "net.sf.ehcache.hibernate.EhCacheRegionFactory").
        setProperty("hibernate.generate_statistics", "true").
        setProperty("hibernate.connection.autocommit", "true") // Turn this on to make the tests repeatable,
                                                               // otherwise the preparation step will not get committed

  lazy val sessionFactory = {
    val sf = config.buildSessionFactory
    (new HibernateConfigStorePreparationStep).prepare(sf, config)
    sf
  }
}