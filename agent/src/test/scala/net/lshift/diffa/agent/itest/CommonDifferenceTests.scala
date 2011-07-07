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

import org.joda.time.DateTime
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
import java.util.{UUID, Properties}
import net.lshift.diffa.kernel.client.DifferencesClient
import net.lshift.diffa.kernel.differencing.{PairScanInfo, PairScanState, SessionScope, SessionEvent}

/**
 * Tests that can be applied to an environment to validate that differencing functionality works appropriately.
 */
trait CommonDifferenceTests {

  val log:Logger = LoggerFactory.getLogger(getClass)

  val mailDir = System.getProperty("diffa.maildir")
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

  def getReport(pair:String, from:DateTime, until:DateTime) : Array[SessionEvent]= {
    var sessionId = subscribeAndRunScan(SessionScope.forPairs(env.pairKey), yearAgo, today)
    env.diffClient.poll(sessionId)
  }

  @Test
  def shouldFindNoDifferencesInEmptyParticipants {
    val diffs = getReport(env.pairKey, yearAgo, today)
    assertNotNull(diffs)
    assertTrue(diffs.isEmpty)
  }

  @Test
  def detectionTimeShouldBeMatchTheMostRecentUpdatedTimeOnAParticipatingEntity = {
    val (diffs,_) = getVerifiedDiffsWithSessionId()
    assertNotNull(diffs(0))
    val detectionTime = diffs(0).detectedAt
    // TODO On a rainy day, look into why using a millisecond comparison is necessary
    assertEquals(yesterday.getMillis, detectionTime.getMillis)
  }

  @Test
  def shouldFindDifferencesInDifferingParticipants {
    val (diffs, sessionId) = getVerifiedDiffsWithSessionId()
    val seqId = diffs(0).seqId

    val detail = env.diffClient.eventDetail(sessionId, seqId, ParticipantType.UPSTREAM)
    assertNotNull(diffs)

    assertEquals("abcdef", detail)

    val fileList = messageDir.listFiles
    assertNotNull("File list was null for dir: " + messageDir, fileList)
    assertEquals(1, fileList.size)
    testForLink(fileList(0))
  }

  @Test
  def shouldFindDifferencesInParticipantsThatBecomeDifferent {
    var sessionId = subscribeAndRunScan(SessionScope.forPairs(env.pairKey), yearAgo, today)
    env.addAndNotifyUpstream("abc", "abcdef", someDate = yesterday, someString = "ss")

    val diffs = pollForAllDifferences(sessionId)

    assertFalse(diffs.isEmpty)
  }

  @Test
  def shouldPageDifferences = {
    val start = new DateTime
    val end = start.plusMinutes(2)

    val size = 10
    for (i <- 1 to size) {
      env.addAndNotifyUpstream("" + i, "" + i, someDate = yesterday, someString = "ss")
    }
    val sessionId = subscribeAndRunScan(SessionScope.forPairs(env.pairKey), yearAgo, today)

    val offset = 5

    val diffs1 = tryAgain((d:DifferencesClient) => d.page(sessionId, start, end, offset, size))
    val max = size - offset
    val length = diffs1.size
    assertTrue("Diffs was %s, but should have been maximally %s".format(length,max), max >= length)

    // Select the 7th and 8th differences and validate their content
    val subset = 2
    val diffs2 = tryAgain((d:DifferencesClient) => d.page(sessionId, start, end, 6, subset))

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
    var sessionId = subscribeAndRunScan(SessionScope.forPairs(env.pairKey), yearAgo, today)
    env.addAndNotifyUpstream("abc", up, someDate = yesterday, someString = "ss")

    val diffs = pollForAllDifferences(sessionId)
    val seqId1 = diffs(0).seqId

    val up1 = env.diffClient.eventDetail(sessionId, seqId1, ParticipantType.UPSTREAM)
    val down1 = env.diffClient.eventDetail(sessionId, seqId1, ParticipantType.DOWNSTREAM)

    assertEquals(up, up1)
    assertEquals(NO_CONTENT, down1)

    env.addAndNotifyDownstream("abc", down, someDate = yesterday, someString = "ss")
    Thread.sleep(2000)
    val diffs2 = pollForAllDifferences(sessionId)
    assertEquals(1, diffs2.length)
    val seqId2 = diffs2(0).seqId

    assertTrue("Invalid sequence ids: seqId1 = %s, seqId2 = %s".format(seqId1, seqId2), seqId2 > seqId1)

    val up2 = env.diffClient.eventDetail(sessionId, seqId2, ParticipantType.UPSTREAM)
    val down2 = env.diffClient.eventDetail(sessionId, seqId2, ParticipantType.DOWNSTREAM)
    assertEquals(up, up2)
    assertEquals(down, down2)
  }

  @Test
  def shouldNotFindDifferencesInParticipantsWithSameStateThatAgentWasntInformedOf {
    env.upstream.addEntity("abc", yesterday, "ss", yesterday, "abcdef")
    env.downstream.addEntity("abc", yesterday, "ss", yesterday, "abcdef")

    val diffs = getReport(env.pairKey, yearAgo, today)

    assertNotNull(diffs)
    assertTrue(diffs.isEmpty)
  }

  @Test
  def shouldReportMatchOnlyAsChangesAreReportedWithinMatchingWindow {

    env.addAndNotifyUpstream("abc", "abcdef", someDate = today, someString = "ss")
    env.addAndNotifyDownstream("abc", "abcdef", someDate = today, someString = "ss")

    val diffs = getReport(env.pairKey, yearAgo, today)

    assertNotNull(diffs)
    assertTrue(diffs.isEmpty)
        
  }

  @Test
  def scanShouldTriggerResend {
    env.withActionsServer {
      env.addAndNotifyUpstream("abc", "abcdef", someDate = today, someString = "ss")
      subscribeAndRunScan(SessionScope.forPairs(env.pairKey), yearAgo, today)
      assertEquals(1, env.entityResendTally("abc"))
    }
  }

  def subscribeAndRunScan(scope:SessionScope, from:DateTime, until:DateTime, n:Int = 30, wait:Int = 100) = {
    def isUpToDate(states:Map[String, PairScanInfo]) = states.values.forall(s => s.state == PairScanState.UP_TO_DATE)

    var sessionId = env.diffClient.subscribe(SessionScope.forPairs(env.pairKey), from, until)
    env.diffClient.runScan(sessionId)

    var i = n
    var scanStatus = env.diffClient.getScanStatus(sessionId)
    while(!isUpToDate(scanStatus) && i > 0) {
      Thread.sleep(wait)

      scanStatus = env.diffClient.getScanStatus(sessionId)
      i-=1
    }
    assertTrue("Unexpected scan state (session = %s): %s".format(sessionId, scanStatus), isUpToDate(scanStatus))

    sessionId
  }



  def getVerifiedDiffsWithSessionId() = {
    env.upstream.addEntity("abc", yesterday, "ss", yesterday, "abcdef")

    var sessionId = subscribeAndRunScan(SessionScope.forPairs(env.pairKey), yearAgo, today)
    val diffs = pollForAllDifferences(sessionId)

    assertNotNull(diffs)
    assertFalse(diffs.isEmpty)
    (diffs, sessionId)
  }

  def pollForAllDifferences(sessionId:String,n:Int = 20, wait:Int = 100) =
    tryAgain((d:DifferencesClient) => d.poll(sessionId),n,wait)

  def tryAgain(poll:DifferencesClient => Seq[SessionEvent], n:Int = 20, wait:Int = 100) : Seq[SessionEvent]= {
    var i = n
    var diffs = poll(env.diffClient)
    while(diffs.isEmpty && i > 0) {
      Thread.sleep(wait)

      diffs = poll(env.diffClient)
      i-=1
    }
    assertNotNull(diffs)
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