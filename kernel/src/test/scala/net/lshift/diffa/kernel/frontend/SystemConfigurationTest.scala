package net.lshift.diffa.kernel.frontend

import org.easymock.EasyMock._
import net.lshift.diffa.kernel.differencing.DifferencesManager
import org.junit.Assert._
import org.hibernate.cfg.{Configuration => HibernateConfig}
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.kernel.lifecycle.NotificationCentre
import net.lshift.diffa.kernel.StoreReferenceContainer
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import org.junit.{AfterClass, Test}
import net.lshift.diffa.config.ConfigValidationException

/**
 * Test cases for apply System Configuration.
 */
class SystemConfigurationTest {
 private val storeReferences = SystemConfigurationTest.storeReferences

  private val systemConfigStore = storeReferences.systemConfigStore
  private val serviceLimitsStore = storeReferences.serviceLimitsStore

  private val differencesManager = createMock("differencesManager", classOf[DifferencesManager])

  private val nc = new NotificationCentre

  private val systemConfiguration = new SystemConfiguration(
    systemConfigStore,
    serviceLimitsStore,
    differencesManager, nc, null)
  private val listener = createMock("systemConfigListener", classOf[SystemConfigListener])


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
  def shouldRejectUserDefinitionWithoutPassword() {
    systemConfiguration.createOrUpdateUser(
      UserDef(name = validUserDef.name, email = validUserDef.email, superuser = validUserDef.superuser, password = null))
  }

  @Test
  def shouldBeAbleToRetrieveAUserToken() {
    systemConfiguration.createOrUpdateUser(validUserDef)
    assertNotNull(systemConfiguration.getUserToken(validUserDef.name))
  }

  @Test
  def shouldBeAbleToResetAUserToken() {
    systemConfiguration.createOrUpdateUser(validUserDef)
    val token1 = systemConfiguration.getUserToken(validUserDef.name)
    systemConfiguration.clearUserToken(validUserDef.name)
    val token2 = systemConfiguration.getUserToken(validUserDef.name)

    assertFalse(token1.equals(token2))
  }

  @Test
  def shouldBeAbleToSetASingleOption() {
    systemConfiguration.setSystemConfigOption("a", "b")
    assertEquals(Some("b"), systemConfiguration.getSystemConfigOption("a"))
  }

  @Test
  def shouldReceiveAnEventWhenASingleOptionIsUpdated() {
    listener.configPropertiesUpdated(Seq("a")); expectLastCall()
    replay(listener)
    nc.registerForSystemConfigEvents(listener)

    systemConfiguration.setSystemConfigOption("a", "b")
    verify(listener)
  }

  @Test
  def shouldBeAbleToSetMultipleOptions() {
    systemConfiguration.setSystemConfigOptions(Map("a" -> "foo", "b" -> "bar"))
    assertEquals(Some("foo"), systemConfiguration.getSystemConfigOption("a"))
    assertEquals(Some("bar"), systemConfiguration.getSystemConfigOption("b"))
  }

  @Test
  def shouldReceiveAnEventWhenMultipleOptionsUpdated() {
    listener.configPropertiesUpdated(Seq("a", "b")); expectLastCall()
    replay(listener)
    nc.registerForSystemConfigEvents(listener)

    systemConfiguration.setSystemConfigOptions(Map("a" -> "foo", "b" -> "bar"))
    verify(listener)
  }
}

object SystemConfigurationTest {
  private[SystemConfigurationTest] lazy val env =
    TestDatabaseEnvironments.uniqueEnvironment("target/systemConfigTest")

  private[SystemConfigurationTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  @AfterClass
  def cleanupSchema {
    storeReferences.tearDown
  }
}