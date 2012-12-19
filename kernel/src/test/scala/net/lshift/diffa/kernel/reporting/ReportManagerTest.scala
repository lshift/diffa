package net.lshift.diffa.kernel.reporting

import collection.mutable.{ListBuffer}
import net.lshift.diffa.kernel.frontend.{PairReportDef, EndpointDef, PairDef}
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.events.VersionID
import org.joda.time.DateTime
import org.junit.Assert._
import net.lshift.diffa.kernel.StoreReferenceContainer
import org.junit.{After, AfterClass, Before, Test}
import net.lshift.diffa.kernel.config.{Space, PairRef, Domain}
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import scala.collection.JavaConversions._
import org.apache.commons.lang.RandomStringUtils

class ReportManagerTest {
  private val storeReferences = ReportManagerTest.storeReferences

  private val systemConfigStore = storeReferences.systemConfigStore
  private val domainConfigStore = storeReferences.domainConfigStore
  private val domainDiffStore = storeReferences.domainDifferenceStore

  val spaceName = "reportingDomain"

  var space:Space = null

  var pair:PairRef = null
  val diagnostics = createNiceMock(classOf[DiagnosticsManager])

  val reportManager = new ReportManager(domainConfigStore, domainDiffStore, diagnostics)

  val e1 = EndpointDef(name = "e1")
  val e2 = EndpointDef(name = "e2")

  @Before
  def prepareEnvironment() {
    space = systemConfigStore.createOrUpdateSpace(spaceName)
    pair = PairRef(name = "p1", space = space.id)


    domainDiffStore.clearAllDifferences

    domainConfigStore.createOrUpdateEndpoint(space.id, e1)
    domainConfigStore.createOrUpdateEndpoint(space.id, e2)
  }

  @After
  def clearUp {
    storeReferences.clearConfiguration(space.id)
  }

  @Test
  def shouldDispatchReport() {
    val reports = new ListBuffer[String]
    ReportListenerUtil.withReportListener(reports, reportListenerUrl => {
      // Create our pair/report
      domainConfigStore.createOrUpdatePair(space.id,
        PairDef(pair.name, versionPolicyName = "same", upstreamName = e1.name, downstreamName = e2.name,
                reports = Set(PairReportDef("send diffs", "differences", reportListenerUrl))))

      // Add some differences
      domainDiffStore.addReportableUnmatchedEvent(VersionID(pair, "id1"), new DateTime, "a", "b", new DateTime)
      domainDiffStore.addReportableUnmatchedEvent(VersionID(pair, "id2"), new DateTime, null, "b", new DateTime)
      domainDiffStore.addReportableUnmatchedEvent(VersionID(pair, "id3"), new DateTime, "a", null, new DateTime)

      // Run the report
      reportManager.executeReport(pair, "send diffs")

      // Ensure that we got an appropriate report
      assertEquals(1, reports.length)
      val report = reports(0)
      val lines = report.lines.toSeq
      assertEquals(4, lines.length)   // Header line + 3 difference lines
      assertEquals("detection date,entity id,upstream version,downstream version,state", lines(0))
      val headerKeys = lines(0).split(",")
      val Seq(id1, id2, id3) = lines.drop(1).map(l => headerKeys.zip(l.split(",")).toMap).sortBy(l => l("entity id"))

      assertEquals("a", id1("upstream version"))
      assertEquals("", id2("upstream version"))
      assertEquals("a", id3("upstream version"))

      assertEquals("b", id1("downstream version"))
      assertEquals("b", id2("downstream version"))
      assertEquals("", id3("downstream version"))

      assertEquals("version-mismatch", id1("state"))
      assertEquals("missing-from-upstream", id2("state"))
      assertEquals("missing-from-downstream", id3("state"))
    })
  }
}

object ReportManagerTest {
  private[ReportManagerTest] val env =
    TestDatabaseEnvironments.uniqueEnvironment("target/reportManagerTest")

  private[ReportManagerTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  @AfterClass
  def cleanupSchema {
    storeReferences.tearDown
  }
}