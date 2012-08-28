package net.lshift.diffa.kernel.config

import net.lshift.diffa.schema.servicelimits.{ServiceLimit, Unlimited}
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import net.lshift.diffa.kernel.StoreReferenceContainer
import org.junit._
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.junit.experimental.theories.{DataPoint, Theories, Theory, DataPoints}
import org.apache.commons.lang.RandomStringUtils
import net.lshift.diffa.kernel.frontend.EndpointDef
import net.lshift.diffa.kernel.frontend.PairDef

/**
 */
@RunWith(classOf[Theories])
class JooqServiceLimitsStoreTest {
  
  var space:Space = null
  var pairRef:PairRef = null 

  val testLimit = new ServiceLimit {
    def key = "dummyLimit"
    def description = "A limit that is just for testing"
    def defaultLimit = 132
    def hardLimit = 153
  }

  private val storeReferences = JooqServiceLimitsStoreTest.storeReferences
  private val serviceLimitsStore = storeReferences.serviceLimitsStore
  private val systemConfigStore = storeReferences.systemConfigStore

  @Before
  def prepareStores {
    space = systemConfigStore.createOrUpdateSpace(RandomStringUtils.randomAlphanumeric(10))
    pairRef = PairRef(name = "diffa-test-pair", space = space.id)
    
    val upstream = EndpointDef(name = "upstream")
    val downstream = EndpointDef(name = "downstream")
    val pair = PairDef(
      key = pairRef.name,
      versionPolicyName = "same",
      upstreamName = upstream.name,
      downstreamName = downstream.name)
    
    storeReferences.domainConfigStore.createOrUpdateEndpoint(space.id, upstream)
    storeReferences.domainConfigStore.createOrUpdateEndpoint(space.id, downstream)
    storeReferences.domainConfigStore.createOrUpdatePair(space.id, pair)
    serviceLimitsStore.defineLimit(testLimit)
  }

  @After
  def clearUp { storeReferences.clearConfiguration(space.id) }

  @Test
  def givenExistingDependentsWhenSystemHardLimitConfiguredToValidValueNotLessThanDependentLimitsThenLimitShouldBeAppliedAndNoDependentLimitsChanged {
    val (limit, initialLimit, newLimitValue, depLimit) = (testLimit, 11, 10, 10)
    // Given
    setAllLimits(limit, initialLimit, depLimit)

    // When
    serviceLimitsStore.setSystemHardLimit(limit, newLimitValue)

    // Then
    val (systemHardLimit, systemDefaultLimit, domainHardLimit, domainDefaultLimit, pairLimit) =
      limitValuesForPairByName(pairRef, limit)

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
      limitValuesForPairByName(pairRef, limit)

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
    val (spaceId, limit, initialLimit, newLimitValue, depLimit) = (space.id, testLimit, 11, 10, 10)
    // Given
    serviceLimitsStore.setSystemHardLimit(limit, initialLimit)
    serviceLimitsStore.setSystemDefaultLimit(limit, initialLimit)
    serviceLimitsStore.setDomainHardLimit(spaceId, limit, initialLimit)
    serviceLimitsStore.setDomainDefaultLimit(spaceId, limit, depLimit)
    serviceLimitsStore.setPairLimit(spaceId, pairRef.name, limit, depLimit)

    // When
    serviceLimitsStore.setDomainHardLimit(spaceId, limit, newLimitValue)

    // Then
    val (_, _, domainHardLimit, domainDefaultLimit, pairLimit) =
      limitValuesForPairByName(pairRef, limit)

    assertEquals(newLimitValue, domainHardLimit)
    assertEquals(depLimit, domainDefaultLimit)
    assertEquals(depLimit, pairLimit)
  }

  @Test
  def givenExistingDependentsWhenDomainScopedHardLimitConfiguredToValidValueLessThanDependentLimitsThenLimitShouldBeAppliedAndDependentLimitsLowered {
    val (spaceId, limit, initialLimit, newLimitValue, depLimit) = (space.id, testLimit, 11, 9, 10)
    // Given
    serviceLimitsStore.setSystemHardLimit(limit, initialLimit)
    serviceLimitsStore.setSystemDefaultLimit(limit, initialLimit)
    serviceLimitsStore.setDomainHardLimit(spaceId, limit, initialLimit)
    serviceLimitsStore.setDomainDefaultLimit(spaceId, limit, depLimit)
    serviceLimitsStore.setPairLimit(spaceId, pairRef.name, limit, depLimit)

    // When
    serviceLimitsStore.setDomainHardLimit(spaceId, limit, newLimitValue)

    // Then
    val (_, _, domainHardLimit, domainDefaultLimit, pairLimit) =
      limitValuesForPairByName(pairRef, limit)

    assertEquals(newLimitValue, domainHardLimit)
    assertEquals(newLimitValue, domainDefaultLimit)
    assertEquals(newLimitValue, pairLimit)
  }

  @Test(expected = classOf[Exception])
  def whenDomainHardLimitConfiguredToInvalidValueThenExceptionThrownVerifyNoLimitChange {
    // Given
    val (spaceId, limit, oldLimit) = (space.id, testLimit, 10)
    serviceLimitsStore.setDomainHardLimit(spaceId, limit, oldLimit)

    // When
    try {
      serviceLimitsStore.setDomainHardLimit(spaceId, limit, Unlimited.value - 1)
    } catch {
      case ex =>
        // Verify
        assertEquals(oldLimit, serviceLimitsStore.getDomainHardLimitForDomainAndName(spaceId, limit).get)
        // Then
        throw ex
    }
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndNoDomainScopedLimitAndNoPairScopedLimitWhenPairScopedActionExceedsSystemDefaultThenActionFailsDueToThrottling {
    // Given
    val (pairKey, spaceId, limit, limitValue) = (pairRef.name, pairRef.space, testLimit, 0)
    serviceLimitsStore.setSystemDefaultLimit(limit, limitValue)

    val limiter = getTestLimiter(spaceId, pairKey, limit,
      (id, pKey, limName) => serviceLimitsStore.getEffectiveLimitByNameForPair(id, pKey, limit))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndNoDomainDefaultLimitAndPairScopedLimitWhenPairScopedActionExceedsPairScopedLimitThenActionFailsDueToThrottling {
    // Given
    val (pairKey, spaceId, limitName, limitValue) = (pairRef.name, pairRef.space, testLimit, 0)
    serviceLimitsStore.setSystemDefaultLimit(limitName, limitValue + 1)
    serviceLimitsStore.setPairLimit(spaceId, pairKey, limitName, limitValue)

    val limiter = getTestLimiter(spaceId, pairKey, limitName,
      (domName, pKey, limName) => serviceLimitsStore.getEffectiveLimitByNameForPair(domName, pKey, limName))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndDomainDefaultLimitAndNoPairScopedLimitWhenPairScopedActionExceedsDomainDefaultThenActionFailsDueToThrottling {
    // Given
    val (pairKey, spaceId, limit, limitValue) = (pairRef.name, pairRef.space, testLimit, 0)
    serviceLimitsStore.setSystemDefaultLimit(limit, limitValue + 1)
    serviceLimitsStore.setDomainDefaultLimit(spaceId, limit, limitValue)

    val limiter = getTestLimiter(spaceId, pairKey, limit,
      (domName, pKey, limName) => serviceLimitsStore.getEffectiveLimitByNameForPair(domName, pKey, limName))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Test(expected = classOf[ServiceLimitExceededException])
  def givenSystemDefaultLimitAndDomainDefaultLimitAndPairScopedLimitWhenPairScopedActionExceedsPairLimitThenActionFailsDueToThrottling {
    // Given
    val (pairKey, spaceId, limitName, limitValue) = (pairRef.name, pairRef.space, testLimit, 0)
    serviceLimitsStore.setSystemDefaultLimit(limitName, limitValue + 1)
    serviceLimitsStore.setDomainDefaultLimit(spaceId, limitName, limitValue - 1)
    serviceLimitsStore.setPairLimit(spaceId, pairKey, limitName, limitValue)

    val limiter = getTestLimiter(spaceId, pairKey, limitName,
      (domName, pKey, limName) => serviceLimitsStore.getEffectiveLimitByNameForPair(domName, pKey, limName))

    // When
    limiter.actionPerformed
    // Then exception thrown
  }

  @Theory
  def verifyPairScopedActionSuccess(scenario: Scenario) {
    val (limit, spaceId, pairKey) = (testLimit, space.id, pairRef.name)

    scenario match {
      case LimitEnforcementScenario(systemDefault, domainDefault, pairLimit, expectedLimit, usage)
        if usage <= expectedLimit =>

        serviceLimitsStore.deleteDomainLimits(spaceId)
        systemDefault.foreach(lim => serviceLimitsStore.setSystemDefaultLimit(limit, lim))
        domainDefault.foreach(lim => serviceLimitsStore.setDomainDefaultLimit(spaceId, limit, lim))
        pairLimit.foreach(lim => serviceLimitsStore.setPairLimit(spaceId, pairKey, limit, lim))

        val limiter = getTestLimiter(spaceId, pairKey, limit,
          (domName, pKey, limName) => serviceLimitsStore.getEffectiveLimitByNameForPair(domName, pKey, limName))

        (1 to usage).foreach(_ => limiter.actionPerformed)
      case _ =>
    }
  }

  @Theory
  def verifyPairScopedActionFailures(scenario: Scenario) {
    val (limit, spaceId, pairKey) = (testLimit, space.id, pairRef.name)

    scenario match {
      case LimitEnforcementScenario(systemDefault, domainDefault, pairLimit, expectedLimit, usage)
        if usage > expectedLimit =>

        serviceLimitsStore.deleteDomainLimits(spaceId)
        systemDefault.foreach(lim => serviceLimitsStore.setSystemDefaultLimit(limit, lim))
        domainDefault.foreach(lim => serviceLimitsStore.setDomainDefaultLimit(spaceId, limit, lim))
        pairLimit.foreach(lim => serviceLimitsStore.setPairLimit(spaceId, pairKey, limit, lim))

        val limiter = getTestLimiter(spaceId, pairKey, limit,
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
    val (limit, spaceId, pairKey) = (testLimit, space.id, pairRef.name)
    
    scenario match {
      case CascadingLimitScenario(shl, sdl, dhl, ddl, pl) =>
        // Given
        serviceLimitsStore.setSystemHardLimit(limit, shl._1)
        serviceLimitsStore.setSystemDefaultLimit(limit, sdl._1)
        dhl._1.foreach(lim => serviceLimitsStore.setDomainHardLimit(spaceId, limit, lim))
        ddl._1.foreach(lim => serviceLimitsStore.setDomainDefaultLimit(spaceId, limit, lim))
        pl._1.foreach(lim => serviceLimitsStore.setPairLimit(spaceId, pairKey, limit, lim))
        
        // When
        shl._2.foreach(lim => serviceLimitsStore.setSystemHardLimit(limit, lim))
        dhl._2.foreach(lim => serviceLimitsStore.setDomainHardLimit(spaceId, limit, lim))
        
        // Then
        assertEquals(shl._3, serviceLimitsStore.getSystemHardLimitForName(limit).get)
        assertEquals(sdl._3, serviceLimitsStore.getSystemDefaultLimitForName(limit).get)
        dhl._3.foreach(expectedLimit => assertEquals(expectedLimit, serviceLimitsStore.getDomainHardLimitForDomainAndName(spaceId, limit).get))
        ddl._3.foreach(expectedLimit => assertEquals(expectedLimit, serviceLimitsStore.getDomainDefaultLimitForDomainAndName(spaceId, limit).get))
        pl._3.foreach(expectedLimit => assertEquals(expectedLimit, serviceLimitsStore.getPairLimitForPairAndName(spaceId, pairKey, limit).get))
      case _ =>
    }
  }

  private def getTestLimiter(spaceId: Long, pairKey: String, limit: ServiceLimit,
                             effectiveLimitFn: (Long, String, ServiceLimit) => Int) = {
    new TestLimiter {
      private var actionCount = 0
      private val effectiveLimit = effectiveLimitFn(spaceId, pairKey, limit)

      def actionPerformed {
        actionCount += 1

        if (actionCount > effectiveLimit) {
          throw new ServiceLimitExceededException("limit name: %s, effective limit: %d, pair: %s, domain: %s".format(
            limit.key, effectiveLimit, pairKey, spaceId))
        }
      }
    }
  }

  private def setAllLimits(limit: ServiceLimit, sysHardLimitValue: Int, otherLimitsValue: Int) {
    serviceLimitsStore.setSystemHardLimit(limit, sysHardLimitValue)
    serviceLimitsStore.setSystemDefaultLimit(limit, otherLimitsValue)
    serviceLimitsStore.setDomainHardLimit(space.id, limit, otherLimitsValue)
    serviceLimitsStore.setDomainDefaultLimit(space.id, limit, otherLimitsValue)
    serviceLimitsStore.setPairLimit(space.id, pairRef.name, limit, otherLimitsValue)
  }

  private def limitValuesForPairByName(pair:PairRef, limit: ServiceLimit) = {
    val systemHardLimit = serviceLimitsStore.getSystemHardLimitForName(limit).get
    val systemDefaultLimit = serviceLimitsStore.getSystemDefaultLimitForName(limit).get
    val domainHardLimit = serviceLimitsStore.getDomainHardLimitForDomainAndName(pair.space, limit).get
    val domainDefaultLimit = serviceLimitsStore.getDomainDefaultLimitForDomainAndName(pair.space, limit).get
    val pairLimit = serviceLimitsStore.getPairLimitForPairAndName(pair.space, pair.name, limit).get

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

object JooqServiceLimitsStoreTest {
  private[JooqServiceLimitsStoreTest] val env = TestDatabaseEnvironments.uniqueEnvironment("target/serviceLimitsStore")

  private[JooqServiceLimitsStoreTest] val storeReferences =
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
