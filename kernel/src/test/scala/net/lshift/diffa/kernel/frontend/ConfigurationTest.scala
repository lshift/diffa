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
package net.lshift.diffa.kernel.frontend

import org.easymock.EasyMock._
import net.lshift.diffa.kernel.matching.MatchingManager
import net.lshift.diffa.kernel.scheduler.ScanScheduler
import net.lshift.diffa.kernel.differencing.{DifferencesManager, VersionCorrelationStoreFactory}
import org.junit.Assert._
import net.lshift.diffa.kernel.participants.EndpointLifecycleListener
import net.lshift.diffa.kernel.config._
import scala.collection.JavaConversions._
import org.easymock.IArgumentMatcher
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.actors.{PairPolicyClient, ActivePairManager}
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.StoreReferenceContainer
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import org.junit.{AfterClass, Test, Before}
import org.apache.commons.lang.RandomStringUtils

/**
 * Test cases for the Configuration frontend.
 */
class ConfigurationTest {
  private val storeReferences = ConfigurationTest.storeReferences

  private val matchingManager = createMock("matchingManager", classOf[MatchingManager])
  private val versionCorrelationStoreFactory = createMock("versionCorrelationStoreFactory", classOf[VersionCorrelationStoreFactory])
  private val pairManager = createMock("pairManager", classOf[ActivePairManager])
  private val differencesManager = createMock("differencesManager", classOf[DifferencesManager])
  private val endpointListener = createMock("endpointListener", classOf[EndpointLifecycleListener])
  private val scanScheduler = createMock("scanScheduler", classOf[ScanScheduler])
  private val diagnostics = createMock("diagnostics", classOf[DiagnosticsManager])
  private val pairPolicyClient = createMock(classOf[PairPolicyClient])

  // TODO This is a strange mixture of mock and real objects
  private val systemConfigStore = storeReferences.systemConfigStore
  private val domainConfigStore = storeReferences.domainConfigStore
  private val serviceLimitsStore = storeReferences.serviceLimitsStore
  private val userPreferencesStore = storeReferences.userPreferencesStore

  private val configuration = new Configuration(domainConfigStore,
                                                systemConfigStore,
                                                serviceLimitsStore,
                                                userPreferencesStore,
                                                matchingManager,
                                                versionCorrelationStoreFactory,
                                                List(pairManager),
                                                differencesManager,
                                                endpointListener,
                                                scanScheduler,
                                                diagnostics,
                                                pairPolicyClient)

  val domainName = RandomStringUtils.randomAlphanumeric(10)
  val domain = Domain(name = domainName)

  var space:Space = null

  @Before
  def clearConfig = {
    try {
      if (space != null) {
        systemConfigStore.deleteSpace(space.id)
      }
    }
    catch {
      case e:MissingObjectException => // ignore non-existent domain, since the point of this call was to delete it anyway
    }
    space = systemConfigStore.createOrUpdateSpace(domainName)
  }

  @Test
  def shouldApplyEmptyConfigToEmptySystem {
    replayAll

    configuration.applyConfiguration(space.id, DiffaConfig())
    assertEquals(Some(DiffaConfig()), configuration.retrieveConfiguration(space.id))
  }

  @Test
  def shouldNotLetCallingUserRemoveThemselvesFromDomain {

    val callingUser = User(name = "callingUser", email = "call@me.com", superuser = false, passwordEnc = "e23e", token = "32sza")
    val anotherUser = User(name = "anotherUser", email = "another@me.com", superuser = false, passwordEnc = "e23e", token = "32sza")

    systemConfigStore.createOrUpdateUser(callingUser)
    systemConfigStore.createOrUpdateUser(anotherUser)
    domainConfigStore.makeDomainMember(space.id, "callingUser")
    domainConfigStore.makeDomainMember(space.id, "anotherUser")

    val configWithCallingUser = DiffaConfig(
      members = Set("callingUser")
    )

    configuration.applyConfiguration(space.id, DiffaConfig(), Some("callingUser"))
    assertEquals(Some(configWithCallingUser), configuration.retrieveConfiguration(space.id))
  }

  @Test
  def shouldGenerateExceptionWhenInvalidConfigurationIsApplied() {
    val e1 = EndpointDef(name = "upstream1", scanUrl = "http://localhost:1234/scan",
          inboundUrl = "http://inbound")
    val e2 = EndpointDef(name = "downstream1", scanUrl = "http://localhost:5432/scan")
    val conf = new DiffaConfig(
      endpoints = Set(e1, e2),
      pairs = Set(
        PairDef("ab", "same", 5, "upstream1", "downstream1", "bad-cron-spec"))
    )

    try {
      configuration.applyConfiguration(space.id, conf)
      fail("Should have thrown ConfigValidationException")
    } catch {
      case ex:ConfigValidationException =>
        assertEquals("config/pair[key=ab]: Schedule 'bad-cron-spec' is not a valid: Illegal characters for this position: 'BAD'", ex.getMessage)
    }
  }

  @Test
  def shouldApplyConfigurationToEmptySystem() {

    // Create users that have membership references in the domain config

    val user1 = User(name = "abc", email = "dev_null1@lshift.net", passwordEnc = "TEST")
    val user2 = User(name = "def", email = "dev_null1@lshift.net", passwordEnc = "TEST")

    systemConfigStore.createOrUpdateUser(user1)
    systemConfigStore.createOrUpdateUser(user2)

    val ep1 = DomainEndpointDef(space = space.id, name = "upstream1", scanUrl = "http://localhost:1234",
                inboundUrl = "http://inbound",
                categories = Map(
                  "a" -> new RangeCategoryDescriptor("datetime", "2009", "2010"),
                  "b" -> new SetCategoryDescriptor(Set("a", "b", "c"))),
                views = List(EndpointViewDef("v1")))
    val ep2 = DomainEndpointDef(space = space.id, name = "downstream1", scanUrl = "http://localhost:5432/scan",
          categories = Map(
            "c" -> new PrefixCategoryDescriptor(1, 5, 1),
            "d" -> new PrefixCategoryDescriptor(1, 6, 1)
          ),
          views = List(EndpointViewDef("v1")))
    val config = new DiffaConfig(
      properties = Map("diffa.host" -> "localhost:1234", "a" -> "b"),
      members = Set("abc","def"),
      endpoints = Set(ep1.withoutDomain, ep2.withoutDomain),
      pairs = Set(
        PairDef("ab", "same", 5, "upstream1", "downstream1", "0 * * * * ?",
          allowManualScans = true, views = List(PairViewDef("v1", "0 * * * * ?", false)),
          repairActions = Set(RepairActionDef("Resend Sauce", "resend", "pair")),
          reports = Set(PairReportDef("Bulk Send Differences", "differences", "http://location:5432/diffhandler")),
          escalations = Set(
            EscalationDef("Resend Missing", "Resend Sauce", "repair", "downstreamMissing", 30),
            EscalationDef("Report Differences", "Bulk Send Differences", "report", "scan-completed")
          )
        ),
        PairDef("ac", "same", 5, "upstream1", "downstream1", "0 * * * * ?", scanCronEnabled = false))
    )

    val ab = DomainPairDef(key = "ab", space = space.id, matchingTimeout = 5,
                       versionPolicyName = "same", scanCronSpec = "0 * * * * ?", upstreamName = ep1.name, downstreamName = ep2.name,
                       views = List(PairViewDef("v1", "0 * * * * ?", false)))

    val ac = DomainPairDef(key = "ac", space = space.id, matchingTimeout = 5, scanCronEnabled = false,
                           versionPolicyName = "same", scanCronSpec = "0 * * * * ?", upstreamName = ep1.name, downstreamName = ep2.name)


    expect(endpointListener.onEndpointAvailable(ep1)).once
    expect(endpointListener.onEndpointAvailable(ep2)).once
    expect(pairManager.startActor(pairInstance("ab"))).once
    expect(matchingManager.onUpdatePair(ab.asRef)).once
    expect(scanScheduler.onUpdatePair(ab.asRef)).once
    expect(differencesManager.onUpdatePair(ab.asRef)).once
    expect(pairPolicyClient.difference(ab.asRef)).once
    expect(pairManager.startActor(pairInstance("ac"))).once
    expect(matchingManager.onUpdatePair(ac.asRef)).once
    expect(scanScheduler.onUpdatePair(ac.asRef)).once
    expect(differencesManager.onUpdatePair(ac.asRef)).once
    expect(pairPolicyClient.difference(ac.asRef)).once
    replayAll

    configuration.applyConfiguration(space.id, config)
    assertEquals(Some(config), configuration.retrieveConfiguration(space.id))
    verifyAll
  }

  @Test
  def shouldUpdateConfigurationInNonEmptySystem() {
    // Apply the configuration used in the empty state test
    shouldApplyConfigurationToEmptySystem
    resetAll

      // upstream1 is kept but changed
    val ep1 = DomainEndpointDef(space = space.id, name = "upstream1", scanUrl = "http://localhost:6543/scan",
          inboundUrl = "http://inbound",
          categories = Map(
            "a" -> new RangeCategoryDescriptor("datetime", "2009", "2010"),
            "b" -> new SetCategoryDescriptor(Set("a", "b", "c"))))
      // downstream1 is gone, downstream2 is added
    val ep2 = DomainEndpointDef(space = space.id, name = "downstream2", scanUrl = "http://localhost:54321/scan",
          categories = Map(
            "c" -> new PrefixCategoryDescriptor(1, 5, 1),
            "d" -> new PrefixCategoryDescriptor(1, 6, 1)
          ))
    val config = new DiffaConfig(
        // diffa.host is changed, a -> b is gone, c -> d is added
      properties = Map("c" -> "d", "diffa.host" -> "localhost:2345"),
        // abc is changed, def is gone, ghi is added
      members = Set("abc","def"),
      endpoints = Set(ep2.withoutDomain(), ep1.withoutDomain()),
        // gaa is gone, gcc is created, gbb is the same
      pairs = Set(
          // ab has moved from gaa to gcc
        PairDef("ab", "same", 5, "upstream1", "downstream2", "0 * * * * ?",
          repairActions = Set(RepairActionDef("Resend Source", "resend", "pair")),
          escalations = Set(EscalationDef(name = "Resend Another Missing",
                                        action = "Resend Source", actionType = "repair",
                                        rule = "downstreamMissing", delay = 30)),
          reports = Set(PairReportDef("Bulk Send Reports Elsewhere", "differences", "http://location:5431/diffhandler"))
        ),
          // ac is gone
        PairDef("ad", "same", 5, "upstream1", "downstream2"))
    )

    val ab = DomainPairDef(key = "ab", space = space.id, matchingTimeout = 5,
                           versionPolicyName = "same", scanCronSpec = "0 * * * * ?", upstreamName = ep1.name, downstreamName = ep2.name)

    val ac = DomainPairDef(key = "ac", space = space.id, matchingTimeout = 5,
                           versionPolicyName = "same", scanCronSpec = "0 * * * * ?", upstreamName = ep1.name, downstreamName = ep2.name)

    val ad = DomainPairDef(key = "ad", space = space.id, matchingTimeout = 5,
                           versionPolicyName = "same", upstreamName = ep1.name, downstreamName = ep2.name)

    expect(pairManager.startActor(pairInstance("ab"))).once   // Note that this will result in the actor being restarted
    expect(matchingManager.onUpdatePair(ab.asRef)).once
    expect(scanScheduler.onUpdatePair(ab.asRef)).once
    expect(differencesManager.onUpdatePair(PairRef(name = "ab", space = space.id))).once
    expect(pairPolicyClient.difference(PairRef(name = "ab", space = space.id))).once
    expect(pairManager.stopActor(PairRef(name = "ac", space = space.id))).once
    expect(matchingManager.onDeletePair(ac.asRef)).once
    expect(scanScheduler.onDeletePair(ac.asRef)).once
    expect(differencesManager.onDeletePair(ac.asRef)).once
    expect(versionCorrelationStoreFactory.remove(PairRef(name = "ac", space = space.id))).once
    expect(diagnostics.onDeletePair(PairRef(name = "ac", space = space.id))).once
    expect(pairManager.startActor(pairInstance("ad"))).once
    expect(matchingManager.onUpdatePair(ad.asRef)).once
    expect(scanScheduler.onUpdatePair(ad.asRef)).once
    expect(differencesManager.onUpdatePair(PairRef(name = "ad", space = space.id))).once
    expect(pairPolicyClient.difference(PairRef(name = "ad", space = space.id))).once

    expect(endpointListener.onEndpointRemoved(space.id, "downstream1")).once
    expect(endpointListener.onEndpointAvailable(ep1)).once
    expect(endpointListener.onEndpointAvailable(ep2)).once
    replayAll

    configuration.applyConfiguration(space.id,config)
    val Some(newConfig) = configuration.retrieveConfiguration(space.id)
    assertEquals(config, newConfig)

    // check that the action was updated
    assertEquals(Set(RepairActionDef("Resend Source", "resend", "pair")),
      newConfig.pairs.find(_.key == "ab").get.repairActions.toSet)
    verifyAll
  }

  @Test
  def shouldBlankConfigurationOfNonEmptySystem() {
    // Apply the configuration used in the empty state test
    shouldApplyConfigurationToEmptySystem
    resetAll

    val ab = DomainPairDef(key = "ab", space=space.id)
    val ac = DomainPairDef(key = "ac", space=space.id)

    expect(pairManager.stopActor(PairRef(name = "ab", space = space.id))).once
    expect(pairManager.stopActor(PairRef(name = "ac", space = space.id))).once
    expect(matchingManager.onDeletePair(ab.asRef)).once
    expect(matchingManager.onDeletePair(ac.asRef)).once
    expect(scanScheduler.onDeletePair(ab.asRef)).once
    expect(scanScheduler.onDeletePair(ac.asRef)).once
    expect(versionCorrelationStoreFactory.remove(PairRef(name = "ab", space = space.id))).once
    expect(versionCorrelationStoreFactory.remove(PairRef(name = "ac", space = space.id))).once
    expect(diagnostics.onDeletePair(PairRef(name = "ab", space = space.id))).once
    expect(diagnostics.onDeletePair(PairRef(name = "ac", space = space.id))).once
    expect(differencesManager.onDeletePair(PairRef(name = "ab", space = space.id))).once
    expect(differencesManager.onDeletePair(PairRef(name = "ac", space = space.id))).once
    expect(endpointListener.onEndpointRemoved(space.id, "upstream1")).once
    expect(endpointListener.onEndpointRemoved(space.id, "downstream1")).once
    replayAll

    configuration.applyConfiguration(space.id,DiffaConfig())
    assertEquals(Some(DiffaConfig()), configuration.retrieveConfiguration(space.id))
    verifyAll
  }

  @Test
  def shouldSupportClearingOfActionsAndEscalations() {
    // Apply the configuration used in the empty state test
    shouldApplyConfigurationToEmptySystem
    resetAll

    configuration.clearRepairActions(space.id, "ab")
    configuration.clearEscalations(space.id, "ab")

    assertEquals(0, configuration.getPairDef(space.id, "ab").repairActions.size())
    assertEquals(0, configuration.getPairDef(space.id, "ab").escalations.size())
  }

  private def replayAll = replay(matchingManager, pairManager, differencesManager, endpointListener, scanScheduler)
  private def verifyAll = verify(matchingManager, pairManager, differencesManager, endpointListener, scanScheduler)
  private def resetAll = reset(matchingManager, pairManager, differencesManager, endpointListener, scanScheduler)
  private def pairInstance(key:String):PairRef = {
    reportMatcher(new IArgumentMatcher {
      def appendTo(buffer: StringBuffer) = buffer.append("pair with key " + key)
      def matches(argument: AnyRef) = argument.asInstanceOf[PairRef].name == key
    })
    null
  }
}

object ConfigurationTest {
  private[ConfigurationTest] val env = TestDatabaseEnvironments.uniqueEnvironment("target/configTest")

  private[ConfigurationTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  @AfterClass
  def cleanupSchema {
    storeReferences.tearDown
  }
}