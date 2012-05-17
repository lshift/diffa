package net.lshift.diffa.kernel.diag

import org.junit.Assert._
import org.hamcrest.CoreMatchers._
import org.hamcrest.number.OrderingComparison._
import org.joda.time.DateTime
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.util.HamcrestDateTimeHelpers._
import net.lshift.diffa.kernel.differencing.{PairScanState}
import org.junit.{Before, Test}
import java.io.{FileInputStream, File}
import org.apache.commons.io.{IOUtils, FileDeleteStrategy}
import java.util.zip.ZipInputStream
import org.junit.experimental.theories.{DataPoints, DataPoint, Theory}
import net.lshift.diffa.kernel.frontend.{PairDef, FrontendConversions}
import net.lshift.diffa.kernel.config._
import limits.{ExplainFiles, DiagnosticEventBufferSize, Unlimited}
import system.SystemConfigStore

class LocalDiagnosticsManagerTest {
  val domainConfigStore = createStrictMock(classOf[DomainConfigStore])
  val systemConfigStore = createStrictMock(classOf[SystemConfigStore])
  val serviceLimitsStore = createStrictMock(classOf[ServiceLimitsStore])

  val explainRoot = new File("target/explain")
  val diagnostics = new LocalDiagnosticsManager(systemConfigStore, domainConfigStore, serviceLimitsStore, explainRoot.getPath)

  val domainName = "domain"
  val testDomain = Domain(name=domainName)

  val u = Endpoint(name = "1", scanUrl = "http://foo.com/scan", inboundUrl = "changes")
  val d = Endpoint(name = "2", scanUrl = "http://bar.com/scan", inboundUrl = "changes")

  val pair1 = DiffaPair(key = "pair1", domain = testDomain, maxExplainFiles = 1, versionPolicyName = "policy", upstream = u.name, downstream = d.name)
  val pair2 = DiffaPair(key = "pair2", domain = testDomain, eventsToLog = 0, versionPolicyName = "policy", upstream = u.name, downstream = d.name)

  @Before
  def cleanupExplanations() {
    FileDeleteStrategy.FORCE.delete(explainRoot)
  }

  @Test
  def shouldAcceptAndStoreLogEventForPair() {
    val pairKey = "moderateLoggingPair"
    val pair = DiffaPairRef(pairKey, domainName)

    expectEventBufferLimitQuery(domainName, pairKey, 10)

    diagnostics.logPairEvent(DiagnosticLevel.INFO, pair, "Some msg")

    val events = diagnostics.queryEvents(pair, 100)
    assertEquals(1, events.length)
    assertThat(events(0).timestamp,
      is(allOf(after((new DateTime).minusSeconds(5)), before((new DateTime).plusSeconds(1)))))
    assertEquals(DiagnosticLevel.INFO, events(0).level)
    assertEquals("Some msg", events(0).msg)
  }

  @Test
  def shouldLimitNumberOfStoredLogEventsToMax() {

    val pairKey = "maxLoggingPair"

    expectEventBufferLimitQuery(domainName, pairKey, 100)

    val pair = DiffaPairRef(pairKey, domainName)

    for (i <- 1 until 1000)
      diagnostics.logPairEvent(DiagnosticLevel.INFO, pair, "Some msg")

    assertEquals(100, diagnostics.queryEvents(pair, 1000).length)
  }

  @Test
  def shouldTrackStateOfPairsWithinDomain() {
    expectPairListFromConfigStore(pair1 :: pair2 :: Nil)

    // Query for the states associated. We should get back an entry for pair in "unknown"
    assertEquals(Map("pair1" -> PairScanState.UNKNOWN, "pair2" -> PairScanState.UNKNOWN),
      diagnostics.retrievePairScanStatesForDomain("domain"))

    // Notify that the pair1 is now in Up To Date state
    diagnostics.pairScanStateChanged(pair1.asRef, PairScanState.UP_TO_DATE)
    assertEquals(Map("pair1" -> PairScanState.UP_TO_DATE, "pair2" -> PairScanState.UNKNOWN),
      diagnostics.retrievePairScanStatesForDomain("domain"))

    // Notify that the pair2 is now in Failed state
    diagnostics.pairScanStateChanged(pair2.asRef, PairScanState.FAILED)
    assertEquals(Map("pair1" -> PairScanState.UP_TO_DATE, "pair2" -> PairScanState.FAILED),
      diagnostics.retrievePairScanStatesForDomain("domain"))

    // Report a scan as being started. We should enter the scanning state again
    diagnostics.pairScanStateChanged(pair1.asRef, PairScanState.SCANNING)    // Simulate the supervisor indicating a scan start
    diagnostics.pairScanStateChanged(pair2.asRef, PairScanState.SCANNING)    // Simulate the supervisor indicating a scan start
    assertEquals(Map("pair1" -> PairScanState.SCANNING, "pair2" -> PairScanState.SCANNING),
      diagnostics.retrievePairScanStatesForDomain("domain"))

  }

  @Test
  def shouldNotReportStateOfDeletedPairs() {
    // Wire up pair1 and pair2 to exist, and provide a status
    expectPairListFromConfigStore(pair1 :: pair2 :: Nil)

    diagnostics.pairScanStateChanged(pair1.asRef, PairScanState.SCANNING)
    assertEquals(Map("pair1" -> PairScanState.SCANNING, "pair2" -> PairScanState.UNKNOWN), diagnostics.retrievePairScanStatesForDomain("domain"))

    // Remove the pair, and report it
    reset(domainConfigStore)
    expect(domainConfigStore.listPairs(domainName)).
        andStubReturn(Seq(FrontendConversions.toPairDef(pair2)))
    replayDomainConfig
    diagnostics.onDeletePair(pair1.asRef)
    assertEquals(Map("pair2" -> PairScanState.UNKNOWN), diagnostics.retrievePairScanStatesForDomain(domainName))
  }
  
  @Test
  def shouldNotGenerateAnyOutputWhenCheckpointIsCalledOnASilentPair() {
    val key = "quiet"
    diagnostics.checkpointExplanations(DiffaPairRef(key, domainName))

    val pairDir = new File(explainRoot, "%s/%s".format(domainName, key))
    if (pairDir.exists())
      assertEquals(0, pairDir.listFiles().length)
  }

  @Test
  def shouldGenerateOutputWhenExplanationsHaveBeenLogged() {
    val pairKey = "explained"
    val pair = DiffaPairRef(pairKey, domainName)

    expectMaxExplainFilesLimitQuery(domainName, pairKey, 1)

    diagnostics.logPairExplanation(pair, "Test Case", "Diffa did something")
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "%s/%s".format(domainName, pairKey))
    val zips = pairDir.listFiles()

    assertEquals(1, zips.length)
    assertTrue(zips(0).getName.endsWith(".zip"))

    val zis = new ZipInputStream(new FileInputStream(zips(0)))
    val entry = zis.getNextEntry
    assertEquals("explain.log", entry.getName)

    val content = IOUtils.toString(zis)
    assertTrue(content.contains("[Test Case] Diffa did something"))
  }

  @Test
  def shouldIncludeContentsOfObjectsAdded() {
    val pairKey = "explainedobj"
    val pair = DiffaPairRef(pairKey, domainName)

    expectMaxExplainFilesLimitQuery(domainName, pairKey, 1)

    diagnostics.writePairExplanationObject(pair, "Test Case", "upstream.123.json", os => {
      os.write("{a: 1}".getBytes("UTF-8"))
    })
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "%s/%s".format(domainName, pairKey))
    val zips = pairDir.listFiles()

    assertEquals(1, zips.length)
    assertTrue(zips(0).getName.endsWith(".zip"))

    val zis = new ZipInputStream(new FileInputStream(zips(0)))

    var entry = zis.getNextEntry
    while (entry != null && entry.getName != "upstream.123.json") entry = zis.getNextEntry

    if (entry == null) fail("Could not find entry upstream.123.json")

    val content = IOUtils.toString(zis)
    assertEquals("{a: 1}", content)
  }

  @Test
  def shouldAddLogMessageIndicatingObjectWasAttached() {
    val pairKey = "explainedobj"
    val pair = DiffaPairRef(pairKey, domainName)

    expectMaxExplainFilesLimitQuery(domainName, pairKey, 1)

    diagnostics.writePairExplanationObject(pair, "Test Case", "upstream.123.json", os => {
      os.write("{a: 1}".getBytes("UTF-8"))
    })
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "%s/%s".format(domainName, pairKey))
    val zips = pairDir.listFiles()

    assertEquals(1, zips.length)
    assertTrue(zips(0).getName.endsWith(".zip"))

    val zis = new ZipInputStream(new FileInputStream(zips(0)))

    var entry = zis.getNextEntry
    while (entry != null && entry.getName != "explain.log") entry = zis.getNextEntry

    if (entry == null) fail("Could not find entry explain.log")

    val content = IOUtils.toString(zis)
    assertTrue(content.contains("[Test Case] Attached object upstream.123.json"))
  }

  @Test
  def shouldCreateMultipleOutputsWhenMultipleNonQuietRunsHaveBeenMade() {
    val pairKey = "explained_20_2"

    expectMaxExplainFilesLimitQuery(domainName, pairKey, 2)

    val pair = DiffaPairRef(pairKey, domainName)

    diagnostics.logPairExplanation(pair, "Test Case", "Diffa did something")
    diagnostics.checkpointExplanations(pair)

    diagnostics.logPairExplanation(pair, "Test Case" , "Diffa did something else")
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "%s/%s".format(domainName, pairKey))
    val zips = pairDir.listFiles()

    assertEquals(2, zips.length)
  }

  @Test
  def shouldKeepNumberOfExplanationFilesUnderControl() {
    val filesToKeep = 20
    val generateCount = 100
    val pairKey = "controlled_100_20"
    val pair = DiffaPairRef(pairKey, domainName)

    expectMaxExplainFilesLimitQuery(domainName, pairKey, filesToKeep)

    for (i <- 1 until generateCount) {
      diagnostics.logPairExplanation(pair, "Test Case", i.toString)
      diagnostics.checkpointExplanations(pair)
    }

    val pairDir = new File(explainRoot, "%s/%s".format(domainName, pairKey))
    val zips = pairDir.listFiles()

    assertEquals(filesToKeep, zips.length)

    zips.foreach(z => verifyZipContent(generateCount - filesToKeep)(z))
  }
  
  private def verifyZipContent(earliestEntryNum: Int)(zipFile: File) {
    val zipInputStream = new ZipInputStream(new FileInputStream(zipFile))
    zipInputStream.getNextEntry
    val content = IOUtils.toString(zipInputStream)
    zipInputStream.close()
    
    val entryNum = content.trim().split(" ").last
    assertThat(new Integer(entryNum), is(greaterThanOrEqualTo(new Integer(earliestEntryNum))))
  }

  private def expectEventBufferLimitQuery(domain:String, pairKey:String, eventBufferSize:Int) = {

    expect(serviceLimitsStore.
      getEffectiveLimitByNameForPair(domain, pairKey, DiagnosticEventBufferSize)).
      andReturn(eventBufferSize).atLeastOnce()

    replay(serviceLimitsStore)
  }

  private def expectMaxExplainFilesLimitQuery(domain:String, pairKey:String, eventBufferSize:Int) = {

    expect(serviceLimitsStore.
      getEffectiveLimitByNameForPair(domain, pairKey, ExplainFiles)).
      andReturn(eventBufferSize).atLeastOnce()

    replay(serviceLimitsStore)
  }

  private def expectPairListFromConfigStore(pairs: Seq[DiffaPair]) {
    val pairDefs = pairs map FrontendConversions.toPairDef
    expect(domainConfigStore.listPairs(domainName)).
      andStubReturn(pairDefs)

    pairDefs foreach { pairDef =>
      expect(domainConfigStore.getPairDef(domainName, pairDef.key)).
        andStubReturn(pairDef)
    }
    replayDomainConfig
  }

  def replayDomainConfig {
    replay(domainConfigStore)
  }
  
  def replaySystemConfig {
    replay(systemConfigStore)
  }
}
