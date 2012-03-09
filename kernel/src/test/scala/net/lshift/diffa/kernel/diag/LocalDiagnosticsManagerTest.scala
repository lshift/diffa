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
import system.SystemConfigStore

class LocalDiagnosticsManagerTest {
  val domainConfigStore = createStrictMock(classOf[DomainConfigStore])
  val systemConfigStore = createStrictMock(classOf[SystemConfigStore])

  val explainRoot = new File("target/explain")
  val diagnostics = new LocalDiagnosticsManager(systemConfigStore, domainConfigStore, explainRoot.getPath)

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
    val key = "moderateLoggingPair"
    val pair = DiffaPairRef(key, domainName)
    setPairExplainLimits(key, 10, 1)
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
    val key = "maxLoggingPair"
    val pair = DiffaPairRef(key, domainName)
    setPairExplainLimits(key, 100, 1)
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
    val key = "explained"
    val pair = DiffaPairRef(key, domainName)

    setPairExplainLimits(key, 1, 1)
    diagnostics.logPairExplanation(pair, "Test Case", "Diffa did something")
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "%s/%s".format(domainName, key))
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
    val key = "explainedobj"
    val pair = DiffaPairRef(key, domainName)
    setPairExplainLimits(key, 1, 1)

    diagnostics.writePairExplanationObject(pair, "Test Case", "upstream.123.json", os => {
      os.write("{a: 1}".getBytes("UTF-8"))
    })
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "%s/%s".format(domainName, key))
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
    val key = "explainedobj"
    val pair = DiffaPairRef(key, domainName)
    setPairExplainLimits(key, 1, 1)

    diagnostics.writePairExplanationObject(pair, "Test Case", "upstream.123.json", os => {
      os.write("{a: 1}".getBytes("UTF-8"))
    })
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "%s/%s".format(domainName, key))
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
    val key = "explained_20_2"
    val pair = DiffaPairRef(key, domainName)
    setPairExplainLimits(key, 50, 2)

    diagnostics.logPairExplanation(pair, "Test Case", "Diffa did something")
    diagnostics.checkpointExplanations(pair)

    diagnostics.logPairExplanation(pair, "Test Case" , "Diffa did something else")
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "%s/%s".format(domainName, key))
    val zips = pairDir.listFiles()

    assertEquals(2, zips.length)
  }

  @Test
  def shouldKeepNumberOfExplanationFilesUnderControl() {
    val filesToKeep = 20
    val generateCount = 100
    val key = "controlled_100_20"
    val pair = DiffaPairRef(key, domainName)
    setPairExplainLimits(key, generateCount, filesToKeep)

    for (i <- 1 until generateCount) {
      diagnostics.logPairExplanation(pair, "Test Case", i.toString)
      diagnostics.checkpointExplanations(pair)
    }

    val pairDir = new File(explainRoot, "%s/%s".format(domainName, key))
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

  @Test
  def shouldMakeOnlyAsManyExplanationFilesAsConfiguredForPair {
    val someExtra = 100 // a few more than the limit, to ensure that the limit works.

    List(0, 1, 2, 3, 5, 10, 20) foreach { i =>
      val key = "limited_to_%d_%d".format(i + someExtra, i)
      setPairExplainLimits(key, i + someExtra, i)
      verifyExplanationFileCount(i, DiffaPairRef(key, domainName))
      reset(domainConfigStore)
    }
  }

  @Test
  def shouldMakeNoExplanationFilesForDefaultConfiguredPair {
    val key = "limited_to_0_by_default"
    setDefaultPairExplainLimits(key)
    verifyExplanationFileCount(0, DiffaPairRef(key, domainName))
  }

  @Test
  def shouldLimitExplanationFilesDespitePairConfiguration {
    val key = "limited_to_fixed_file_count"
    setPairExplainLimits(key, 500, 22)
    verifyExplanationFileCount(20, DiffaPairRef(key, domainName))
  }

  @Test
  def shouldUsePairConfigurationToLimitExplanations {
    val key = "limited_by_pair_to_0_files"
    setDefaultPairExplainLimits(key)

    verifyExplanationFileCount(0, DiffaPairRef(key, domainName))
  }
  
  @Test
  def shouldLimitExplanationFilesToSystemLimit {
    val key = "limited_to_2_by_system_config"
    val systemExplanationFileLimit = 2
    setPairExplainLimitsWithSystemLimits(key, 50, 10, 20, systemExplanationFileLimit)
    verifyExplanationFileCount(systemExplanationFileLimit, DiffaPairRef(key, domainName))
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

  private def setDefaultPairExplainLimits(key: String) {
    val pair = makeDiffaPair(key)

    expect(domainConfigStore.getPairDef(pair.domain.name, pair.key)).
      andStubReturn(FrontendConversions.toPairDef(pair))
    replayDomainConfig
  }

  private def setPairExplainLimitsWithSystemLimits(key: String,
                                                   pairEventsToLog: Int,
                                                   pairMaxExplainFiles: Int,
                                                   systemEventsToLog: Int,
                                                   systemMaxExplainFiles: Int) {
    val pair = makeDiffaPairWithLimits(key, pairEventsToLog, pairMaxExplainFiles)

    expect(domainConfigStore.getPairDef(pair.domain.name, pair.key)).
      andStubReturn(FrontendConversions.toPairDef(pair))

    expect(systemConfigStore.maybeSystemConfigOption(ConfigOption.eventExplanationLimitKey)).
      andStubReturn(Some(String.valueOf(systemEventsToLog)))
    expect(systemConfigStore.maybeSystemConfigOption(ConfigOption.explainFilesLimitKey)).
      andStubReturn(Some(String.valueOf(systemMaxExplainFiles)))

    replay(domainConfigStore, systemConfigStore)
  }

  private def setPairExplainLimits(key: String, eventsToLog: Int, maxExplainFiles: Int) {
    val pair = makeDiffaPairWithLimits(key, eventsToLog, maxExplainFiles)

    expect(domainConfigStore.getPairDef(pair.domain.name, pair.key)).
      andStubReturn(FrontendConversions.toPairDef(pair))
    replayDomainConfig
  }

  private def verifyExplanationFileCount(expectedCount: Int, diffaPair: DiffaPairRef) {
    val key = diffaPair.key
    val domain = diffaPair.domain
    val generationCount = 21

    for (i <- 1 to generationCount) {
      diagnostics.logPairExplanation(diffaPair, "Non-standard explanation count", "Test action %d".format(i))
      diagnostics.checkpointExplanations(diffaPair)
      // a hack to allow time for the filesystem to write the file and
      // provide discrete timestamps on the explanation files.
      Thread.sleep(2)
    }

    val pairDir = new File(explainRoot, "%s/%s".format(domain, key))
    val zips = pairDir.listFiles
    val zipsFound = zips match {
      case null => 0
      case _ => zips.length
    }

    assertEquals(expectedCount, zipsFound)

    if (zips != null) {
      zips foreach verifyZipContent(generationCount - zipsFound)
    }
  }

  private def makeDiffaPair(key: String) = DiffaPair(
    domain = testDomain, key = key, versionPolicyName = "policy", upstream = u.name, downstream = d.name
  )

  private def makeDiffaPairWithLimits(key: String, eventsToLog: Int, maxExplainFiles: Int) = DiffaPair(
    domain = testDomain, key = key, versionPolicyName = "policy", upstream = u.name, downstream = d.name,
    eventsToLog = eventsToLog, maxExplainFiles = maxExplainFiles
  )

  def replayDomainConfig {
    replay(domainConfigStore)
  }
  
  def replaySystemConfig {
    replay(systemConfigStore)
  }
}
