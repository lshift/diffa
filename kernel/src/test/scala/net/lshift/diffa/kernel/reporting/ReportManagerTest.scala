package net.lshift.diffa.kernel.reporting

import collection.mutable.{ListBuffer}
import org.junit.{Before, Test}
import net.lshift.diffa.kernel.frontend.{PairReportDef, EndpointDef, PairDef}
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.config.{DiffaPairRef, Domain, HibernateDomainConfigStoreTest}
import org.joda.time.DateTime
import net.lshift.diffa.kernel.differencing.{HibernateDomainDifferenceStore}
import org.junit.Assert._

class ReportManagerTest {
  val domain = "reportingDomain"
  val pair = DiffaPairRef(key = "p1", domain = domain)
  val diagnostics = createNiceMock(classOf[DiagnosticsManager])

  val configStore = HibernateDomainConfigStoreTest.domainConfigStore
  val systemConfigStore = HibernateDomainConfigStoreTest.systemConfigStore
  val diffStore =new HibernateDomainDifferenceStore(
    HibernateDomainConfigStoreTest.sessionFactory, HibernateDomainConfigStoreTest.cacheManager,
    HibernateDomainConfigStoreTest.dialect)
  val reportManager = new ReportManager(configStore, diffStore, diagnostics)

  @Before
  def prepareEnvironment() {
    HibernateDomainConfigStoreTest.clearAllConfig
    diffStore.clearAllDifferences

    systemConfigStore.createOrUpdateDomain(Domain(domain))
    configStore.createOrUpdateEndpoint(domain,
      EndpointDef("e", contentType = "application/json"))
    configStore.createOrUpdatePair(domain,
      PairDef(pair.key, versionPolicyName = "same", upstreamName = "e", downstreamName = "e"))
  }

  @Test
  def shouldDispatchReport() {
    val reports = new ListBuffer[String]
    ReportListenerUtil.withReportListener(reports, reportListenerUrl => {
      // Create our report
      configStore.createOrUpdateReport(domain, PairReportDef("send diffs", "p1", "differences", reportListenerUrl))

      // Add some differences
      diffStore.addReportableUnmatchedEvent(VersionID(pair, "id1"), new DateTime, "a", "b", new DateTime)
      diffStore.addReportableUnmatchedEvent(VersionID(pair, "id2"), new DateTime, null, "b", new DateTime)
      diffStore.addReportableUnmatchedEvent(VersionID(pair, "id3"), new DateTime, "a", null, new DateTime)

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