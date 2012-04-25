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

package net.lshift.diffa.agent.itest

import org.junit.Assert._
import net.lshift.diffa.agent.itest.support.TestConstants._
import java.lang.String
import support.TestEnvironment
import javax.mail.Session
import java.io.{File, FileInputStream}
import javax.mail.internet.MimeMessage
import util.matching.Regex
import org.slf4j.{Logger, LoggerFactory}
import java.net.URI
import org.junit.{Before, Test}
import net.lshift.diffa.kernel.participants.ParticipantType
import net.lshift.diffa.agent.client.DifferencesRestClient
import scala.collection.JavaConversions._
import java.util.{UUID, Properties}
import org.joda.time.{DateTimeZone, DateTime}
import net.lshift.diffa.kernel.differencing.ZoomLevels._
import net.lshift.diffa.agent.rest.AggregateRequest
import net.lshift.diffa.kernel.differencing.{InvalidAggregateRequestException, ZoomLevels, PairScanState, DifferenceEvent}

/**
 * Tests that can be applied to an environment to validate that differencing functionality works appropriately.
 */
trait CommonDifferenceTests {

  val log:Logger = LoggerFactory.getLogger(getClass)

  val mailDir = System.getProperty("diffa.maildir", System.getProperty("basedir", ".") + "/target/messages")
  log.debug("Using maildir: " + mailDir)
  
  val messageDir = (new File(mailDir)).getAbsoluteFile

  /**
   * The environment under test.
   */
  def env:TestEnvironment

  @Before
  def deleteMails() = {
    if (messageDir.exists) {
      messageDir.listFiles.foreach(f => f.delete)
    }
  }

  def getReport(from:DateTime, until:DateTime) : Array[DifferenceEvent]= {
    runScanAndWaitForCompletion(yearAgo, today)
    env.diffClient.getEvents(env.pairKey, from, until, 0, 100)
  }

  @Test
  def shouldFindNoDifferencesInEmptyParticipants {
    val diffs = getReport(yearAgo, today)
    assertNotNull(diffs)
    assertTrue(diffs.isEmpty)
  }

  @Test
  def detectionTimeShouldBeMatchTheMostRecentUpdatedTimeOnAParticipatingEntity = {
    val diffs = getVerifiedDiffs()
    assertNotNull(diffs(0))
    val detectionTime = diffs(0).detectedAt
    // TODO On a rainy day, look into why using a millisecond comparison is necessary
    assertEquals(yesterday.getMillis, detectionTime.getMillis)
  }

  @Test
  def shouldFindDifferencesInDifferingParticipants {
    val diffs = getVerifiedDiffs()
    val seqId = diffs(0).seqId

    val detail = env.diffClient.eventDetail(seqId, ParticipantType.UPSTREAM)
    assertNotNull(diffs)

    assertEquals("abcdef", detail)

    val fileList = messageDir.listFiles
    assertNotNull("File list was null for dir: " + messageDir, fileList)
    // #338 This notification test is broken since the default quiet time was set to a non-zero value,
    // but unfortuneately, this requires the config to be dynamically updateable.
    //assertEquals(1, fileList.size)
    //testForLink(fileList(0))
  }

  @Test
  def differencesShouldTile = {
    val diffs = getVerifiedDiffs()
    assertNotNull(diffs)

    val zoomLevel = ZoomLevels.QUARTER_HOURLY
    val tiles = env.diffClient.getZoomedTiles(yesterday, tomorrow, zoomLevel)
    assertNotNull(tiles)
    assertNotNull(tiles(env.pairKey))
  }

  @Test
  def differencesShouldTileAtEachLevel = {

    val rightNow = new DateTime(2011,7,8,15,0,0,0, DateTimeZone.UTC)
    val upperBound = rightNow.plusMinutes(1)

    val events = 480 // 8 hours * 60 minutes, i.e. will get split at 8-hourly level, but fits into a daily column
    val columns = 32

    val expectedZoomedViews = Map(
      DAILY          -> List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 480), // 24 hour interval
      EIGHT_HOURLY   -> List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 60, 420), // 0-8h, 8-16h intervals
      FOUR_HOURLY    -> List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 60, 240, 180),  // 8-12h,12-16h
      TWO_HOURLY     -> List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 60, 120, 120, 120, 60),  // 8-10h,10-12h,12-14h,14-16h
      HOURLY         -> List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 60, 60, 60, 60, 60, 60, 60, 60, 0),  // every hour, remembering the extra minute
      HALF_HOURLY    -> List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 0),  // every half hour
      QUARTER_HOURLY -> List(15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 0)  // every quarter hour
    )

    resetPair
    env.clearParticipants

    for (i <- 1 to events) {
      val timestamp = rightNow.minusMinutes(i)
      env.upstream.addEntity("id-" + i, yesterday, "ss", timestamp, "abcdef")
    }

    val fullLowerBound = rightNow.minusMinutes(ZoomLevels.lookupZoomLevel(DAILY) * columns)
    runScanAndWaitForCompletion(fullLowerBound, upperBound, 100, 100)

    expectedZoomedViews.foreach{ case (zoomLevel, expectedBlobs) => {
      val lowerBound = upperBound.minusMinutes(ZoomLevels.lookupZoomLevel(zoomLevel) * columns)
      val tiles = env.diffClient.getZoomedTiles(lowerBound, upperBound, zoomLevel)
      val retrievedBlobs = tiles(env.pairKey)
      assertEquals("Zoom level %s ".format(zoomLevel), expectedBlobs,retrievedBlobs)
    }}
  }

  @Test
  def differencesShouldTileAtEachLevelViaAggregationAPI = {

    val rightNow = new DateTime(2011,7,8,15,0,0,0, DateTimeZone.UTC)
    val upperBound = rightNow.plusMinutes(1)

    val events = 720 // 12 hours * 60 minutes, i.e. will get split at 8-hourly level, but fits into a daily column
    val columns = 32

    val expectedZoomedViews = Map(
      DAILY          -> List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 720), // 24 hour interval
      EIGHT_HOURLY   -> List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 240, 480), // 0-8h, 8-16h intervals
      FOUR_HOURLY    -> List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 240, 240, 240),  // 8-12h,12-16h
      TWO_HOURLY     -> List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 120, 120, 120, 120, 120, 120),  // 8-10h,10-12h,12-14h,14-16h
      HOURLY         -> List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 60, 60, 60, 60, 60, 60, 60, 60, 60, 60, 60, 60),  // every hour, remembering the extra minute
      HALF_HOURLY    -> List(0, 0, 0, 0, 0, 0, 0, 0, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),  // every half hour
      QUARTER_HOURLY -> List(15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15)  // every quarter hour
    )
    // Sanity check the test data
    expectedZoomedViews.foreach { case (k, v) => assertEquals("Zoom Level " + k, columns, v.length) }

    resetPair
    env.clearParticipants

    for (i <- 0 until events) {
      val timestamp = rightNow.minusMinutes(i)
      env.upstream.addEntity("id-" + i, yesterday, "ss", timestamp, "abcdef")
    }

    val fullLowerBound = rightNow.minusMinutes(ZoomLevels.lookupZoomLevel(DAILY) * columns)
    runScanAndWaitForCompletion(fullLowerBound, upperBound, 100, 100)

    val requests = expectedZoomedViews.map { case (zoomLevel, expectedBlobs) =>
      val lowerBound = upperBound.minusMinutes(ZoomLevels.lookupZoomLevel(zoomLevel) * columns)
      (zoomLevel.toString) -> AggregateRequest(lowerBound, upperBound, Some(ZoomLevels.lookupZoomLevel(zoomLevel)))
    }.toMap
    val aggregates = env.diffClient.retrieveAggregates(env.pairKey, requests)

    expectedZoomedViews.foreach{ case (zoomLevel, expectedBlobs) => {
      val retrieveAggregates = aggregates(zoomLevel.toString)

      assertEquals("Zoom level %s ".format(zoomLevel), expectedBlobs, retrieveAggregates)
    }}
  }

  @Test
  def differencesShouldSupportAggregateRequestsWithoutBucketing = {

    val rightNow = new DateTime(2011,7,8,15,0,0,0, DateTimeZone.UTC)
    val upperBound = rightNow.plusMinutes(1)

    val events = 720 // 12 hours * 60 minutes, i.e. will get split at 8-hourly level, but fits into a daily column
    val columns = 32

    resetPair
    env.clearParticipants

    for (i <- 0 until events) {
      val timestamp = rightNow.minusMinutes(i)
      env.upstream.addEntity("id-" + i, yesterday, "ss", timestamp, "abcdef")
    }

    val fullLowerBound = rightNow.minusMinutes(ZoomLevels.lookupZoomLevel(DAILY) * columns)
    runScanAndWaitForCompletion(fullLowerBound, upperBound, 100, 100)

    val requests = Map(
      "open-top" -> AggregateRequest(upperBound.minusMinutes(50), null, None),
      "open-bottom" -> AggregateRequest(null, upperBound.minusMinutes(50), None),
      "all" -> AggregateRequest(fullLowerBound, upperBound, None),
      "open-all" -> AggregateRequest(null.asInstanceOf[String], null.asInstanceOf[String], None)
    )
    val aggregates = env.diffClient.retrieveAggregates(env.pairKey, requests)

    assertEquals(List(50), aggregates("open-top"))
    assertEquals(List(events - 50), aggregates("open-bottom"))
    assertEquals(List(events), aggregates("all"))
    assertEquals(List(events), aggregates("open-all"))
    
    try {
      env.diffClient.retrieveAggregates(env.pairKey, Map("bad" -> AggregateRequest(null.asInstanceOf[String], null.asInstanceOf[String], Some(5))))
      fail("Should have thrown InvalidAggregateRequestException")
    } catch {
      case x:InvalidAggregateRequestException =>
        assertEquals("Both a start and end time must be defined when requesting bucketing", x.getMessage)
    }
  }

  private def resetPair = {
    try {
      env.deletePair
    }
    finally {
      env.createPair
    }
  }

  @Test
  def shouldBeAbleToIgnoreDifference() {
    val diffs = getVerifiedDiffs()
    assertFalse(diffs.isEmpty)

    env.diffClient.ignore(diffs(0).seqId)

    val events = env.diffClient.getEvents(env.pairKey, yearAgo, today, 0, 100)
    assertTrue(events.isEmpty)
  }

  @Test
  def shouldBeAbleToUnignoreDifference() {
    val diffs = getVerifiedDiffs()
    assertFalse(diffs.isEmpty)

    val ignored = env.diffClient.ignore(diffs(0).seqId)
    env.diffClient.unignore(ignored.seqId)

    val events = env.diffClient.getEvents(env.pairKey, yearAgo, today, 0, 100)
    assertEquals(1, events.length)
  }

  @Test
  def shouldFindDifferencesInParticipantsThatBecomeDifferent {
    runScanAndWaitForCompletion(yearAgo, today)
    env.addAndNotifyUpstream("abc", "abcdef", someDate = yesterday, someString = "ss")

    val diffs = env.differencesHelper.pollForAllDifferences(yearAgo, nextYear)

    assertFalse("Expected to find differences in range: %s -> %s".format(yearAgo, nextYear),diffs.isEmpty)
  }

  /**
   * Create a setup where there are two differences, but then run a scan with a view that will only see one of them.
   * The list of differences should only include the difference within the viewed range.
   */
  @Test
  def shouldNotScanOutsideViewBounds {
    runScanAndWaitForCompletion(yearAgo, nextYear)
    env.upstream.addEntity("abc", datetime = yesterday, someString = "ss", body = "abcdef", lastUpdated = new DateTime)
    env.upstream.addEntity("def", datetime = yesterday, someString = "tt", body = "abcdef", lastUpdated = new DateTime)
    runScanAndWaitForCompletion(yearAgo, nextYear, view = Some("tt-only"))

    val events = env.differencesHelper.pollForAllDifferences(yearAgo, nextYear)
    assertEquals(1, events.length)
    assertEquals("def", events(0).objId.id)
  }

  @Test
  def shouldIgnoreRealtimeChangesThatDontConformToConstraints {

    // someString is configured to be a string prefix category that only accepts 'ss' or 'tt'

    runScanAndWaitForCompletion(yearAgo, today)
    env.addAndNotifyUpstream("abc", "abcdef", someDate = yesterday, someString = "abcdef")

    val diffs = env.differencesHelper.pollForAllDifferences(yearAgo, nextYear)

    assertTrue("Expected not to find differences for realtime event", diffs.isEmpty)
  }

  @Test
  def shouldIgnoreScanChangesThatDontConformToConstraints {

    // someString is configured to be a string prefix category that only accepts 'ss'

    env.upstream.addEntity("abc", datetime = today, someString = "abcdef", lastUpdated = new DateTime, body = "abcdef")
    val diffs = getReport(yearAgo, nextYear)

    assertTrue("Expected not to find differences in scan", diffs.isEmpty)
  }

  @Test
  def shouldPageDifferences = {
    val start = new DateTime
    val end = start.plusMinutes(2)

    val size = 10
    for (i <- 1 to size) {
      env.addAndNotifyUpstream("" + i, "" + i, someDate = yesterday, someString = "ss")
    }
    runScanAndWaitForCompletion(yearAgo, today)

    val offset = 5

    val diffs1 = env.differencesHelper.tryAgain((d:DifferencesRestClient) => d.getEvents(env.pairKey, start, end, offset, size))
    val max = size - offset
    val length = diffs1.size
    assertTrue("Diffs was %s, but should have been maximally %s".format(length,max), max >= length)

    // Select the 7th and 8th differences and validate their content
    val subset = 2
    val diffs2 = env.differencesHelper.tryAgain((d:DifferencesRestClient) => d.getEvents(env.pairKey, start, end, 6, subset))

    assertTrue("Diffs was %s, but should have been maximally %s".format(diffs2.length,subset), subset >= diffs2.length)
    // TODO [#224] Put back in
    //assertEquals(subset, diffs2.size)

    // The events aren't guaranteed to come back in any particular order
    //val bySeqId = diffs2.sortBy(evt => evt.seqId)

    //assertEquals("Unexpected sequence %s; expected to see sequence 7, all were: %s".format(bySeqId(0), bySeqId), "7", bySeqId(0).seqId)
    //assertEquals("Unexpected sequence %s; expected to see sequence 8".format(bySeqId(1)), "8", bySeqId(1).seqId)
  }

  @Test
  def walkThroughEventDetails = {
    def guid() = UUID.randomUUID.toString
    val up = guid()
    val down = guid()
    val NO_CONTENT = "Expanded detail not available"

    runScanAndWaitForCompletion(yearAgo, nextYear)
    env.addAndNotifyUpstream("abc", up, someDate = yesterday, someString = "ss")

    val diffs = env.differencesHelper.pollForAllDifferences(yearAgo, nextYear)
    val seqId1 = diffs(0).seqId

    val up1 = env.diffClient.eventDetail(seqId1, ParticipantType.UPSTREAM)
    val down1 = env.diffClient.eventDetail(seqId1, ParticipantType.DOWNSTREAM)

    assertEquals(up, up1)
    assertEquals(NO_CONTENT, down1)

    env.addAndNotifyDownstream("abc", down, someDate = yesterday, someString = "ss")
    Thread.sleep(2000)
    val diffs2 = env.differencesHelper.pollForAllDifferences(yearAgo, nextYear)
    assertEquals(1, diffs2.length)
    val seqId2 = diffs2(0).seqId

    assertTrue("Invalid sequence ids: seqId1 = %s, seqId2 = %s".format(seqId1, seqId2), seqId2 > seqId1)

    val up2 = env.diffClient.eventDetail(seqId2, ParticipantType.UPSTREAM)
    val down2 = env.diffClient.eventDetail(seqId2, ParticipantType.DOWNSTREAM)
    assertEquals(up, up2)
    assertEquals(down, down2)
  }

  @Test
  def shouldNotFindDifferencesInParticipantsWithSameStateThatAgentWasntInformedOf {
    env.upstream.addEntity("abc", yesterday, "ss", yesterday, "abcdef")
    env.downstream.addEntity("abc", yesterday, "ss", yesterday, "abcdef")

    val diffs = getReport(yearAgo, today)

    assertNotNull(diffs)
    assertTrue(diffs.isEmpty)
  }

  @Test
  def shouldReportMatchOnlyAsChangesAreReportedWithinMatchingWindow {

    env.addAndNotifyUpstream("abc", "abcdef", someDate = today, someString = "ss")
    env.addAndNotifyDownstream("abc", "abcdef", someDate = today, someString = "ss")

    val diffs = getReport(yearAgo, today)

    assertNotNull(diffs)
    assertTrue(diffs.isEmpty)
        
  }

  @Test
  def scanShouldTriggerResend {
    env.withActionsServer {
      env.upstream.addEntity("abc", datetime = today, someString = "ss", lastUpdated = new DateTime, body = "abcdef")
      runScanAndWaitForCompletion(yearAgo, today)

      waitForResendTally("abc", 1)
    }
  }

  @Test
  def scanShouldBeCancellable {
    env.withActionsServer {
      env.upstream.addEntity("abc", datetime = today, someString = "ss", lastUpdated = new DateTime, body = "abcdef")

      // Make the upstream wait 5s before responding so the scan doesn't complete too quickly
      env.upstream.queryResponseDelay = 5000

      // Get into a scanning state
      env.scanningClient.startScan(env.pairKey)
      waitForScanStatus(env.pairKey, PairScanState.SCANNING)

      // Cancel the scan
      env.scanningClient.cancelScanning(env.pairKey)
      waitForScanStatus(env.pairKey, PairScanState.CANCELLED)
    }
  }

  def runScanAndWaitForCompletion(from:DateTime, until:DateTime, n:Int = 30, wait:Int = 100, view:Option[String] = None) {
    env.scanningClient.startScan(env.pairKey, view = view)

    waitForScanStatus(env.pairKey, PairScanState.UP_TO_DATE, n, wait)
  }

  def waitForScanStatus(pairKey:String, state:PairScanState, n:Int = 30, wait:Int = 100) {
    def hasReached(states:Map[String, PairScanState]) = states.getOrElse(pairKey, PairScanState.UNKNOWN) == state

    var i = n
    var scanStatus = env.scanningClient.getScanStatus
    while(!hasReached(scanStatus) && i > 0) {
      Thread.sleep(wait)

      scanStatus = env.scanningClient.getScanStatus
      i-=1
    }
    assertTrue("Unexpected scan state (pair = %s): %s (wanted %s)".format(pairKey, scanStatus, state), hasReached(scanStatus))
  }

  def waitForResendTally(entity:String, count:Int) {
    val timeout = 5000L
    val deadline = System.currentTimeMillis() + timeout

    env.entityResendTally.synchronized {
      while (true) {
        val currentCount = env.entityResendTally.getOrElse(entity, 0)

        if (currentCount == count)
          return

        if (System.currentTimeMillis > deadline) {
          fail("Entity resend tally %s for %s never reached (got to %s)".format(count, entity, currentCount))
        }

        env.entityResendTally.wait(timeout)
      }
    }
  }


  def getVerifiedDiffs() = {
    env.upstream.addEntity("abc", yesterday, "ss", yesterday, "abcdef")

    runScanAndWaitForCompletion(yearAgo, today)
    val diffs = env.differencesHelper.pollForAllDifferences(yearAgo, today)

    assertNotNull(diffs)
    assertFalse(diffs.isEmpty)
    diffs
  }

  def testForLink(f:File) = {
      val s = Session.getInstance(new Properties())
      val msg = new MimeMessage(s,new FileInputStream(f))
      val content = msg.getContent.asInstanceOf[String]

      log.debug("Inspecting content from file: " + f.getAbsolutePath)
      log.debug(content)

      val pattern = new Regex(""".*<a href=\"(.*)\".*""", "link");
      pattern.findFirstMatchIn(content) match {
        case None         => fail("Error extracting link from file: " + f.getAbsolutePath)
        case Some(result) => {
          // Attempt to parse the URL, if it is bogus, let the exception fly
          val uri = new URI(result.group("link"))
        }
      }
    }

}