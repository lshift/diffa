package net.lshift.diffa.kernel.diag

import org.junit.Assert._
import org.junit.Ignore
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
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.schema.servicelimits._
import system.SystemConfigStore
import net.lshift.diffa.kernel.frontend.DomainPairDef

class LocalDiagnosticsManagerTest {
  val domainConfigStore = createStrictMock(classOf[DomainConfigStore])
  val systemConfigStore = createStrictMock(classOf[SystemConfigStore])
  val serviceLimitsStore = createStrictMock(classOf[ServiceLimitsStore])

  val explainRoot = new File("target/explain")
  val diagnostics = new LocalDiagnosticsManager(systemConfigStore, domainConfigStore, serviceLimitsStore, explainRoot.getPath)

  val spaceId = System.currentTimeMillis()

  val u = Endpoint(name = "1", scanUrl = "http://foo.com/scan", inboundUrl = "changes")
  val d = Endpoint(name = "2", scanUrl = "http://bar.com/scan", inboundUrl = "changes")

  val pair1 = DomainPairDef(key = "pair1", space = spaceId, versionPolicyName = "policy", upstreamName = u.name, downstreamName = d.name)
  val pair2 = DomainPairDef(key = "pair2", space = spaceId, versionPolicyName = "policy", upstreamName = u.name, downstreamName = d.name)

  @Before
  def cleanupExplanations() {
    FileDeleteStrategy.FORCE.delete(explainRoot)
  }

  @Test
  def shouldAcceptAndStoreLogEventForPair() {
    val pairKey = "moderateLoggingPair"
    val pair = PairRef(pairKey, spaceId)

    expectEventBufferLimitQuery(spaceId, pairKey, 10)

    diagnostics.logPairEvent(None, pair, DiagnosticLevel.INFO, "Some msg")

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

    expectEventBufferLimitQuery(spaceId, pairKey, 100)

    val pair = PairRef(pairKey, spaceId)

    for (i <- 1 until 1000)
      diagnostics.logPairEvent(None, pair, DiagnosticLevel.INFO, "Some msg")

    assertEquals(100, diagnostics.queryEvents(pair, 1000).length)
  }

  @Test
  def shouldTrackStateOfPairsWithinDomain() {
    expectPairListFromConfigStore(pair1 :: pair2 :: Nil)

    // Query for the states associated. We should get back an entry for pair in "unknown"
    assertEquals(Map("pair1" -> PairScanState.UNKNOWN, "pair2" -> PairScanState.UNKNOWN),
      diagnostics.retrievePairScanStatesForDomain(spaceId))

    // Notify that the pair1 is now in Up To Date state
    diagnostics.pairScanStateChanged(pair1.asRef, PairScanState.UP_TO_DATE)
    assertEquals(Map("pair1" -> PairScanState.UP_TO_DATE, "pair2" -> PairScanState.UNKNOWN),
      diagnostics.retrievePairScanStatesForDomain(spaceId))

    // Notify that the pair2 is now in Failed state
    diagnostics.pairScanStateChanged(pair2.asRef, PairScanState.FAILED)
    assertEquals(Map("pair1" -> PairScanState.UP_TO_DATE, "pair2" -> PairScanState.FAILED),
      diagnostics.retrievePairScanStatesForDomain(spaceId))

    // Report a scan as being started. We should enter the scanning state again
    diagnostics.pairScanStateChanged(pair1.asRef, PairScanState.SCANNING)    // Simulate the supervisor indicating a scan start
    diagnostics.pairScanStateChanged(pair2.asRef, PairScanState.SCANNING)    // Simulate the supervisor indicating a scan start
    assertEquals(Map("pair1" -> PairScanState.SCANNING, "pair2" -> PairScanState.SCANNING),
      diagnostics.retrievePairScanStatesForDomain(spaceId))

  }

  @Test
  def shouldNotReportStateOfDeletedPairs() {
    // Wire up pair1 and pair2 to exist, and provide a status
    expectPairListFromConfigStore(pair1 :: pair2 :: Nil)

    diagnostics.pairScanStateChanged(pair1.asRef, PairScanState.SCANNING)
    assertEquals(Map("pair1" -> PairScanState.SCANNING, "pair2" -> PairScanState.UNKNOWN), diagnostics.retrievePairScanStatesForDomain(spaceId))

    // Remove the pair, and report it
    reset(domainConfigStore)
    expect(domainConfigStore.listPairs(spaceId)).
        andStubReturn(Seq(pair2))
    replayDomainConfig
    diagnostics.onDeletePair(pair1.asRef)
    assertEquals(Map("pair2" -> PairScanState.UNKNOWN), diagnostics.retrievePairScanStatesForDomain(spaceId))
  }
  
  @Test
  def shouldNotGenerateAnyOutputWhenCheckpointIsCalledOnASilentPair() {
    val key = "quiet"
    diagnostics.checkpointExplanations(None, PairRef(key, spaceId))

    val pairDir = new File(explainRoot, "%s/%s".format(spaceId, key))
    if (pairDir.exists())
      assertEquals(0, pairDir.listFiles().length)
  }

  @Test
  def shouldGenerateOutputWhenExplanationsHaveBeenLogged() {
    val pairKey = "explained"
    val pair = PairRef(pairKey, spaceId)

    expectMaxExplainFilesLimitQuery(spaceId, pairKey, 1)

    diagnostics.logPairExplanation(None, pair, "Test Case", "Diffa did something")
    diagnostics.checkpointExplanations(None, pair)

    val pairDir = new File(explainRoot, "%s/%s".format(spaceId, pairKey))
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
    val pair = PairRef(pairKey, spaceId)

    expectMaxExplainFilesLimitQuery(spaceId, pairKey, 1)

    diagnostics.writePairExplanationObject(None, pair, "Test Case", "upstream.123.json", os => {
      os.write("{a: 1}".getBytes("UTF-8"))
    })
    diagnostics.checkpointExplanations(None, pair)

    val pairDir = new File(explainRoot, "%s/%s".format(spaceId, pairKey))
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
    val pair = PairRef(pairKey, spaceId)

    expectMaxExplainFilesLimitQuery(spaceId, pairKey, 1)

    diagnostics.writePairExplanationObject(None, pair, "Test Case", "upstream.123.json", os => {
      os.write("{a: 1}".getBytes("UTF-8"))
    })
    diagnostics.checkpointExplanations(None, pair)

    val pairDir = new File(explainRoot, "%s/%s".format(spaceId, pairKey))
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

    expectMaxExplainFilesLimitQuery(spaceId, pairKey, 2)

    val pair = PairRef(pairKey, spaceId)

    diagnostics.logPairExplanation(None, pair, "Test Case", "Diffa did something")
    diagnostics.checkpointExplanations(None, pair)

    diagnostics.logPairExplanation(None, pair, "Test Case" , "Diffa did something else")
    diagnostics.checkpointExplanations(None, pair)

    val pairDir = new File(explainRoot, "%s/%s".format(spaceId, pairKey))
    val zips = pairDir.listFiles()

    assertEquals(2, zips.length)
  }

  @Test
  def shouldKeepNumberOfExplanationFilesUnderControl() {
    val filesToKeep = 20
    val generateCount = 100
    val pairKey = "controlled_100_20"
    val pair = PairRef(pairKey, spaceId)

    expectMaxExplainFilesLimitQuery(spaceId, pairKey, filesToKeep)

    for (i <- 1 until generateCount) {
      diagnostics.logPairExplanation(None, pair, "Test Case", i.toString)
      diagnostics.checkpointExplanations(None, pair)
    }

    val pairDir = new File(explainRoot, "%s/%s".format(spaceId, pairKey))
    val zips = pairDir.listFiles()

    assertEquals(filesToKeep, zips.length)

    // Occasionally, because of imprecision of timing with regards to creating
    // and naming explanation files, we sometimes find that we're off by a few
    // files. However, it's likely rare that we will trigger this in practice,
    // and given it's non-critical data, it's okay if we're slightly off.
    val fuzzFactor = 5;

    val earliestEntryNum: Int = generateCount - filesToKeep
    val contents = zips.sortBy(_.getAbsolutePath).map(verifyZipContent(_));
    contents.foreach(entryNum =>
      assertThat(entryNum, is(greaterThanOrEqualTo(new Integer(earliestEntryNum-fuzzFactor)))))
  }
  
  private def verifyZipContent(zipFile: File) = {
    val zipInputStream = new ZipInputStream(new FileInputStream(zipFile))
    zipInputStream.getNextEntry
    val content = IOUtils.toString(zipInputStream)
    zipInputStream.close()
    
    val entryNum = content.trim().split(" ").last
    Integer.valueOf(entryNum)
  }

  private def expectEventBufferLimitQuery(space:Long, pairKey:String, eventBufferSize:Int) = {

    expect(serviceLimitsStore.
      getEffectiveLimitByNameForPair(space, pairKey, DiagnosticEventBufferSize)).
      andReturn(eventBufferSize).atLeastOnce()

    replay(serviceLimitsStore)
  }

  private def expectMaxExplainFilesLimitQuery(space:Long, pairKey:String, eventBufferSize:Int) = {

    expect(serviceLimitsStore.
      getEffectiveLimitByNameForPair(space, pairKey, ExplainFiles)).
      andReturn(eventBufferSize).atLeastOnce()

    replay(serviceLimitsStore)
  }

  private def expectPairListFromConfigStore(pairs: Seq[DomainPairDef]) {
    expect(domainConfigStore.listPairs(spaceId)).
      andStubReturn(pairs)

    pairs foreach { pairDef =>
      expect(domainConfigStore.getPairDef(pairDef.asRef)).
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
