package net.lshift.diffa.kernel.config

import net.lshift.diffa.schema.servicelimits.{ServiceLimit, Unlimited}
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import net.lshift.diffa.kernel.StoreReferenceContainer
import org.junit.{AfterClass, Before, Test}

import org.junit.Assert.assertEquals
import org.junit.Assert
import net.lshift.diffa.kernel.frontend.{PairDef, EndpointDef}
import org.junit.runner.RunWith
import org.junit.experimental.theories.{DataPoint, Theories, Theory, DataPoints}

/**
 */
@RunWith(classOf[Theories])
class HibernateServiceLimitsStoreTest {
  private val testDomain = Domain("diffa-test-domain")
  private val testPair = DiffaPair(key = "diffa-test-pair", domain = testDomain)

  val testLimit = new ServiceLimit {
    def key = "dummyLimit"
    def description = "A limit that is just for testing"
    def defaultLimit = 132
    def hardLimit = 153
  }

  private val storeReferences = HibernateServiceLimitsStoreTest.storeReferences
  private val serviceLimitsStore = storeReferences.serviceLimitsStore

  @Before
  def prepareStores {
    val upstream = EndpointDef(name = "upstream")
    val downstream = EndpointDef(name = "downstream")
    val pair = PairDef(key = testPair.key, versionPolicyName = "same",
      upstreamName = upstream.name,
      downstreamName = downstream.name)

    storeReferences.clearConfiguration(testDomain.name)
    storeReferences.systemConfigStore.createOrUpdateDomain(testDomain)
    storeReferences.domainConfigStore.createOrUpdateEndpoint(testDomain.name, upstream)
    storeReferences.domainConfigStore.createOrUpdateEndpoint(testDomain.name, downstream)
    storeReferences.domainConfigStore.createOrUpdatePair(testDomain.name, pair)
    serviceLimitsStore.defineLimit(testLimit)
  }

  @Test
  def givenExistingDependentsWhenSystemHardLimitConfiguredToValidValueNotLessThanDependentLimitsThenLimitShouldBeAppliedAndNoDependentLimitsChanged {
    val (limit, initialLimit, newLimitValue, depLimit) = (testLimit, 11, 10, 10)
    // Given
    setAllLimits(limit, initialLimit, depLimit)

    // When
    serviceLimitsStore.setSystemHardLimit(limit, newLimitValue)

    // Then
    val (systemHardLimit, systemDefaultLimit, domainHardLimit, domainDefaultLimit, pairLimit) =
      limitValuesForPairByName(testPair, limit)

    assertEquals(newLimitValue, systemHardLimit)
    assertEquals(depLimit, systemDefaultLimit)
    assertEquals(depLimit, domainHardLimit)
    assertEquals(depLimit, domainDefaultLimit)
    assertEquals(depLimit, pairLimit)
  }

  @Test
  def givenExistingDependentsWhenSystemHardLimitConfiguredToValidValueLessThanDependentLimitsThenLimitShouldBeAppliedAndDependentLimitsLowered {
    val (limit, initialLimit, newLimitValue, depLimit) = (testLimit, 11, 9, 10)
    // Given
    setAllLimits(limit, initialLimit, depLimit)

    // When
    serviceLimitsStore.setSystemHardLimit(limit, newLimitValue)

    // Then
    val (systemHardLimit, systemDefaultLimit, domainHardLimit, domainDefaultLimit, pairLimit) =
      limitValuesForPairByName(testPair, limit)

    assertEquals(newLimitValue, systemHardLimit)
    assertEquals(newLimitValue, systemDefaultLimit)
    assertEquals(newLimitValue, domainHardLimit)
    assertEquals(newLimitValue, domainDefaultLimit)
    assertEquals(newLimitValue, pairLimit)
  }

  @Test(expected = classOf[Exception])
  def whenSystemHardLimitConfiguredToInvalidValueThenExceptionThrownVerifyNoLimitChange {
    // Given
    val (limit, oldLimit) = (testLimit, 10)
    serviceLimitsStore.setSystemHardLimit(limit, oldLimit)

    // When
    try {
      serviceLimitsStore.setSystemHardLimit(limit, Unlimited.value - 1)
    } catch {
      case ex =>
        // Verify
        assertEquals(oldLimit, serviceLimitsStore.getSystemHardLimitForName(limit).get)
        // Then
        throw ex
    }
  }

  @Test
  def givenExistingDependentsWhenDomainScopedHardLimitConfiguredToValidValueNotLessThanDependentLimitsThenLimitShouldBeAppliedAndNoDependentLimitsChanged {
    val (domainName, limit, initialLimit, newLimitValue, depLimit) = (testDomain.name, testLimit, 11, 10, 10)
    // Given
    serviceLimitsStore.setSystemHardLimit(limit, initialLimit)
    serviceLimitsStore.setSystemDefaultLimit(limit, initialLimit)
    serviceLimitsStore.setDomainHardLimit(domainName, limit, initialLimit)
    serviceLimitsStore.setDomainDefaultLimit(domainName, limit, depLimit)
    serviceLimitsStore.setPairLimit(domainName, testPair.key, limit, depLimit)

    // When
    serviceLimitsStore.setDomainHardLimit(domainName, limit, newLimitValue)

    // Then
    val (_, _, domainHardLimit, domainDefaultLimit, pairLimit) =
      limitValuesForPairByName(testPair, limit)

    assertEquals(newLimitValue, domainHardLimit)
    assertEquals(depLimit, domainDefaultLimit)
    assertEquals(depLimit, pairLimit)
  }

  @Test
  def givenExistingDependentsWhenDomainScopedHardLimitConfiguredToValidValueLessThanDependentLimitsThenLimitShouldBeAppliedAndDependentLimitsLowered {
    val (domainName, limit, initialLimit, newLimitValue, depLimit) = (testDomain.name, testLimit, 11, 9, 10)
    // Given
    serviceLimitsStore.setSystemHardLimit(limit, initialLimit)
    serviceLimitsStore.setSystemDefaultLimit(limit, initialLimit)
    serviceLimitsStore.setDomainHardLimit(domainName, limit, initialLimit)
    serviceLimitsStore.setDomainDefaultLimit(domainName, limit, depLimit)
    serviceLimitsStore.setPairLimit(domainName, testPair.key, limit, depLimit)

    // When
    serviceLimitsStore.setDomainHardLimit(domainName, limit, newLimitValue)

    // Then
    val (_, _, domainHardLimit, domainDefaultLimit, pairLimit) =
      limitValuesForPairByName(testPair, limit)

    assertEquals(newLimitValue, domainHardLimit)
    assertEquals(newLimitValue, domainDefaultLimit)
    assertEquals(newLimitValue, pairLimit)
  }

  @Test(expected = classOf[Exception])
  def whenDomainHardLimitConfiguredToInvalidValueThenExceptionThrownVerifyNoLimitChange {
    // Given
    val (domainName, limit, oldLimit) = (testDomain.name, testLimit, 10)
    serviceLimitsStore.setDomainHardLimit(domainName, limit, oldLimit)

    // When
    try {
      serviceLimitsStore.setDomainHardLimit(domainName, limit, Unlimited.value - 1)
    } catch {
      case ex =>
        // Verify
        assertEquals(oldLimit, serviceLimitsStore.getDomainHardLimitForDomainAndName(domainName, limit).get)
        // Then
        throw ex
    }
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndNoDomainScopedLimitAndNoPairScopedLimitWhenPairScopedActionExceedsSystemDefaultThenActionFailsDueToThrottling {
    // Given
    val (pairKey, domainName, limit, limitValue) = (testPair.key, testPair.domain.name, testLimit, 0)
    serviceLimitsStore.setSystemDefaultLimit(limit, limitValue)

    val limiter = getTestLimiter(domainName, pairKey, limit,
      (domName, pKey, limName) => serviceLimitsStore.getEffectiveLimitByNameForPair(domName, pKey, limit))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndNoDomainDefaultLimitAndPairScopedLimitWhenPairScopedActionExceedsPairScopedLimitThenActionFailsDueToThrottling {
    // Given
    val (pairKey, domainName, limitName, limitValue) = (testPair.key, testPair.domain.name, testLimit, 0)
    serviceLimitsStore.setSystemDefaultLimit(limitName, limitValue + 1)
    serviceLimitsStore.setPairLimit(domainName, pairKey, limitName, limitValue)

    val limiter = getTestLimiter(domainName, pairKey, limitName,
      (domName, pKey, limName) => serviceLimitsStore.getEffectiveLimitByNameForPair(domName, pKey, limName))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndDomainDefaultLimitAndNoPairScopedLimitWhenPairScopedActionExceedsDomainDefaultThenActionFailsDueToThrottling {
    // Given
    val (pairKey, domainName, limit, limitValue) = (testPair.key, testPair.domain.name, testLimit, 0)
    serviceLimitsStore.setSystemDefaultLimit(limit, limitValue + 1)
    serviceLimitsStore.setDomainDefaultLimit(domainName, limit, limitValue)

    val limiter = getTestLimiter(domainName, pairKey, limit,
      (domName, pKey, limName) => serviceLimitsStore.getEffectiveLimitByNameForPair(domName, pKey, limName))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndDomainDefaultLimitAndPairScopedLimitWhenPairScopedActionExceedsPairLimitThenActionFailsDueToThrottling {
    // Given
    val (pairKey, domainName, limitName, limitValue) = (testPair.key, testPair.domain.name, testLimit, 0)
    serviceLimitsStore.setSystemDefaultLimit(limitName, limitValue + 1)
    serviceLimitsStore.setDomainDefaultLimit(domainName, limitName, limitValue - 1)
    serviceLimitsStore.setPairLimit(domainName, pairKey, limitName, limitValue)

    val limiter = getTestLimiter(domainName, pairKey, limitName,
      (domName, pKey, limName) => serviceLimitsStore.getEffectiveLimitByNameForPair(domName, pKey, limName))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Theory
  def verifyPairScopedActionSuccess(scenario: Scenario) {
    val (limit, domainName, pairKey) = (testLimit, testDomain.name, testPair.key)

    scenario match {
      case LimitEnforcementScenario(systemDefault, domainDefault, pairLimit, expectedLimit, usage)
        if usage <= expectedLimit =>

        serviceLimitsStore.deleteDomainLimits(domainName)
        systemDefault.foreach(lim => serviceLimitsStore.setSystemDefaultLimit(limit, lim))
        domainDefault.foreach(lim => serviceLimitsStore.setDomainDefaultLimit(domainName, limit, lim))
        pairLimit.foreach(lim => serviceLimitsStore.setPairLimit(domainName, pairKey, limit, lim))

        val limiter = getTestLimiter(domainName, pairKey, limit,
          (domName, pKey, limName) => serviceLimitsStore.getEffectiveLimitByNameForPair(domName, pKey, limName))

        (1 to usage).foreach(_ => limiter.actionPerformed)
      case _ =>
    }
  }

  @Theory
  def verifyPairScopedActionFailures(scenario: Scenario) {
    val (limit, domainName, pairKey) = (testLimit, testDomain.name, testPair.key)

    scenario match {
      case LimitEnforcementScenario(systemDefault, domainDefault, pairLimit, expectedLimit, usage)
        if usage > expectedLimit =>

        serviceLimitsStore.deleteDomainLimits(domainName)
        systemDefault.foreach(lim => serviceLimitsStore.setSystemDefaultLimit(limit, lim))
        domainDefault.foreach(lim => serviceLimitsStore.setDomainDefaultLimit(domainName, limit, lim))
        pairLimit.foreach(lim => serviceLimitsStore.setPairLimit(domainName, pairKey, limit, lim))

        val limiter = getTestLimiter(domainName, pairKey, limit,
          (domName, pKey, limName) => serviceLimitsStore.getEffectiveLimitByNameForPair(domName, pKey, limName))

        (1 to expectedLimit).foreach(_ => limiter.actionPerformed)
        (expectedLimit to usage).foreach(_ =>
          try {
            limiter.actionPerformed
            Assert.fail("Operation was expected to fail due to exceeding a service limit, but succeeded")
          } catch {
            case ex: ServiceLimitExceededException =>
          }
        )
      case _ =>
    }
  }
  
  @Theory
  def verifyLimitCascadingRules(scenario: Scenario) {
    val (limit, domainName, pairKey) = (testLimit, testDomain.name, testPair.key)
    
    scenario match {
      case CascadingLimitScenario(shl, sdl, dhl, ddl, pl) =>
        // Given
        serviceLimitsStore.setSystemHardLimit(limit, shl._1)
        serviceLimitsStore.setSystemDefaultLimit(limit, sdl._1)
        dhl._1.foreach(lim => serviceLimitsStore.setDomainHardLimit(domainName, limit, lim))
        ddl._1.foreach(lim => serviceLimitsStore.setDomainDefaultLimit(domainName, limit, lim))
        pl._1.foreach(lim => serviceLimitsStore.setPairLimit(domainName, pairKey, limit, lim))
        
        // When
        shl._2.foreach(lim => serviceLimitsStore.setSystemHardLimit(limit, lim))
        dhl._2.foreach(lim => serviceLimitsStore.setDomainHardLimit(domainName, limit, lim))
        
        // Then
        assertEquals(shl._3, serviceLimitsStore.getSystemHardLimitForName(limit).get)
        assertEquals(sdl._3, serviceLimitsStore.getSystemDefaultLimitForName(limit).get)
        dhl._3.foreach(expectedLimit => assertEquals(expectedLimit, serviceLimitsStore.getDomainHardLimitForDomainAndName(domainName, limit).get))
        ddl._3.foreach(expectedLimit => assertEquals(expectedLimit, serviceLimitsStore.getDomainDefaultLimitForDomainAndName(domainName, limit).get))
        pl._3.foreach(expectedLimit => assertEquals(expectedLimit, serviceLimitsStore.getPairLimitForPairAndName(domainName, pairKey, limit).get))
      case _ =>
    }
  }

  private def getTestLimiter(domainName: String, pairKey: String, limit: ServiceLimit,
                             effectiveLimitFn: (String, String, ServiceLimit) => Int) = {
    new TestLimiter {
      private var actionCount = 0
      private val effectiveLimit = effectiveLimitFn(domainName, pairKey, limit)

      def actionPerformed {
        actionCount += 1

        if (actionCount > effectiveLimit) {
          throw new ServiceLimitExceededException("limit name: %s, effective limit: %d, pair: %s, domain: %s".format(
            limit.key, effectiveLimit, pairKey, domainName))
        }
      }
    }
  }

  private def setAllLimits(limit: ServiceLimit, sysHardLimitValue: Int, otherLimitsValue: Int) {
    serviceLimitsStore.setSystemHardLimit(limit, sysHardLimitValue)
    serviceLimitsStore.setSystemDefaultLimit(limit, otherLimitsValue)
    serviceLimitsStore.setDomainHardLimit(testDomain.name, limit, otherLimitsValue)
    serviceLimitsStore.setDomainDefaultLimit(testDomain.name, limit, otherLimitsValue)
    serviceLimitsStore.setPairLimit(testDomain.name, testPair.key, limit, otherLimitsValue)
  }

  private def limitValuesForPairByName(pair: DiffaPair, limit: ServiceLimit) = {
    val systemHardLimit = serviceLimitsStore.getSystemHardLimitForName(limit).get
    val systemDefaultLimit = serviceLimitsStore.getSystemDefaultLimitForName(limit).get
    val domainHardLimit = serviceLimitsStore.getDomainHardLimitForDomainAndName(pair.domain.name, limit).get
    val domainDefaultLimit = serviceLimitsStore.getDomainDefaultLimitForDomainAndName(pair.domain.name, limit).get
    val pairLimit = serviceLimitsStore.getPairLimitForPairAndName(pair.domain.name, pair.key, limit).get

    (systemHardLimit, systemDefaultLimit, domainHardLimit, domainDefaultLimit, pairLimit)
  }
}

trait TestLimiter {
  def actionPerformed: Unit
}

abstract class Scenario

case class LimitEnforcementScenario(systemLimit: Option[Int],
                                    domainLimit: Option[Int],
                                    pairLimit: Option[Int],
                                    enforcedLimit: Int, usage: Int) extends Scenario

case class CascadingLimitScenario(systemHardLimit: (Int, Option[Int], Int),
                                  systemDefaultLimit: (Int, Option[Int], Int),
                                  domainHardLimit: (Option[Int], Option[Int], Option[Int]),
                                  domainDefaultLimit: (Option[Int], Option[Int], Option[Int]),
                                  pairLimit: (Option[Int], Option[Int], Option[Int])) extends Scenario

object HibernateServiceLimitsStoreTest {
  private[HibernateServiceLimitsStoreTest] val env = TestDatabaseEnvironments.uniqueEnvironment("target/serviceLimitsStore")

  private[HibernateServiceLimitsStoreTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  implicit def intToSome(i: Int): Option[Int] = Some(i)

  @AfterClass
  def cleanupStores {
    storeReferences.tearDown
  }

  @DataPoints
  def actionsShouldSucceed = Array(
    LimitEnforcementScenario(0, 1, 2, 2, 2),
    LimitEnforcementScenario(0, 1, None, 1, 1),
    LimitEnforcementScenario(0, None, None, 0, 0),
    LimitEnforcementScenario(0, None, 2, 2, 2)
  )

  @DataPoints
  def operationsShouldFail = Array(
    LimitEnforcementScenario(Some(0), Some(1), Some(2), 2, 3),
    LimitEnforcementScenario(Some(7), None, None, 7, 11)
  )

  @DataPoint
  def systemDependentLimitsShouldFallToMatchToMatchConstrainingLimit = CascadingLimitScenario(
    (7,  3,   3),
    (4, None, 3),
    (6, None, 3),
    (4, None, 3),
    (5, None, 3)
  )

  @DataPoint
  def systemDependentLimitsShouldBeUnchangedWhenLessThanConstrainingLimit = CascadingLimitScenario(
    (7,  5,   5),
    (1, None, 1),
    (4, None, 4),
    (2, None, 2),
    (3, None, 3)
  )

  @DataPoint
  def domainDependentLimitsShouldFallToMatchConstrainingLimit =  CascadingLimitScenario(
    (7, None, 7),
    (5, None, 5),
    (4,  2,   2),
    (3, None, 2),
    (3, None, 2)
  )

  @DataPoint
  def domainDependentLimitsShouldBeUnchangedWhenLessThanConstrainingLimit =  CascadingLimitScenario(
    (7, None, 7),
    (5, None, 5),
    (4,  3,   3),
    (2, None, 2),
    (1, None, 1)
  )
}
