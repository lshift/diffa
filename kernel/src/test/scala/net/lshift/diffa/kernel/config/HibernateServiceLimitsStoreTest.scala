package net.lshift.diffa.kernel.config

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
  private val testLimit = ServiceLimitDefinitions("dummyLimit", "A limit that is just for testing")

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
    serviceLimitsStore.defineLimit(testLimit.limitName, testLimit.limitDescription)
  }

  @Test
  def givenExistingDependentsWhenSystemHardLimitConfiguredToValidValueNotLessThanDependentLimitsThenLimitShouldBeAppliedAndNoDependentLimitsChanged {
    val (limitName, initialLimit, newLimitValue, depLimit) = (testLimit.limitName, 11, 10, 10)
    // Given
    setAllLimits(limitName, initialLimit, depLimit)

    // When
    serviceLimitsStore.setSystemHardLimit(limitName, newLimitValue)

    // Then
    val (systemHardLimit, systemDefaultLimit, domainHardLimit, domainDefaultLimit, pairLimit) =
      limitValuesForPairByName(testPair, limitName)

    assertEquals(newLimitValue, systemHardLimit)
    assertEquals(depLimit, systemDefaultLimit)
    assertEquals(depLimit, domainHardLimit)
    assertEquals(depLimit, domainDefaultLimit)
    assertEquals(depLimit, pairLimit)
  }

  @Test
  def givenExistingDependentsWhenSystemHardLimitConfiguredToValidValueLessThanDependentLimitsThenLimitShouldBeAppliedAndDependentLimitsLowered {
    val (limitName, initialLimit, newLimitValue, depLimit) = (testLimit.limitName, 11, 9, 10)
    // Given
    setAllLimits(limitName, initialLimit, depLimit)

    // When
    serviceLimitsStore.setSystemHardLimit(limitName, newLimitValue)

    // Then
    val (systemHardLimit, systemDefaultLimit, domainHardLimit, domainDefaultLimit, pairLimit) =
      limitValuesForPairByName(testPair, limitName)

    assertEquals(newLimitValue, systemHardLimit)
    assertEquals(newLimitValue, systemDefaultLimit)
    assertEquals(newLimitValue, domainHardLimit)
    assertEquals(newLimitValue, domainDefaultLimit)
    assertEquals(newLimitValue, pairLimit)
  }

  @Test(expected = classOf[Exception])
  def whenSystemHardLimitConfiguredToInvalidValueThenExceptionThrownVerifyNoLimitChange {
    // Given
    val (limitName, oldLimit) = (testLimit.limitName, 10)
    serviceLimitsStore.setSystemHardLimit(limitName, oldLimit)

    // When
    try {
      serviceLimitsStore.setSystemHardLimit(limitName, ServiceLimit.UNLIMITED - 1)
    } catch {
      case ex =>
        // Verify
        assertEquals(oldLimit, serviceLimitsStore.getSystemHardLimitForName(limitName).get)
        // Then
        throw ex
    }
  }

  @Test
  def givenExistingDependentsWhenDomainScopedHardLimitConfiguredToValidValueNotLessThanDependentLimitsThenLimitShouldBeAppliedAndNoDependentLimitsChanged {
    val (domainName, limitName, initialLimit, newLimitValue, depLimit) = (testDomain.name, testLimit.limitName, 11, 10, 10)
    // Given
    serviceLimitsStore.setSystemHardLimit(limitName, initialLimit)
    serviceLimitsStore.setSystemDefaultLimit(limitName, initialLimit)
    serviceLimitsStore.setDomainHardLimit(domainName, limitName, initialLimit)
    serviceLimitsStore.setDomainDefaultLimit(domainName, limitName, depLimit)
    serviceLimitsStore.setPairLimit(domainName, testPair.key, limitName, depLimit)

    // When
    serviceLimitsStore.setDomainHardLimit(domainName, limitName, newLimitValue)

    // Then
    val (_, _, domainHardLimit, domainDefaultLimit, pairLimit) =
      limitValuesForPairByName(testPair, limitName)

    assertEquals(newLimitValue, domainHardLimit)
    assertEquals(depLimit, domainDefaultLimit)
    assertEquals(depLimit, pairLimit)
  }

  @Test
  def givenExistingDependentsWhenDomainScopedHardLimitConfiguredToValidValueLessThanDependentLimitsThenLimitShouldBeAppliedAndDependentLimitsLowered {
    val (domainName, limitName, initialLimit, newLimitValue, depLimit) = (testDomain.name, testLimit.limitName, 11, 9, 10)
    // Given
    serviceLimitsStore.setSystemHardLimit(limitName, initialLimit)
    serviceLimitsStore.setSystemDefaultLimit(limitName, initialLimit)
    serviceLimitsStore.setDomainHardLimit(domainName, limitName, initialLimit)
    serviceLimitsStore.setDomainDefaultLimit(domainName, limitName, depLimit)
    serviceLimitsStore.setPairLimit(domainName, testPair.key, limitName, depLimit)

    // When
    serviceLimitsStore.setDomainHardLimit(domainName, limitName, newLimitValue)

    // Then
    val (_, _, domainHardLimit, domainDefaultLimit, pairLimit) =
      limitValuesForPairByName(testPair, limitName)

    assertEquals(newLimitValue, domainHardLimit)
    assertEquals(newLimitValue, domainDefaultLimit)
    assertEquals(newLimitValue, pairLimit)
  }

  @Test(expected = classOf[Exception])
  def whenDomainHardLimitConfiguredToInvalidValueThenExceptionThrownVerifyNoLimitChange {
    // Given
    val (domainName, limitName, oldLimit) = (testDomain.name, testLimit.limitName, 10)
    serviceLimitsStore.setDomainHardLimit(domainName, limitName, oldLimit)

    // When
    try {
      serviceLimitsStore.setDomainHardLimit(domainName, limitName, ServiceLimit.UNLIMITED - 1)
    } catch {
      case ex =>
        // Verify
        assertEquals(oldLimit, serviceLimitsStore.getDomainHardLimitForDomainAndName(domainName, limitName).get)
        // Then
        throw ex
    }
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndNoDomainScopedLimitAndNoPairScopedLimitWhenPairScopedActionExceedsSystemDefaultThenActionFailsDueToThrottling {
    // Given
    val (pairKey, domainName, limitName, limitValue) = (testPair.key, testPair.domain.name, testLimit.limitName, 0)
    serviceLimitsStore.setSystemDefaultLimit(limitName, limitValue)

    val limiter = getTestLimiter(limitName, pairKey, domainName,
      (limName, domName, pKey) => serviceLimitsStore.getEffectiveLimitByNameForPair(limName, domName, pKey))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndNoDomainDefaultLimitAndPairScopedLimitWhenPairScopedActionExceedsPairScopedLimitThenActionFailsDueToThrottling {
    // Given
    val (pairKey, domainName, limitName, limitValue) = (testPair.key, testPair.domain.name, testLimit.limitName, 0)
    serviceLimitsStore.setSystemDefaultLimit(limitName, limitValue + 1)
    serviceLimitsStore.setPairLimit(domainName, pairKey, limitName, limitValue)

    val limiter = getTestLimiter(limitName, pairKey, domainName,
      (limName, domName, pKey) => serviceLimitsStore.getEffectiveLimitByNameForPair(limName, domName, pKey))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndDomainDefaultLimitAndNoPairScopedLimitWhenPairScopedActionExceedsDomainDefaultThenActionFailsDueToThrottling {
    // Given
    val (pairKey, domainName, limitName, limitValue) = (testPair.key, testPair.domain.name, testLimit.limitName, 0)
    serviceLimitsStore.setSystemDefaultLimit(limitName, limitValue + 1)
    serviceLimitsStore.setDomainDefaultLimit(domainName, limitName, limitValue)

    val limiter = getTestLimiter(limitName, pairKey, domainName,
      (limName, domName, pKey) => serviceLimitsStore.getEffectiveLimitByNameForPair(limName, domName, pKey))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndDomainDefaultLimitAndPairScopedLimitWhenPairScopedActionExceedsPairLimitThenActionFailsDueToThrottling {
    // Given
    val (pairKey, domainName, limitName, limitValue) = (testPair.key, testPair.domain.name, testLimit.limitName, 0)
    serviceLimitsStore.setSystemDefaultLimit(limitName, limitValue + 1)
    serviceLimitsStore.setDomainDefaultLimit(domainName, limitName, limitValue - 1)
    serviceLimitsStore.setPairLimit(domainName, pairKey, limitName, limitValue)

    val limiter = getTestLimiter(limitName, pairKey, domainName,
      (limName, domName, pKey) => serviceLimitsStore.getEffectiveLimitByNameForPair(limName, domName, pKey))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Theory
  def verifyPairScopedActionSuccess(scenario: Scenario) {
    val (limitName, domainName, pairKey) = (testLimit.limitName, testDomain.name, testPair.key)

    scenario match {
      case LimitEnforcementScenario(systemDefault, domainDefault, pairLimit, expectedLimit, usage)
        if usage <= expectedLimit =>

        serviceLimitsStore.deleteDomainLimits(domainName)
        systemDefault.foreach(lim => serviceLimitsStore.setSystemDefaultLimit(limitName, lim))
        domainDefault.foreach(lim => serviceLimitsStore.setDomainDefaultLimit(domainName, limitName, lim))
        pairLimit.foreach(lim => serviceLimitsStore.setPairLimit(domainName, pairKey, limitName, lim))

        val limiter = getTestLimiter(limitName, pairKey, domainName,
          (limName, domName, pKey) => serviceLimitsStore.getEffectiveLimitByNameForPair(limName, domName, pKey))

        (1 to usage).foreach(_ => limiter.actionPerformed)
      case _ =>
    }
  }

  @Theory
  def verifyPairScopedActionFailures(scenario: Scenario) {
    val (limitName, domainName, pairKey) = (testLimit.limitName, testDomain.name, testPair.key)

    scenario match {
      case LimitEnforcementScenario(systemDefault, domainDefault, pairLimit, expectedLimit, usage)
        if usage > expectedLimit =>

        serviceLimitsStore.deleteDomainLimits(domainName)
        systemDefault.foreach(lim => serviceLimitsStore.setSystemDefaultLimit(limitName, lim))
        domainDefault.foreach(lim => serviceLimitsStore.setDomainDefaultLimit(domainName, limitName, lim))
        pairLimit.foreach(lim => serviceLimitsStore.setPairLimit(domainName, pairKey, limitName, lim))

        val limiter = getTestLimiter(limitName, pairKey, domainName,
          (limName, domName, pKey) => serviceLimitsStore.getEffectiveLimitByNameForPair(limName, domName, pKey))

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
    val (limitName, domainName, pairKey) = (testLimit.limitName, testDomain.name, testPair.key)
    
    scenario match {
      case CascadingLimitScenario(shl, sdl, dhl, ddl, pl) =>
        // Given
        serviceLimitsStore.setSystemHardLimit(limitName, shl._1)
        serviceLimitsStore.setSystemDefaultLimit(limitName, sdl._1)
        dhl._1.foreach(lim => serviceLimitsStore.setDomainHardLimit(domainName, limitName, lim))
        ddl._1.foreach(lim => serviceLimitsStore.setDomainDefaultLimit(domainName, limitName, lim))
        pl._1.foreach(lim => serviceLimitsStore.setPairLimit(domainName, pairKey, limitName, lim))
        
        // When
        shl._2.foreach(lim => serviceLimitsStore.setSystemHardLimit(limitName, lim))
        dhl._2.foreach(lim => serviceLimitsStore.setDomainHardLimit(domainName, limitName, lim))
        
        // Then
        assertEquals(shl._3, serviceLimitsStore.getSystemHardLimitForName(limitName).get)
        assertEquals(sdl._3, serviceLimitsStore.getSystemDefaultLimitForName(limitName).get)
        dhl._3.foreach(expectedLimit => assertEquals(expectedLimit, serviceLimitsStore.getDomainHardLimitForDomainAndName(domainName, limitName).get))
        ddl._3.foreach(expectedLimit => assertEquals(expectedLimit, serviceLimitsStore.getDomainDefaultLimitForDomainAndName(domainName, limitName).get))
        pl._3.foreach(expectedLimit => assertEquals(expectedLimit, serviceLimitsStore.getPairLimitForPairAndName(domainName, pairKey, limitName).get))
      case _ =>
    }
  }

  private def getTestLimiter(limitName: String, pairKey: String, domainName: String,
                             effectiveLimitFn: (String, String, String) => Int) = {
    new TestLimiter {
      private var actionCount = 0
      private val effectiveLimit = effectiveLimitFn(limitName, domainName, pairKey)

      def actionPerformed {
        actionCount += 1

        if (actionCount > effectiveLimit) {
          throw new ServiceLimitExceededException("limit name: %s, effective limit: %d, pair: %s, domain: %s".format(
            limitName, effectiveLimit, pairKey, domainName))
        }
      }
    }
  }

  private def setAllLimits(limitName: String, sysHardLimitValue: Int, otherLimitsValue: Int) {
    serviceLimitsStore.setSystemHardLimit(limitName, sysHardLimitValue)
    serviceLimitsStore.setSystemDefaultLimit(limitName, otherLimitsValue)
    serviceLimitsStore.setDomainHardLimit(testDomain.name, limitName, otherLimitsValue)
    serviceLimitsStore.setDomainDefaultLimit(testDomain.name, limitName, otherLimitsValue)
    serviceLimitsStore.setPairLimit(testDomain.name, testPair.key, limitName, otherLimitsValue)
  }

  private def limitValuesForPairByName(pair: DiffaPair, limitName: String) = {
    val systemHardLimit = serviceLimitsStore.getSystemHardLimitForName(limitName).get
    val systemDefaultLimit = serviceLimitsStore.getSystemDefaultLimitForName(limitName).get
    val domainHardLimit = serviceLimitsStore.getDomainHardLimitForDomainAndName(pair.domain.name, limitName).get
    val domainDefaultLimit = serviceLimitsStore.getDomainDefaultLimitForDomainAndName(pair.domain.name, limitName).get
    val pairLimit = serviceLimitsStore.getPairLimitForPairAndName(pair.domain.name, pair.key, limitName).get

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
