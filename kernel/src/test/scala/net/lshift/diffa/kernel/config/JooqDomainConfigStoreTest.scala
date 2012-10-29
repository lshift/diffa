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

import org.junit.Assert
import org.junit.Assert._
import scala.collection.Map
import org.joda.time.DateTime
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.frontend._
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.StoreReferenceContainer
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import org.slf4j.LoggerFactory
import org.junit.{After, Test, AfterClass, Before}
import net.lshift.diffa.kernel.preferences.FilteredItemType
import com.eaio.uuid.UUID
import org.jooq.exception.DataAccessException
import org.apache.commons.lang.RandomStringUtils
import system.PolicyKey
import org.junit.experimental.theories.{DataPoint, Theory, Theories}
import org.junit.runner.RunWith

@RunWith(classOf[Theories])
class JooqDomainConfigStoreTest {
  import JooqDomainConfigStoreTest.{Scenario, randomSpace}

  private val log = LoggerFactory.getLogger(getClass)

  private val storeReferences = JooqDomainConfigStoreTest.storeReferences
  private val systemConfigStore = storeReferences.systemConfigStore
  private val domainConfigStore = storeReferences.domainConfigStore
  private val userPreferencesStore = storeReferences.userPreferencesStore

  val dateCategoryName = "bizDate"
  val dateCategoryLower = new DateTime(1982,4,5,12,13,9,0).toString()
  val dateCategoryUpper = new DateTime(1982,4,6,12,13,9,0).toString()
  val dateRangeCategoriesMap =
    Map(dateCategoryName ->  new RangeCategoryDescriptor("datetime",dateCategoryLower,dateCategoryUpper))

  val setCategoryValues = Set("a","b","c")
  val setCategoriesMap = Map(dateCategoryName ->  new SetCategoryDescriptor(setCategoryValues))

  val intCategoryName = "someInt"
  val stringCategoryName = "someString"

  val intCategoryType = "int"
  val intRangeCategoriesMap = Map(intCategoryName ->  new RangeCategoryDescriptor(intCategoryType))

  val stringPrefixCategoriesMap = Map(stringCategoryName -> new PrefixCategoryDescriptor(1, 3, 1))

  var space:Space = null
  
  val domainName = RandomStringUtils.randomAlphanumeric(10)
  val domain = new Domain(domainName)

  val setView = EndpointViewDef(name = "a-only", categories = Map(dateCategoryName -> new SetCategoryDescriptor(Set("a"))))

  val upstream1 = new EndpointDef(name = "e1", scanUrl = "testScanUrl1",
                                  categories = dateRangeCategoriesMap)
  val upstream2 = new EndpointDef(name = "e2", scanUrl = "testScanUrl2",
                                  contentRetrievalUrl = "contentRetrieveUrl1",
                                  categories = setCategoriesMap,
                                  views = Seq(setView))

  val downstream1 = new EndpointDef(name = "e3", scanUrl = "testScanUrl3",
                                    categories = intRangeCategoriesMap)
  val downstream2 = new EndpointDef(name = "e4", scanUrl = "testScanUrl4",
                                    versionGenerationUrl = "generateVersionUrl1",
                                    categories = stringPrefixCategoriesMap)

  val repairAction = RepairActionDef(name="REPAIR_ACTION_NAME",
                                       scope=RepairAction.ENTITY_SCOPE,
                                       url="resend")
  val escalation = EscalationDef(name="esc", action = "test_action",
                                   rule = "upstreamMissing",
                                   actionType = EscalationActionType.REPAIR,
                                   delay = 30)
  val report = PairReportDef(name = "REPORT_NAME",
                             reportType = "differences", target = "http://example.com/diff_listener")


  val versionPolicyName1 = "TEST_VPNAME"
  val matchingTimeout = 120
  val versionPolicyName2 = "TEST_VPNAME_ALT"
  val pairKey = "TEST_PAIR"
  val pairDef = new PairDef(pairKey, versionPolicyName1, matchingTimeout, upstream1.name,
    downstream1.name, views = Seq(PairViewDef(name = "a-only")),
    repairActions = Set(repairAction),
    escalations = Set(escalation),
    reports = Set(report))

  var pair:PairRef = null

  val configKey = "foo"
  val configValue = "bar"

  val upstreamRenamed = "TEST_UPSTREAM_RENAMED"
  val pairRenamed = "TEST_PAIR_RENAMED"

  val user = User(name = "test_user", email = "dev_null@lshift.net", passwordEnc = "TEST")
  val user2 = User(name = "test_user2", email = "dev_null@lshift.net", passwordEnc = "TEST")
  val adminUser = User(name = "admin_user", email = "dev_null@lshift.net", passwordEnc = "TEST", superuser = true)

  def declareAll() {
    domainConfigStore.createOrUpdateEndpoint(space.id, upstream1)
    domainConfigStore.createOrUpdateEndpoint(space.id, upstream2)
    domainConfigStore.createOrUpdateEndpoint(space.id, downstream1)
    domainConfigStore.createOrUpdateEndpoint(space.id, downstream2)
    domainConfigStore.createOrUpdatePair(space.id, pairDef)
    domainConfigStore.setConfigOption(space.id, configKey, configValue)
  }

  @Before
  def setUp {
    space = systemConfigStore.createOrUpdateSpace(domainName)
    pair = PairRef(name = pairKey, space = space.id)
    domainConfigStore.reset
  }

  @After
  def cleanup {
    storeReferences.clearConfiguration(space.id)
  }

  def exists (e:EndpointDef, count:Int, offset:Int) : Unit = {
    val endpoints = domainConfigStore.listEndpoints(space.id).sortWith((a, b) => a.name < b.name)
    assertEquals(count, endpoints.length)
    assertEquals(e.name, endpoints(offset).name)
    assertEquals(e.inboundUrl, endpoints(offset).inboundUrl)
    assertEquals(e.scanUrl, endpoints(offset).scanUrl)
    assertEquals(e.contentRetrievalUrl, endpoints(offset).contentRetrievalUrl)
    assertEquals(e.versionGenerationUrl, endpoints(offset).versionGenerationUrl)
  }

  def exists (e:EndpointDef, count:Int) : Unit = exists(e, count, count - 1)

  @Test
  def domainShouldBeDeletable = {
    declareAll()

    exists(upstream1, 4, 0)
    exists(upstream2, 4, 1)
    exists(downstream1, 4, 2)
    exists(downstream2, 4, 3)

    assertFalse(domainConfigStore.listPairs(space.id).isEmpty)
    assertFalse(domainConfigStore.allConfigOptions(space.id).isEmpty)

    systemConfigStore.deleteSpace(space.id)

    // TODO I find this behavior a bit strange - should these methods not be throwing MissingObjectExceptions
    // for a non-existent space?

    assertTrue(domainConfigStore.listEndpoints(space.id).isEmpty)
    assertTrue(domainConfigStore.listPairs(space.id).isEmpty)
    assertTrue(domainConfigStore.allConfigOptions(space.id).isEmpty)

    assertTrue(systemConfigStore.listDomains.filter(_ == space.id).isEmpty)
  }


  @Test
  def escalationsRepairsAndReportsShouldBeNamespacedPerPairAndDomain() {
    declareAll()

    val repairAction2 = repairAction.copy(url = "resend2")
    val repairAction3 = repairAction.copy(url = "resend3")
    val escalation2 = escalation.copy(rule = "downstreamMissing")
    val escalation3 = escalation.copy(rule = "upstreamMissing")
    val report2 = report.copy(target = "http://example.com/diff_listener2")
    val report3 = report.copy(target = "http://example.com/diff_listener3")

    val pair2 = pairDef.copy(key = pairKey + "2", // Different name, difference associated objects
        repairActions = Set(repairAction2), escalations = Set(escalation2), reports = Set(report2))
    var pair3 = pairDef.copy(     // Same name, but different associated objects
        repairActions = Set(repairAction3), escalations = Set(escalation3), reports = Set(report3))

    domainConfigStore.createOrUpdatePair(space.id, pair2)

    val space2 = systemConfigStore.createOrUpdateSpace(RandomStringUtils.randomAlphanumeric(10))
    
    domainConfigStore.createOrUpdateEndpoint(space2.id, upstream1)
    domainConfigStore.createOrUpdateEndpoint(space2.id, downstream1)
    domainConfigStore.createOrUpdatePair(space2.id, pair3)

    // Load the created pairs, and ensure the data remains unique
    val retrievedPair = domainConfigStore.getPairDef(space.id, pairKey).withoutDomain
    val retrievedPair2 = domainConfigStore.getPairDef(space.id, pairKey + "2").withoutDomain
    val retrievedPair3 = domainConfigStore.getPairDef(space2.id, pairKey).withoutDomain

    assertEquals(pairDef, retrievedPair)
    assertEquals(pair2, retrievedPair2)
    assertEquals(pair3, retrievedPair3)
  }


@Test
  def testDeclare {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)

    // Declare endpoints
    domainConfigStore.createOrUpdateEndpoint(space.id, upstream1)
    exists(upstream1, 1)

    domainConfigStore.createOrUpdateEndpoint(space.id, downstream1)
    exists(downstream1, 2)

    // Declare a pair
    domainConfigStore.createOrUpdatePair(space.id, pairDef)

    val retrPair = domainConfigStore.getPairDef(space.id, pairDef.key)
    assertEquals(pairKey, retrPair.key)
    assertEquals(upstream1.name, retrPair.upstreamName)
    assertEquals(downstream1.name, retrPair.downstreamName)
    assertEquals(versionPolicyName1, retrPair.versionPolicyName)
    assertEquals(matchingTimeout, retrPair.matchingTimeout)
    assertEquals(Set(repairAction), retrPair.repairActions.toSet)
    assertEquals(Set(report), retrPair.reports.toSet)
    assertEquals(Set(escalation), retrPair.escalations.toSet)
  }

  @Test
  def removingPairShouldRemoveAnyUserSettingsRelatedToThatPair {
    declareAll()

    systemConfigStore.createOrUpdateUser(user)
    domainConfigStore.makeDomainMember(space.id, user.name, PolicyKey(0L, "User"))
    userPreferencesStore.createFilteredItem(pair, user.name, FilteredItemType.SWIM_LANE)

    domainConfigStore.deletePair(pair)

  }

  @Test
  def removingDomainShouldRemoveAnyUserSettingsRelatedToThatDomain {
    declareAll()

    systemConfigStore.createOrUpdateUser(user)
    domainConfigStore.makeDomainMember(space.id, user.name, PolicyKey(0L, "User"))
    userPreferencesStore.createFilteredItem(pair, user.name, FilteredItemType.SWIM_LANE)

    systemConfigStore.deleteSpace(space.id)

  }

  @Test
  def shouldAllowMaxGranularityOverride = {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)

    val categories =
      Map(dateCategoryName ->  new RangeCategoryDescriptor("datetime", dateCategoryLower, dateCategoryUpper, "individual"))

    val endpoint = new EndpointDef(name = "ENDPOINT_WITH_OVERIDE", scanUrl = "testScanUrlOverride",
                                   contentRetrievalUrl = "contentRetrieveUrlOverride",
                                   categories = categories)
    domainConfigStore.createOrUpdateEndpoint(space.id, endpoint)
    exists(endpoint, 1)
    val retrEndpoint = domainConfigStore.getEndpointDef(space.id, endpoint.name)
    val descriptor = retrEndpoint.categories(dateCategoryName).asInstanceOf[RangeCategoryDescriptor]
    assertEquals("individual", descriptor.maxGranularity)

  }

  @Test
  def testPairsAreValidatedBeforeUpdate() {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)
    // Declare endpoints
    domainConfigStore.createOrUpdateEndpoint(space.id, upstream1)
    exists(upstream1, 1)

    domainConfigStore.createOrUpdateEndpoint(space.id, downstream1)
    exists(downstream1, 2)

    pairDef.scanCronSpec = "invalid"

    try {
      domainConfigStore.createOrUpdatePair(space.id, pairDef)
      fail("Should have thrown ConfigValidationException")
    } catch {
      case ex:ConfigValidationException =>
        assertEquals("pair[key=TEST_PAIR]: Schedule 'invalid' is not a valid: Illegal characters for this position: 'INV'", ex.getMessage)
    }
  }

  @Test
  def testEndpointsWithSameScanURL {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)
    domainConfigStore.createOrUpdateEndpoint(space.id, upstream1)

    upstream2.scanUrl = upstream1.scanUrl
    domainConfigStore.createOrUpdateEndpoint(space.id, upstream2)

    exists(upstream1, 2, 0)
    exists(upstream2, 2, 1)
  }


  @Test
  def testUpdateEndpoint: Unit = {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)
    // Create endpoint
    domainConfigStore.createOrUpdateEndpoint(space.id, upstream1)
    exists(upstream1, 1)

    domainConfigStore.deleteEndpoint(space.id, upstream1.name)
    expectMissingObject("endpoint") {
      domainConfigStore.getEndpointDef(space.id, upstream1.name)
    }
        
    // Change its name
    domainConfigStore.createOrUpdateEndpoint(space.id, EndpointDef(name = upstreamRenamed,
                                                                     scanUrl = upstream1.scanUrl,
                                                                     inboundUrl = "changes"))

    val retrieved = domainConfigStore.getEndpointDef(space.id, upstreamRenamed)
    assertEquals(upstreamRenamed, retrieved.name)
  }

  @Test
  def testEndpointCollationIsPersisted = {
    systemConfigStore.createOrUpdateDomain(domainName)
    domainConfigStore.createOrUpdateEndpoint(space.id, upstream1.copy(collation = UnicodeCollationOrdering.name))
    val retrieved = domainConfigStore.getEndpointDef(space.id, upstream1.name)
    assertEquals(UnicodeCollationOrdering.name, retrieved.collation)
  }

  @Test
  def testUpdatePair: Unit = {
    declareAll

    // Rename, change a few fields and swap endpoints by deleting and creating new
    domainConfigStore.deletePair(space.id, pairKey)
    expectMissingObject("pair") {
      domainConfigStore.getPairDef(space.id, pairKey)
    }

    domainConfigStore.createOrUpdatePair(space.id, PairDef(pairRenamed, versionPolicyName2, 10,
      downstream1.name, upstream1.name, "0 0 * * * ?", scanCronEnabled = false, allowManualScans = false))
    
    val retrieved = domainConfigStore.getPairDef(space.id, pairRenamed)
    assertEquals(pairRenamed, retrieved.key)
    assertEquals(downstream1.name, retrieved.upstreamName) // check endpoints are swapped
    assertEquals(upstream1.name, retrieved.downstreamName)
    assertEquals(versionPolicyName2, retrieved.versionPolicyName)
    assertEquals("0 0 * * * ?", retrieved.scanCronSpec)
    assertEquals(false, retrieved.scanCronEnabled)
    assertEquals(false, retrieved.allowManualScans)
    assertEquals(10, retrieved.matchingTimeout)
  }

  @Test
  def testDeleteEndpointCascade: Unit = {
    declareAll

    assertEquals(upstream1.name, domainConfigStore.getEndpointDef(space.id, upstream1.name).name)
    domainConfigStore.deleteEndpoint(space.id, upstream1.name)
    expectMissingObject("endpoint") {
      domainConfigStore.getEndpointDef(space.id, upstream1.name)
    }
    expectMissingObject("pair") {
      domainConfigStore.getPairDef(space.id, pairKey) // delete should cascade
    }
  }

  @Test
  def testDeletePair {
    declareAll

    assertEquals(pairKey, domainConfigStore.getPairDef(space.id, pairKey).key)
    domainConfigStore.deletePair(space.id, pairKey)
    expectMissingObject("pair") {
      domainConfigStore.getPairDef(space.id, pairKey)
    }
  }

  @Test
  def testDeleteRepairsReportsAndEscalations {
    declareAll
    assertEquals(1, domainConfigStore.getPairDef(space.id, pairKey).repairActions.size)
    assertEquals(1, domainConfigStore.getPairDef(space.id, pairKey).escalations.size)
    assertEquals(1, domainConfigStore.getPairDef(space.id, pairKey).reports.size)

    domainConfigStore.createOrUpdatePair(space.id,
      pairDef.copy(
        repairActions = Set[RepairActionDef](),
        escalations = Set[EscalationDef](),
        reports = Set[PairReportDef]()))

    assertEquals(0, domainConfigStore.getPairDef(space.id, pairKey).repairActions.size)
    assertEquals(0, domainConfigStore.getPairDef(space.id, pairKey).escalations.size)
    assertEquals(0, domainConfigStore.getPairDef(space.id, pairKey).reports.size)
  }

  @Test(expected = classOf[ConfigValidationException])
  def escalationRulesMustBeUniqueWithinPairs {

    val path = RandomStringUtils.randomAlphanumeric(10)
    val space = systemConfigStore.createOrUpdateSpace(path)

    val pair = buildBasicRandomPair(space.id)

    val repair = RepairActionDef(
      name = RandomStringUtils.randomAlphanumeric(10),
      scope = RepairAction.ENTITY_SCOPE,
      url = "http://foo.com")

    val escalation = EscalationDef(
      name = RandomStringUtils.randomAlphanumeric(10),
      action = repair.name,
      rule = "upstreamMissing",
      actionType = EscalationActionType.REPAIR,
      delay = 30)

    val withEscalation = pair.copy(repairActions = Set(repair), escalations = Set(escalation))
    domainConfigStore.createOrUpdatePair(space.id, withEscalation)

    val duplicatedRule = EscalationDef(
      name = RandomStringUtils.randomAlphanumeric(10),
      action = repair.name,
      rule = "upstreamMissing",
      actionType = EscalationActionType.REPAIR,
      delay = 30)

    val withDuplicatedRule = pair.copy(repairActions = Set(repair), escalations = Set(escalation, duplicatedRule))
    domainConfigStore.createOrUpdatePair(space.id, withDuplicatedRule)

  }

  private def buildBasicRandomPair(space:Long) : PairDef = {
    val up = domainConfigStore.createOrUpdateEndpoint(space, EndpointDef(name = RandomStringUtils.randomAlphanumeric(10)))
    val down = domainConfigStore.createOrUpdateEndpoint(space, EndpointDef(name = RandomStringUtils.randomAlphanumeric(10)))
    val pair = PairDef(
      key = RandomStringUtils.randomAlphanumeric(10),
      upstreamName = up.name,
      downstreamName = down.name
    )
    domainConfigStore.createOrUpdatePair(space, pair)
    pair
  }

  @Test
  def testDeleteMissing {

    systemConfigStore.createOrUpdateDomain(domainName)

    expectMissingObject("endpoint") {
      domainConfigStore.deleteEndpoint(space.id, "MISSING_ENDPOINT")
    }

    // TODO changed the expectation from domain/MISSING_PAIR to just MISSING_PAIR
    // because what now happens is that the error message contains the surrogate key
    // of the space, which this version of the test doesn't know anything about.
    // This needs to get fixed in the long term.
    expectMissingObject("MISSING_PAIR") {
      domainConfigStore.deletePair(space.id, "MISSING_PAIR")
    }
  }

  @Theory
  def shouldStoreAnEndpoint(scenario: Scenario) {
    val space = systemConfigStore.createOrUpdateSpace(randomSpace())
    domainConfigStore.createOrUpdateEndpoint(space.id, scenario.upstream)

    Assert.assertEquals("The endpoint retrieved should match the endpoint declared",
      scenario.upstream, domainConfigStore.getEndpointDef(space.id, scenario.upstream.name))
  }

  @Theory
  def shouldListEndpoints(scenario: Scenario) {
    val endpoints = Seq(scenario.upstream, scenario.downstream)

    val space = systemConfigStore.createOrUpdateSpace(randomSpace())

    endpoints.foreach { endpoint =>
      domainConfigStore.createOrUpdateEndpoint(space.id, endpoint)
    }

    Assert.assertEquals("The endpoints retrieved should match those declared",
      endpoints.sortBy(_.name), domainConfigStore.listEndpoints(space.id).sortBy(_.name))
  }

  @Theory
  def shouldStoreRedeclaredEndpoint(scenario: Scenario) {
    scenario.newUp.foreach { newUpstream =>
      val space = systemConfigStore.createOrUpdateSpace(randomSpace())

      domainConfigStore.createOrUpdateEndpoint(space.id, scenario.upstream)
      domainConfigStore.createOrUpdateEndpoint(space.id, newUpstream)

      Assert.assertEquals("The new endpoint definition should replace the previous definition",
        newUpstream, domainConfigStore.getEndpointDef(space.id, newUpstream.name))
    }
  }

  /*
   * TODO: replace completely with theories - in progress.
   * Property-based tests will need some initial effort to build the arbitrary instances.
   */
  @Test
  def shouldBeAbleToRedeclareEndpoints = {

    // Note that this is a heuristic that attempts to flush out all of the main state transitions
    // that we can think of. It is in no way systematic or exhaustive.
    // If somebody knew their way around property based testing, then could break a leg here.

    val space = systemConfigStore.createOrUpdateSpace(RandomStringUtils.randomAlphanumeric(10))

    def verifyEndpoints(endpoints:Seq[EndpointDef]) {
      endpoints.foreach(e => {
        domainConfigStore.createOrUpdateEndpoint(space.id, e)

        val endpoint = domainConfigStore.getEndpointDef(space.id, e.name)
        assertEquals(e, endpoint)
      })

      val result = domainConfigStore.listEndpoints(space.id)
      assertEquals(endpoints, result)
    }

    val up_v0 = EndpointDef(
      name = "upstream",
      scanUrl = "upstream_url"
    )

    val down_v0 = EndpointDef(
      name = "downstream",
      scanUrl = "downstream_url"
    )

    verifyEndpoints(Seq(down_v0, up_v0))

    val up_v1 = up_v0.copy(scanUrl = "some_other_url")
    verifyEndpoints(Seq(down_v0, up_v1))

    val up_v2 = up_v1.copy(views = List(EndpointViewDef(name = "view1")))
    verifyEndpoints(Seq(down_v0, up_v2))

    val down_v1 = down_v0.copy(categories = Map("foo" -> new RangeCategoryDescriptor("date", null, null, null)))
    verifyEndpoints(Seq(down_v1, up_v2))

    val down_v2 = down_v1.copy(
      categories = Map(
        "foo" -> new RangeCategoryDescriptor("date", null, null, null),
        "bar" -> new PrefixCategoryDescriptor(1,3,1)
      ))
    verifyEndpoints(Seq(down_v2, up_v2))

    val down_v3 = down_v2.copy(
      categories = Map(
        "foo" -> new RangeCategoryDescriptor("date", null, null, null),
        "bar" -> new PrefixCategoryDescriptor(1,3,1),
        "baz" -> new SetCategoryDescriptor(Set("a","b","c"))
      ))
    verifyEndpoints(Seq(down_v3, up_v2))

    val down_v4 = down_v3.copy(
      categories = Map(
        "foo" -> new RangeCategoryDescriptor("date", null, null, null),
        "ibm" -> new RangeCategoryDescriptor("date", "1999-10-10", "1999-10-11", null),
        "bar" -> new PrefixCategoryDescriptor(1,3,1),
        "who" -> new PrefixCategoryDescriptor(2,3,2),
        "baz" -> new SetCategoryDescriptor(Set("sierra","lima","yankie")),
        "pom" -> new SetCategoryDescriptor(Set("indigo","victor","charlie"))
      ))
    verifyEndpoints(Seq(down_v4, up_v2))

    val up_v3 = up_v0.copy(categories = Map(
      "november" -> new RangeCategoryDescriptor("date", null, "2010-11-11", null),
      "zulu"     -> new PrefixCategoryDescriptor(3,6,3)),
      views = List(EndpointViewDef(
        name = "view1",
        categories = Map(
          "november" -> new RangeCategoryDescriptor("date", null, "2010-11-11", null),
          "zulu"     -> new PrefixCategoryDescriptor(3,6,3)
        )
    )))
    verifyEndpoints(Seq(down_v4, up_v3))

    verifyEndpoints(Seq(down_v0, up_v3))
    verifyEndpoints(Seq(down_v0, up_v0))

  }

  @Test
  def rangeCategory = {
    declareAll
    val pair = domainConfigStore.getPairDef(space.id, pairKey)
    assertNotNull(pair.upstreamName)
    assertNotNull(pair.downstreamName)
    val upstream = domainConfigStore.getEndpointDef(space.id, pair.upstreamName)
    val downstream = domainConfigStore.getEndpointDef(space.id, pair.downstreamName)
    assertNotNull(upstream.categories)
    assertNotNull(downstream.categories)
    val us_descriptor = upstream.categories(dateCategoryName).asInstanceOf[RangeCategoryDescriptor]
    val ds_descriptor = downstream.categories(intCategoryName).asInstanceOf[RangeCategoryDescriptor]
    assertEquals("datetime", us_descriptor.dataType)
    assertEquals(intCategoryType, ds_descriptor.dataType)
    assertEquals(dateCategoryLower, us_descriptor.lower)
    assertEquals(dateCategoryUpper, us_descriptor.upper)
  }

  @Test
  def setCategory = {
    declareAll
    val endpoint = domainConfigStore.getEndpointDef(space.id, upstream2.name)
    assertNotNull(endpoint.categories)
    val descriptor = endpoint.categories(dateCategoryName).asInstanceOf[SetCategoryDescriptor]
    assertEquals(setCategoryValues, descriptor.values.toSet)
  }

  @Test
  def prefixCategory = {
    declareAll
    val endpoint = domainConfigStore.getEndpointDef(space.id, downstream2.name)
    assertNotNull(endpoint.categories)
    val descriptor = endpoint.categories(stringCategoryName).asInstanceOf[PrefixCategoryDescriptor]
    assertEquals(1, descriptor.prefixLength)
    assertEquals(3, descriptor.maxLength)
    assertEquals(1, descriptor.step)
  }

  @Test
  def shouldStoreViewsOnEndpoints = {
    declareAll
    val endpoint = domainConfigStore.getEndpointDef(space.id, upstream2.name)
    assertNotNull(endpoint.views)
    assertEquals(1, endpoint.views.length)

    val view = endpoint.views(0)
    assertEquals("a-only", view.name)
    assertNotNull(view.categories)
    val descriptor = view.categories(dateCategoryName).asInstanceOf[SetCategoryDescriptor]
    assertEquals(Set("a"), descriptor.values.toSet)
  }

  @Test
  def shouldStoreViewsOnPairs = {
    declareAll
    val pair = domainConfigStore.getPairDef(space.id, pairKey)
    assertNotNull(pair.views)
    assertEquals(1, pair.views.length)

    val view = pair.views(0)
    assertEquals("a-only", view.name)
  }

  @Test
  def shouldAllowViewsWithTheSameNameToBeAppliedToBothSidesOfAPair = {

    val parentCategories = Map("some-date-category" ->  new RangeCategoryDescriptor("date", "2009-11-11", "2009-11-20"))
    val viewCategories = Map("some-date-category" ->  new RangeCategoryDescriptor("date", "2009-11-18", "2009-11-19"))

    // It should be possible to have:
    // - An endpoint with multiple views each having a category of the same name
    // - Shared view names across endpoints

    val uv1 = EndpointViewDef(name = "first-shared-name", categories = viewCategories)
    val uv2 = uv1.copy(name = "second-shared-name")
    val dv1 = EndpointViewDef(name = "first-shared-name", categories = viewCategories)
    val dv2 = dv1.copy(name = "second-shared-name")

    val upstreamViews = Seq(uv1,uv2)
    val downstreamViews = Seq(dv1,dv2)

    val upstream = EndpointDef(name = new UUID().toString, categories = parentCategories, views = upstreamViews)
    val downstream = EndpointDef(name = new UUID().toString, categories = parentCategories, views = downstreamViews)

    val pair = PairDef(key = new UUID().toString, upstreamName = upstream.name, downstreamName = downstream.name)

    val space = systemConfigStore.createOrUpdateSpace(RandomStringUtils.randomAlphanumeric(10))

    domainConfigStore.createOrUpdateEndpoint(space.id, upstream)
    domainConfigStore.createOrUpdateEndpoint(space.id, downstream)
    domainConfigStore.createOrUpdatePair(space.id, pair)

    // It should not be possible to create more than one view with the same name within the same endpoint

    val uv3 = uv2.copy()
    val invalidUpstreamViews = Seq(uv1,uv2,uv3)
    val invalidUpstream = EndpointDef(name = new UUID().toString, categories = parentCategories, views = invalidUpstreamViews)

    try {
      domainConfigStore.createOrUpdateEndpoint(space.id, invalidUpstream)
      fail("Should have thrown integrity error")
    }
    catch {
      case x:DataAccessException => // expected
    }
    finally {
      systemConfigStore.deleteSpace(space.id)
    }

  }

  @Test
  def testApplyingDefaultConfigOption = {
    assertEquals("defaultVal", domainConfigStore.configOptionOrDefault(space.id,"some.option", "defaultVal"))
  }

  @Test
  def testReturningNoneForConfigOption {
    assertEquals(None, domainConfigStore.maybeConfigOption(space.id, "some.option"))
  }

  @Test
  def testRetrievingConfigOption = {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)
    domainConfigStore.setConfigOption(space.id, "some.option2", "storedVal")
    assertEquals("storedVal", domainConfigStore.configOptionOrDefault(space.id, "some.option2", "defaultVal"))
    assertEquals(Some("storedVal"), domainConfigStore.maybeConfigOption(space.id, "some.option2"))
  }

  @Test
  def testUpdatingConfigOption = {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)

    domainConfigStore.setConfigOption(space.id, "some.option3", "storedVal")
    domainConfigStore.setConfigOption(space.id, "some.option3", "storedVal2")
    assertEquals("storedVal2", domainConfigStore.configOptionOrDefault(space.id, "some.option3", "defaultVal"))
    assertEquals(Some("storedVal2"), domainConfigStore.maybeConfigOption(space.id, "some.option3"))
  }

  @Test
  def testRemovingConfigOption = {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)

    domainConfigStore.setConfigOption(space.id, "some.option3", "storedVal")
    domainConfigStore.clearConfigOption(space.id, "some.option3")
    assertEquals("defaultVal", domainConfigStore.configOptionOrDefault(space.id, "some.option3", "defaultVal"))
    assertEquals(None, domainConfigStore.maybeConfigOption(space.id, "some.option3"))
  }

  @Test
  def testRetrievingAllOptions = {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)

    domainConfigStore.setConfigOption(space.id, "some.option3", "storedVal")
    domainConfigStore.setConfigOption(space.id, "some.option4", "storedVal3")
    assertEquals(Map("some.option3" -> "storedVal", "some.option4" -> "storedVal3"), domainConfigStore.allConfigOptions(space.id))
  }

  @Test
  def testRetrievingOptionsIgnoresSystemOptions = {
    // declare the child domain
    systemConfigStore.createOrUpdateDomain(domainName)

    domainConfigStore.setConfigOption(space.id, "some.option3", "storedVal")
    systemConfigStore.setSystemConfigOption("some.option4", "storedVal3")
    assertEquals(Map("some.option3" -> "storedVal"), domainConfigStore.allConfigOptions(space.id))
  }

  @Test
  def shouldBeAbleToManageDomainMembership = {

    val space = systemConfigStore.createOrUpdateSpace(RandomStringUtils.randomAlphanumeric(10))

    def assertIsDomainMember(member:Member, expectation:Boolean) = {
      val members = domainConfigStore.listDomainMembers(space.id)
      val isMember = members.contains(member)
      assertEquals(expectation, isMember)

      val userMembers = systemConfigStore.listDomainMemberships(user.name)
      val hasDomainMember = userMembers.contains(member)
      assertEquals(expectation, hasDomainMember)
    }


    systemConfigStore.createOrUpdateUser(user)

    val member = domainConfigStore.makeDomainMember(space.id, user.name, PolicyKey(0L, "User"))
    assertIsDomainMember(member, true)

    domainConfigStore.removeDomainMembership(space.id, user.name, "User")
    assertIsDomainMember(member, false)
  }

  @Test
  def shouldBeAbleToFindRootUsers = {

    systemConfigStore.createOrUpdateUser(user)
    systemConfigStore.createOrUpdateUser(adminUser)

    assertTrue(systemConfigStore.containsRootUser(Seq(user.name, adminUser.name, "missing_user")))
    assertFalse(systemConfigStore.containsRootUser(Seq(user.name, "missing_user")))
    assertFalse(systemConfigStore.containsRootUser(Seq("missing_user1", "missing_user2")))
  }

  @Test
  def shouldBeAbleToRetrieveTokenForUser() {
    systemConfigStore.createOrUpdateUser(user)
    systemConfigStore.createOrUpdateUser(user2)

    val token1 = systemConfigStore.getUserToken("test_user")
    val token2 = systemConfigStore.getUserToken("test_user2")

    assertFalse(token1.equals(token2))

    assertEquals("test_user", systemConfigStore.getUserByToken(token1).name)
    assertEquals("test_user2", systemConfigStore.getUserByToken(token2).name)
  }

  @Test
  def tokenShouldRemainConsistentEvenWhenUserIsUpdated() {
    systemConfigStore.createOrUpdateUser(user)
    val token1 = systemConfigStore.getUserToken("test_user")

    systemConfigStore.createOrUpdateUser(User(name = "test_user", email = "dev_null2@lshift.net", passwordEnc = "TEST"))
    val token2 = systemConfigStore.getUserToken("test_user")

    assertEquals(token1, token2)
  }

  @Test
  def shouldBeAbleToResetTokenForUser() {
    systemConfigStore.createOrUpdateUser(user)
    systemConfigStore.createOrUpdateUser(user2)

    val token1 = systemConfigStore.getUserToken(user.name)
    val token2 = systemConfigStore.getUserToken(user2.name)

    systemConfigStore.clearUserToken(user2.name)

    assertEquals(token1, systemConfigStore.getUserToken(user.name))

    val newToken2 = systemConfigStore.getUserToken(user2.name)
    assertNotNull(newToken2)
    assertFalse(token2.equals(newToken2))

    assertEquals(user2.name, systemConfigStore.getUserByToken(newToken2).name)
    try {
      systemConfigStore.getUserByToken(token2)
      fail("Should have thrown MissingObjectException")
    } catch {
      case ex:MissingObjectException => // Expected
    }
  }

  @Test
  def configChangeShouldUpgradeDomainConfigVersion {

    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)

    val up = EndpointDef(name = "some-upstream-endpoint")
    val down = EndpointDef(name = "some-downstream-endpoint")
    val pair = PairDef(key = "some-pair", upstreamName = up.name, downstreamName = down.name)

    val v1 = domainConfigStore.getConfigVersion(space.id)
    domainConfigStore.createOrUpdateEndpoint(space.id, up)
    verifyDomainConfigVersionWasUpgraded(space.id, v1)

    val v2 = domainConfigStore.getConfigVersion(space.id)
    domainConfigStore.createOrUpdateEndpoint(space.id, down)
    verifyDomainConfigVersionWasUpgraded(space.id, v2)

    val v3 = domainConfigStore.getConfigVersion(space.id)
    domainConfigStore.createOrUpdatePair(space.id, pair)
    verifyDomainConfigVersionWasUpgraded(space.id, v3)

    val v4 = domainConfigStore.getConfigVersion(space.id)
    domainConfigStore.deletePair(space.id, pair.key)
    verifyDomainConfigVersionWasUpgraded(space.id, v4)

    val v5 = domainConfigStore.getConfigVersion(space.id)
    domainConfigStore.deleteEndpoint(space.id, up.name)
    domainConfigStore.deleteEndpoint(space.id, down.name)
    verifyDomainConfigVersionWasUpgraded(space.id, v5)

  }

  @Test
  def shouldDefaultToReportingBreakerAsUntripped() {
    systemConfigStore.createOrUpdateDomain(domainName)
    assertFalse(domainConfigStore.isBreakerTripped(space.id, pairKey, "escalations:*"))
  }

  @Test
  def shouldStoreTrippedBreaker() {
    systemConfigStore.createOrUpdateDomain(domainName)

    val e1 = domainConfigStore.createOrUpdateEndpoint(space.id, EndpointDef(name = "some-upstream-endpoint"))
    val e2 = domainConfigStore.createOrUpdateEndpoint(space.id, EndpointDef(name = "some-downstream-endpoint"))
    domainConfigStore.createOrUpdatePair(space.id, PairDef(key = pairKey, upstreamName = e1.name, downstreamName = e2.name))

    domainConfigStore.tripBreaker(space.id, pairKey, "escalations:*")
    assertTrue(domainConfigStore.isBreakerTripped(space.id, pairKey, "escalations:*"))
  }

  @Test
  def shouldKeepTrippedBreakersIsolated() {
    systemConfigStore.createOrUpdateDomain(domainName)

    val e1 = domainConfigStore.createOrUpdateEndpoint(space.id, EndpointDef(name = "some-upstream-endpoint"))
    val e2 = domainConfigStore.createOrUpdateEndpoint(space.id, EndpointDef(name = "some-downstream-endpoint"))
    domainConfigStore.createOrUpdatePair(space.id, PairDef(key = pairKey, upstreamName = e1.name, downstreamName = e2.name))

    domainConfigStore.tripBreaker(space.id, pairKey, "escalations:*")
    assertTrue(domainConfigStore.isBreakerTripped(space.id, pairKey, "escalations:*"))
    assertFalse(domainConfigStore.isBreakerTripped(space.id, pairKey, "escalations:other"))
  }

  @Test
  def shouldSupportClearingABreaker() {
    systemConfigStore.createOrUpdateDomain(domainName)

    val e1 = domainConfigStore.createOrUpdateEndpoint(space.id, EndpointDef(name = "some-upstream-endpoint"))
    val e2 = domainConfigStore.createOrUpdateEndpoint(space.id, EndpointDef(name = "some-downstream-endpoint"))
    domainConfigStore.createOrUpdatePair(space.id, PairDef(key = pairKey, upstreamName = e1.name, downstreamName = e2.name))

    domainConfigStore.tripBreaker(space.id, pairKey, "escalations:*")
    domainConfigStore.tripBreaker(space.id, pairKey, "escalations:other")
    domainConfigStore.clearBreaker(space.id, pairKey, "escalations:*")
    assertFalse(domainConfigStore.isBreakerTripped(space.id, pairKey, "escalations:*"))
    assertTrue(domainConfigStore.isBreakerTripped(space.id, pairKey, "escalations:other"))
  }

  private def verifyDomainConfigVersionWasUpgraded(space:Long, oldVersion:Int) {
    val currentVersion = domainConfigStore.getConfigVersion(space)
    assertTrue("Current version %s is not greater than old version %s".format(currentVersion,oldVersion), currentVersion > oldVersion)
  }

  private def expectMissingObject(name:String)(f: => Unit) {
    try {
      f
      fail("Expected MissingObjectException")
    } catch {
      case e:MissingObjectException => assertTrue(
        "Missing Object Exception for wrong object. Expected for " + name + ", got msg: " + e.getMessage,
        e.getMessage.contains(name))
    }
  }

}

object JooqDomainConfigStoreTest {
  private[JooqDomainConfigStoreTest] val env =
    TestDatabaseEnvironments.uniqueEnvironment("target/domainConfigStore")

  private[JooqDomainConfigStoreTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  private def randomName() = RandomStringUtils.randomAlphanumeric(10)
  private[JooqDomainConfigStoreTest] def randomSpace() = randomName()
  private def randomEndpoint() = EndpointDef(name = randomName())
  private def endpointWithScanUrl() = EndpointDef(name = randomName())
  private def endpointWithView() = EndpointDef(name = randomName(), views = List(EndpointViewDef(name = randomName())))
  private def randomPair() = (randomEndpoint(), randomEndpoint())

  case class Scenario(upstream: EndpointDef, downstream: EndpointDef, newUp: Option[EndpointDef] = None)

  @DataPoint def blankEndpoints = Scenario(randomEndpoint(), randomEndpoint())
  @DataPoint def upWithScanUrl = Scenario(endpointWithScanUrl(), randomEndpoint())
  @DataPoint def bothWithScanUrl = Scenario(endpointWithScanUrl(), endpointWithScanUrl())
  @DataPoint def upWithView = Scenario(endpointWithView(), endpointWithScanUrl())
  @DataPoint def redeclareWithScanUrl = {
    val (up, down) = randomPair()
    Scenario(up, down, Some(up.copy(scanUrl = randomName())))
  }
  @DataPoint def redeclareWithView = {
    val (up, down) = randomPair()
    Scenario(up, down, Some(up.copy(views = List(EndpointViewDef(name = randomName())))))
  }
  @DataPoint def redeclareWithCategory = {
    val (up, down) = randomPair()
    Scenario(up, down, Some(up.copy(categories = Map("cat1" -> new RangeCategoryDescriptor("date", null, null, null)))))
  }
  @DataPoint def redeclareWithViewCategory = {
    val (up, down) = randomPair()
    Scenario(up, down, Some(up.copy(
      categories = Map("cat1" -> new RangeCategoryDescriptor("date", null, null, null)),
      views = List(EndpointViewDef(
        name = randomName(),
        categories = Map("cat1" -> new RangeCategoryDescriptor("date", "2012-01-01", "2012-01-14", "daily"))))))
    )
  }

  @AfterClass
  def tearDown {
    storeReferences.tearDown
  }
}
