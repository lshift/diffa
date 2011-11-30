package net.lshift.diffa.kernel.diag

import org.junit.Assert._
import org.hamcrest.CoreMatchers._
import org.hamcrest.number.OrderingComparison._
import org.joda.time.DateTime
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.util.HamcrestDateTimeHelpers._
import net.lshift.diffa.kernel.differencing.{PairScanState}
import net.lshift.diffa.kernel.frontend.FrontendConversions
import org.junit.{Before, Test}
import net.lshift.diffa.kernel.config.{DiffaPairRef, Endpoint, DomainConfigStore, Domain, Pair => DiffaPair}
import java.io.{FileInputStream, File}
import org.apache.commons.io.{IOUtils, FileDeleteStrategy}
import java.util.zip.ZipInputStream

class LocalDiagnosticsManagerTest {
  val domainConfigStore = createStrictMock(classOf[DomainConfigStore])

  val explainRoot = new File("target/explain")
  val diagnostics = new LocalDiagnosticsManager(domainConfigStore, explainRoot.getPath)

  val domainName = "domain"
  val domain = Domain(name=domainName)

  val u = Endpoint(name = "1", scanUrl = "http://foo.com/scan", inboundUrl = "changes")
  val d = Endpoint(name = "2", scanUrl = "http://bar.com/scan", inboundUrl = "changes")

  val pair1 = DiffaPair(key = "pair1", domain = domain, versionPolicyName = "policy", upstream = u, downstream = d)
  val pair2 = DiffaPair(key = "pair2", domain = domain, versionPolicyName = "policy", upstream = u, downstream = d)

  @Before
  def cleanupExplanations() {
    FileDeleteStrategy.FORCE.delete(explainRoot)
  }

  @Test
  def shouldAcceptAndStoreLogEventForPair() {
    diagnostics.logPairEvent(DiagnosticLevel.INFO, pair1.asRef, "Some msg")

    val events = diagnostics.queryEvents(pair1.asRef, 100)
    assertEquals(1, events.length)
    assertThat(events(0).timestamp,
      is(allOf(after((new DateTime).minusSeconds(5)), before((new DateTime).plusSeconds(1)))))
    assertEquals(DiagnosticLevel.INFO, events(0).level)
    assertEquals("Some msg", events(0).msg)
  }

  @Test
  def shouldLimitNumberOfStoredLogEvents() {
    for (i <- 1 until 1000)
      diagnostics.logPairEvent(DiagnosticLevel.INFO, pair1.asRef, "Some msg")

    assertEquals(100, diagnostics.queryEvents(pair1.asRef, 1000).length)
  }

  @Test
  def shouldTrackStateOfPairsWithinDomain() {
    expect(domainConfigStore.listPairs(domainName)).
        andStubReturn(Seq(FrontendConversions.toPairDef(pair1), FrontendConversions.toPairDef(pair2)))
    replayAll()

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
    expect(domainConfigStore.listPairs(domainName)).
        andStubReturn(Seq(FrontendConversions.toPairDef(pair1), FrontendConversions.toPairDef(pair2)))
    replayAll()

    diagnostics.pairScanStateChanged(pair1.asRef, PairScanState.SCANNING)
    assertEquals(Map("pair1" -> PairScanState.SCANNING, "pair2" -> PairScanState.UNKNOWN), diagnostics.retrievePairScanStatesForDomain("domain"))

    // Remove the pair, and report it
    reset(domainConfigStore)
    expect(domainConfigStore.listPairs(domainName)).
        andStubReturn(Seq(FrontendConversions.toPairDef(pair2)))
    replayAll()
    diagnostics.onDeletePair(pair1.asRef)
    assertEquals(Map("pair2" -> PairScanState.UNKNOWN), diagnostics.retrievePairScanStatesForDomain(domainName))
  }
  
  @Test
  def shouldNotGenerateAnyOutputWhenCheckpointIsCalledOnASilentPair() {
    diagnostics.checkpointExplanations(DiffaPairRef("quiet", "domain"))

    val pairDir = new File(explainRoot, "domain/quiet")
    if (pairDir.exists())
      assertEquals(0, pairDir.listFiles().length)
  }

  @Test
  def shouldGenerateOutputWhenExplanationsHaveBeenLogged() {
    val pair = DiffaPairRef("explained", "domain")

    diagnostics.logPairExplanation(pair, "Test Case", "Diffa did something")
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "domain/explained")
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
    val pair = DiffaPairRef("explainedobj", "domain")

    diagnostics.writePairExplanationObject(pair, "Test Case", "upstream.123.json", os => {
      os.write("{a: 1}".getBytes("UTF-8"))
    })
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "domain/explainedobj")
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
    val pair = DiffaPairRef("explainedobj", "domain")

    diagnostics.writePairExplanationObject(pair, "Test Case", "upstream.123.json", os => {
      os.write("{a: 1}".getBytes("UTF-8"))
    })
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "domain/explainedobj")
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
    val pair = DiffaPairRef("explained", "domain")

    diagnostics.logPairExplanation(pair, "Test Case", "Diffa did something")
    diagnostics.checkpointExplanations(pair)

    diagnostics.logPairExplanation(pair, "Test Case" , "Diffa did something else")
    diagnostics.checkpointExplanations(pair)

    val pairDir = new File(explainRoot, "domain/explained")
    val zips = pairDir.listFiles()

    assertEquals(2, zips.length)
  }

  @Test
  def shouldKeepNumberOfExplanationFilesUnderControl() {
    val pair = DiffaPairRef("controlled", "domain")

    for (i <- 1 until 100) {
      diagnostics.logPairExplanation(pair, "Test Case", i.toString)
      diagnostics.checkpointExplanations(pair)
    }

    val pairDir = new File(explainRoot, "domain/controlled")
    val zips = pairDir.listFiles()

    assertEquals(20, zips.length)
    zips.foreach(z => {
      val zipInputStream = new ZipInputStream(new FileInputStream(z))
      zipInputStream.getNextEntry
      val content = IOUtils.toString(zipInputStream)
      zipInputStream.close()

      val entryNum = content.trim().split(" ").last
      assertThat(new Integer(entryNum), is(greaterThanOrEqualTo(new Integer(80))))
    })
  }

  def replayAll() { replay(domainConfigStore) }
}