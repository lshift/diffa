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

import org.junit.Assert._
import org.hamcrest.Matchers._
import scala.collection.Map
import org.joda.time.DateTime
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.frontend._
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.StoreReferenceContainer
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import org.slf4j.LoggerFactory
import org.junit.{Test, AfterClass, Before}
import net.lshift.diffa.kernel.preferences.FilteredItemType
import com.eaio.uuid.UUID
import java.sql.SQLIntegrityConstraintViolationException
import org.jooq.exception.DataAccessException

class JooqDomainConfigStoreTest {
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

  val domainName = "domain"
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

  val versionPolicyName1 = "TEST_VPNAME"
  val matchingTimeout = 120
  val versionPolicyName2 = "TEST_VPNAME_ALT"
  val pairKey = "TEST_PAIR"
  val pairDef = new PairDef(pairKey, versionPolicyName1, matchingTimeout, upstream1.name,
    downstream1.name, views = Seq(PairViewDef(name = "a-only")))

  val pair = DiffaPair(key = pairKey, domain = domain)
  val pairRef = DiffaPairRef(key = pairKey, domain = domainName)

  val repairAction = RepairActionDef(name="REPAIR_ACTION_NAME",
                                     scope=RepairAction.ENTITY_SCOPE,
                                     url="resend", pair=pairKey)

  val escalation = EscalationDef(name="esc", action = "test_action", pair = pairKey,
                                 event = EscalationEvent.UPSTREAM_MISSING,
                                 actionType = EscalationActionType.REPAIR,
                                 origin = EscalationOrigin.SCAN)

  val configKey = "foo"
  val configValue = "bar"

  val upstreamRenamed = "TEST_UPSTREAM_RENAMED"
  val pairRenamed = "TEST_PAIR_RENAMED"

  val user = User(name = "test_user", email = "dev_null@lshift.net", passwordEnc = "TEST")
  val user2 = User(name = "test_user2", email = "dev_null@lshift.net", passwordEnc = "TEST")
  val adminUser = User(name = "admin_user", email = "dev_null@lshift.net", passwordEnc = "TEST", superuser = true)

  def declareAll() {
    systemConfigStore.createOrUpdateDomain(domainName)
    domainConfigStore.createOrUpdateEndpoint(domainName, upstream1)
    domainConfigStore.createOrUpdateEndpoint(domainName, upstream2)
    domainConfigStore.createOrUpdateEndpoint(domainName, downstream1)
    domainConfigStore.createOrUpdateEndpoint(domainName, downstream2)
    domainConfigStore.createOrUpdatePair(domainName, pairDef)
    domainConfigStore.createOrUpdateRepairAction(domainName, repairAction)
    domainConfigStore.createOrUpdateEscalation(domainName, escalation)
    domainConfigStore.setConfigOption(domainName, configKey, configValue)
  }

  @Before
  def setUp {
    storeReferences.clearConfiguration(domainName)
    domainConfigStore.reset
  }

  def exists (e:EndpointDef, count:Int, offset:Int) : Unit = {
    val endpoints = domainConfigStore.listEndpoints(domainName).sortWith((a, b) => a.name < b.name)
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

    assertFalse(domainConfigStore.listPairs(domainName).isEmpty)
    assertFalse(domainConfigStore.allConfigOptions(domainName).isEmpty)
    assertFalse(domainConfigStore.listRepairActions(domainName).isEmpty)
    assertFalse(domainConfigStore.listEscalations(domainName).isEmpty)

    systemConfigStore.deleteDomain(domainName)

    assertTrue(domainConfigStore.listEndpoints(domainName).isEmpty)
    assertTrue(domainConfigStore.listPairs(domainName).isEmpty)
    assertTrue(domainConfigStore.allConfigOptions(domainName).isEmpty)
    assertTrue(domainConfigStore.listRepairActions(domainName).isEmpty)
    assertTrue(domainConfigStore.listEscalations(domainName).isEmpty)

    assertTrue(systemConfigStore.listDomains.filter(_ == domainName).isEmpty)
  }


  @Test
  def escalationsWithSameNameInSeparateDomainsMustHaveSeparateIdentities {
    val domain2 = domainName + "2"

    val escalations = Seq(domainName, domain2).map { dom =>
      systemConfigStore.createOrUpdateDomain(dom)
      domainConfigStore.createOrUpdateEndpoint(dom, upstream1.copy(name = dom + "-up"))
      domainConfigStore.createOrUpdateEndpoint(dom, downstream1.copy(name = dom + "-down"))
      domainConfigStore.createOrUpdatePair(dom,
        pairDef.copy(upstreamName = dom + "-up", downstreamName = dom + "-down"))
      val anEscalation = escalation.copy(name = "identicalName", pair = pairDef.key)
      // When the primary key on escalations is over (pair_key, name) then
      // this test will fail with a constraint violation, even though the
      // second escalation is in a different domain.

      domainConfigStore.createOrUpdateEscalation(dom, anEscalation)
      anEscalation
    }
    // And at this point, we need to ensure that we have successfully inserted two escalations total.
    assertThat(systemConfigStore.listDomains.flatMap { d => domainConfigStore.listEscalations(d) },
      is(equalTo(escalations)))
  }

  @Test
  def repairActionsWithSameNameInSeparateDomainsMustHaveSeparateIdentities = {
    val domain2 = domainName + "2"

    val repairActions = Seq(domainName, domain2).map { dom =>
      systemConfigStore.createOrUpdateDomain(dom)
      domainConfigStore.createOrUpdateEndpoint(dom, upstream1.copy(name = dom + "-up"))
      domainConfigStore.createOrUpdateEndpoint(dom, downstream1.copy(name = dom + "-down"))
      domainConfigStore.createOrUpdatePair(dom,
        pairDef.copy(upstreamName = dom + "-up", downstreamName = dom + "-down"))
      val aRepairAction = repairAction.copy(name = "identicalName", pair = pairDef.key)
      // When the primary key on repair_actions is over (pair_key, name) then
      // this test will fail with a constraint violation, even though the
      // second repair action is in a different domain.

      domainConfigStore.createOrUpdateRepairAction(dom, aRepairAction)
      aRepairAction
    }

    // And at this point, we need to ensure that we have successfully inserted two repair actions total.
    assertThat(systemConfigStore.listDomains.flatMap { d => domainConfigStore.listRepairActions(d) },
      is(equalTo(repairActions)))

  }


@Test
  def testDeclare {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)

    // Declare endpoints
    domainConfigStore.createOrUpdateEndpoint(domainName, upstream1)
    exists(upstream1, 1)

    domainConfigStore.createOrUpdateEndpoint(domainName, downstream1)
    exists(downstream1, 2)

    // Declare a pair
    domainConfigStore.createOrUpdatePair(domainName, pairDef)

    val retrPair = domainConfigStore.getPairDef(domainName, pairDef.key)
    assertEquals(pairKey, retrPair.key)
    assertEquals(upstream1.name, retrPair.upstreamName)
    assertEquals(downstream1.name, retrPair.downstreamName)
    assertEquals(versionPolicyName1, retrPair.versionPolicyName)
    assertEquals(matchingTimeout, retrPair.matchingTimeout)

    // Declare a repair action
    domainConfigStore.createOrUpdateRepairAction(domainName, repairAction)
    val retrActions = domainConfigStore.listRepairActionsForPair(domainName, retrPair.key)
    assertEquals(1, retrActions.length)
    assertEquals(Some(pairKey), retrActions.headOption.map(_.pair))
  }

  @Test
  def removingPairShouldRemoveAnyUserSettingsRelatedToThatPair {
    declareAll()

    systemConfigStore.createOrUpdateUser(user)
    domainConfigStore.makeDomainMember(domainName, user.name)
    userPreferencesStore.createFilteredItem(pairRef, user.name, FilteredItemType.SWIM_LANE)

    domainConfigStore.deletePair(pairRef)

  }

  @Test
  def removingDomainShouldRemoveAnyUserSettingsRelatedToThatDomain {
    declareAll()

    systemConfigStore.createOrUpdateUser(user)
    domainConfigStore.makeDomainMember(domainName, user.name)
    userPreferencesStore.createFilteredItem(pairRef, user.name, FilteredItemType.SWIM_LANE)

    systemConfigStore.deleteDomain(domainName)

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
    domainConfigStore.createOrUpdateEndpoint(domainName, endpoint)
    exists(endpoint, 1)
    val retrEndpoint = domainConfigStore.getEndpointDef(domainName, endpoint.name)
    val descriptor = retrEndpoint.categories(dateCategoryName).asInstanceOf[RangeCategoryDescriptor]
    assertEquals("individual", descriptor.maxGranularity)

  }

  @Test
  def testPairsAreValidatedBeforeUpdate() {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)
    // Declare endpoints
    domainConfigStore.createOrUpdateEndpoint(domainName, upstream1)
    exists(upstream1, 1)

    domainConfigStore.createOrUpdateEndpoint(domainName, downstream1)
    exists(downstream1, 2)

    pairDef.scanCronSpec = "invalid"

    try {
      domainConfigStore.createOrUpdatePair(domainName, pairDef)
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
    domainConfigStore.createOrUpdateEndpoint(domainName, upstream1)

    upstream2.scanUrl = upstream1.scanUrl
    domainConfigStore.createOrUpdateEndpoint(domainName, upstream2)

    exists(upstream1, 2, 0)
    exists(upstream2, 2, 1)
  }


  @Test
  def testUpdateEndpoint: Unit = {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)
    // Create endpoint
    domainConfigStore.createOrUpdateEndpoint(domainName, upstream1)
    exists(upstream1, 1)

    domainConfigStore.deleteEndpoint(domainName, upstream1.name)
    expectMissingObject("endpoint") {
      domainConfigStore.getEndpointDef(domainName, upstream1.name)
    }
        
    // Change its name
    domainConfigStore.createOrUpdateEndpoint(domainName, EndpointDef(name = upstreamRenamed,
                                                                     scanUrl = upstream1.scanUrl,
                                                                     inboundUrl = "changes"))

    val retrieved = domainConfigStore.getEndpointDef(domainName, upstreamRenamed)
    assertEquals(upstreamRenamed, retrieved.name)
  }

  @Test
  def testEndpointCollationIsPersisted = {
    systemConfigStore.createOrUpdateDomain(domainName)
    domainConfigStore.createOrUpdateEndpoint(domainName, upstream1.copy(collation = UnicodeCollationOrdering.name))
    val retrieved = domainConfigStore.getEndpointDef(domainName, upstream1.name)
    assertEquals(UnicodeCollationOrdering.name, retrieved.collation)
  }

  @Test
  def testUpdatePair: Unit = {
    declareAll

    // Rename, change a few fields and swap endpoints by deleting and creating new
    domainConfigStore.deletePair(domainName, pairKey)
    expectMissingObject("pair") {
      domainConfigStore.getPairDef(domainName, pairKey)
    }

    domainConfigStore.createOrUpdatePair(domainName, PairDef(pairRenamed, versionPolicyName2, DiffaPair.NO_MATCHING,
      downstream1.name, upstream1.name, "0 0 * * * ?", scanCronEnabled = false, allowManualScans = false))
    
    val retrieved = domainConfigStore.getPairDef(domainName, pairRenamed)
    assertEquals(pairRenamed, retrieved.key)
    assertEquals(downstream1.name, retrieved.upstreamName) // check endpoints are swapped
    assertEquals(upstream1.name, retrieved.downstreamName)
    assertEquals(versionPolicyName2, retrieved.versionPolicyName)
    assertEquals("0 0 * * * ?", retrieved.scanCronSpec)
    assertEquals(false, retrieved.scanCronEnabled)
    assertEquals(false, retrieved.allowManualScans)
    assertEquals(DiffaPair.NO_MATCHING, retrieved.matchingTimeout)
  }

  @Test
  def testDeleteEndpointCascade: Unit = {
    declareAll

    assertEquals(upstream1.name, domainConfigStore.getEndpointDef(domainName, upstream1.name).name)
    domainConfigStore.deleteEndpoint(domainName, upstream1.name)
    expectMissingObject("endpoint") {
      domainConfigStore.getEndpointDef(domainName, upstream1.name)
    }
    expectMissingObject("pair") {
      domainConfigStore.getPairDef(domainName, pairKey) // delete should cascade
    }
  }

  @Test
  def testDeletePair {
    declareAll

    assertEquals(pairKey, domainConfigStore.getPairDef(domainName, pairKey).key)
    domainConfigStore.deletePair(domainName, pairKey)
    expectMissingObject("pair") {
      domainConfigStore.getPairDef(domainName, pairKey)
    }
  }

  @Test
  def testDeletePairCascade {
    declareAll()
    assertEquals(Some(repairAction.name), domainConfigStore.listRepairActions(domainName).headOption.map(_.name))
    domainConfigStore.deletePair(domainName, pairKey)
    expectMissingObject("repair action") {
      domainConfigStore.getRepairActionDef(domainName, repairAction.name, pairKey)
    }
  }

  @Test
  def testDeleteRepairAction {
    declareAll
    assertEquals(Some(repairAction.name), domainConfigStore.listRepairActions(domainName).headOption.map(_.name))

    domainConfigStore.deleteRepairAction(domainName, repairAction.name, pairKey)
    expectMissingObject("repair action") {
      domainConfigStore.getRepairActionDef(domainName, repairAction.name, pairKey)
    }
  }

  @Test
  def testDeleteMissing {
    expectMissingObject("endpoint") {
      domainConfigStore.deleteEndpoint(domainName, "MISSING_ENDPOINT")
    }

    expectMissingObject("domain/MISSING_PAIR") {
      domainConfigStore.deletePair(domainName, "MISSING_PAIR")
    }
  }

  @Test
  def shouldBeAbleToRedeclareEndpoints = {

    // Note that this is a heuristic that attempts to flush out all of the main state transitions
    // that we can think of. It is in no way systematic or exhaustive.
    // If somebody knew their way around property based testing, then could break a leg here.

    systemConfigStore.createOrUpdateDomain("domain")

    def verifyEndpoints(endpoints:Seq[EndpointDef]) {
      endpoints.foreach(e => {
        domainConfigStore.createOrUpdateEndpoint("domain", e)

        val endpoint = domainConfigStore.getEndpointDef("domain", e.name)
        assertEquals(e, endpoint)
      })

      val result = domainConfigStore.listEndpoints("domain")
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

    val up_v3 = up_v0.copy(views = List(EndpointViewDef(
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
    val pair = domainConfigStore.getPairDef(domainName, pairKey)
    assertNotNull(pair.upstreamName)
    assertNotNull(pair.downstreamName)
    val upstream = domainConfigStore.getEndpointDef(domainName, pair.upstreamName)
    val downstream = domainConfigStore.getEndpointDef(domainName, pair.downstreamName)
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
    val endpoint = domainConfigStore.getEndpointDef(domainName, upstream2.name)
    assertNotNull(endpoint.categories)
    val descriptor = endpoint.categories(dateCategoryName).asInstanceOf[SetCategoryDescriptor]
    assertEquals(setCategoryValues, descriptor.values.toSet)
  }

  @Test
  def prefixCategory = {
    declareAll
    val endpoint = domainConfigStore.getEndpointDef(domainName, downstream2.name)
    assertNotNull(endpoint.categories)
    val descriptor = endpoint.categories(stringCategoryName).asInstanceOf[PrefixCategoryDescriptor]
    assertEquals(1, descriptor.prefixLength)
    assertEquals(3, descriptor.maxLength)
    assertEquals(1, descriptor.step)
  }

  @Test
  def shouldStoreViewsOnEndpoints = {
    declareAll
    val endpoint = domainConfigStore.getEndpointDef(domainName, upstream2.name)
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
    val pair = domainConfigStore.getPairDef(domainName, pairKey)
    assertNotNull(pair.views)
    assertEquals(1, pair.views.length)

    val view = pair.views(0)
    assertEquals("a-only", view.name)
  }

  @Test
  def shouldAllowViewsWithTheSameNameToBeAppliedToBothSidesOfAPair = {
    val domain = new UUID().toString

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

    systemConfigStore.createOrUpdateDomain(domain)

    domainConfigStore.createOrUpdateEndpoint(domain, upstream)
    domainConfigStore.createOrUpdateEndpoint(domain, downstream)
    domainConfigStore.createOrUpdatePair(domain, pair)

    // It should not be possible to create more than one view with the same name within the same endpoint

    val uv3 = uv2.copy()
    val invalidUpstreamViews = Seq(uv1,uv2,uv3)
    val invalidUpstream = EndpointDef(name = new UUID().toString, categories = parentCategories, views = invalidUpstreamViews)

    try {
      domainConfigStore.createOrUpdateEndpoint(domain, invalidUpstream)
      fail("Should have thrown integrity error")
    }
    catch {
      case x:DataAccessException => // expected
    }
    finally {
      systemConfigStore.deleteDomain(domain)
    }

  }

  @Test
  def testApplyingDefaultConfigOption = {
    assertEquals("defaultVal", domainConfigStore.configOptionOrDefault(domainName,"some.option", "defaultVal"))
  }

  @Test
  def testReturningNoneForConfigOption {
    assertEquals(None, domainConfigStore.maybeConfigOption(domainName, "some.option"))
  }

  @Test
  def testRetrievingConfigOption = {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)
    domainConfigStore.setConfigOption(domainName, "some.option2", "storedVal")
    assertEquals("storedVal", domainConfigStore.configOptionOrDefault(domainName, "some.option2", "defaultVal"))
    assertEquals(Some("storedVal"), domainConfigStore.maybeConfigOption(domainName, "some.option2"))
  }

  @Test
  def testUpdatingConfigOption = {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)

    domainConfigStore.setConfigOption(domainName, "some.option3", "storedVal")
    domainConfigStore.setConfigOption(domainName, "some.option3", "storedVal2")
    assertEquals("storedVal2", domainConfigStore.configOptionOrDefault(domainName, "some.option3", "defaultVal"))
    assertEquals(Some("storedVal2"), domainConfigStore.maybeConfigOption(domainName, "some.option3"))
  }

  @Test
  def testRemovingConfigOption = {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)

    domainConfigStore.setConfigOption(domainName, "some.option3", "storedVal")
    domainConfigStore.clearConfigOption(domainName, "some.option3")
    assertEquals("defaultVal", domainConfigStore.configOptionOrDefault(domainName, "some.option3", "defaultVal"))
    assertEquals(None, domainConfigStore.maybeConfigOption(domainName, "some.option3"))
  }

  @Test
  def testRetrievingAllOptions = {
    // declare the domain
    systemConfigStore.createOrUpdateDomain(domainName)

    domainConfigStore.setConfigOption(domainName, "some.option3", "storedVal")
    domainConfigStore.setConfigOption(domainName, "some.option4", "storedVal3")
    assertEquals(Map("some.option3" -> "storedVal", "some.option4" -> "storedVal3"), domainConfigStore.allConfigOptions(domainName))
  }

  @Test
  def testRetrievingOptionsIgnoresSystemOptions = {
    // declare the child domain
    systemConfigStore.createOrUpdateDomain(domainName)

    domainConfigStore.setConfigOption(domainName, "some.option3", "storedVal")
    systemConfigStore.setSystemConfigOption("some.option4", "storedVal3")
    assertEquals(Map("some.option3" -> "storedVal"), domainConfigStore.allConfigOptions(domainName))
  }

  @Test
  def shouldBeAbleToManageDomainMembership = {

    def assertIsDomainMember(member:Member, expectation:Boolean) = {
      val members = domainConfigStore.listDomainMembers(domain.name)
      val isMember = members.contains(member)
      assertEquals(expectation, isMember)

      val userMembers = systemConfigStore.listDomainMemberships(user.name)
      val hasDomainMember = userMembers.contains(member)
      assertEquals(expectation, hasDomainMember)
    }

    systemConfigStore.createOrUpdateDomain(domainName)
    systemConfigStore.createOrUpdateUser(user)

    val member = domainConfigStore.makeDomainMember(domain.name, user.name)
    assertIsDomainMember(member, true)

    domainConfigStore.removeDomainMembership(domain.name, user.name)
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

    val v1 = domainConfigStore.getConfigVersion(domainName)
    domainConfigStore.createOrUpdateEndpoint(domainName, up)
    verifyDomainConfigVersionWasUpgraded(domainName, v1)

    val v2 = domainConfigStore.getConfigVersion(domainName)
    domainConfigStore.createOrUpdateEndpoint(domainName, down)
    verifyDomainConfigVersionWasUpgraded(domainName, v2)

    val v3 = domainConfigStore.getConfigVersion(domainName)
    domainConfigStore.createOrUpdatePair(domainName, pair)
    verifyDomainConfigVersionWasUpgraded(domainName, v3)

    val v4 = domainConfigStore.getConfigVersion(domainName)
    domainConfigStore.deletePair(domainName, pair.key)
    verifyDomainConfigVersionWasUpgraded(domainName, v4)

    val v5 = domainConfigStore.getConfigVersion(domainName)
    domainConfigStore.deleteEndpoint(domainName, up.name)
    domainConfigStore.deleteEndpoint(domainName, down.name)
    verifyDomainConfigVersionWasUpgraded(domainName, v5)

  }

  private def verifyDomainConfigVersionWasUpgraded(domain:String, oldVersion:Int) {
    val currentVersion = domainConfigStore.getConfigVersion(domain)
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

  @AfterClass
  def tearDown {
    storeReferences.tearDown
  }
}
