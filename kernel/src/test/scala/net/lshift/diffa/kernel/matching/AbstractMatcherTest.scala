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

package net.lshift.diffa.kernel.matching

import org.junit.{Test, After, Before}
import net.lshift.diffa.kernel.events.{DownstreamPairChangeEvent, UpstreamPairChangeEvent, VersionID}
import org.joda.time.DateTime
import org.junit.Assert._
import java.util.concurrent.atomic.{AtomicInteger}
import net.lshift.diffa.kernel.config.PairRef
import java.util.concurrent.{TimeUnit, LinkedBlockingQueue}

/**
 * Standard tests for a matcher.
 */
abstract class AbstractMatcherTest {
  var analyser:EventMatcher = null
  var slowAnalyser:EventMatcher = null
  val ackCallbackAListener = new LinkedBlockingQueue[Object]
  val ackCallbackBListener = new LinkedBlockingQueue[Object]

  val pairId = "pair" + AbstractMatcherTest.nextPairId
  val spaceId = System.currentTimeMillis()

  val ackCallbackA:Function0[Unit] = () => ackCallbackAListener.add(new Object)
  val ackCallbackB:Function0[Unit] = () => ackCallbackBListener.add(new Object)

  val id1 = VersionID(PairRef(pairId,spaceId), "aaaa1")
  val id2 = VersionID(PairRef(pairId,spaceId), "aaaa2")
  val id3 = VersionID(PairRef(pairId,spaceId), "aaaa3")
  val id4 = VersionID(PairRef(pairId,spaceId), "aaaa4")
  val id5 = VersionID(PairRef(pairId,spaceId), "aaaa5")
  val id6 = VersionID(PairRef(pairId,spaceId), "aaaa6")
  val idB6 = VersionID(PairRef(pairId,spaceId), "bbbb6")
  val id7 = VersionID(PairRef(pairId,spaceId), "aaaa7")

  def createMatcher(id:String, timeout:Int):EventMatcher

  @Before
  def createAnalyser() {
    analyser = createMatcher(pairId, 1)
    slowAnalyser = createMatcher(pairId + "-Slow", 3)
  }

  @After
  def disposeAnalyser() {
    analyser.dispose
    slowAnalyser.dispose
  }

  @Test
  def addingBalancedPairShouldResultInPairedMessageAndAcksBeingTriggered() {
    val mb = new LinkedBlockingQueue[Object]
    analyser.addListener(new MatchingStatusListener {
      def onUpstreamExpired(id:VersionID, vsn:String) {}
      def onPaired(id: VersionID, vsn:String) {
        mb.add(new Object)
      }
      def onDownstreamExpired(id: VersionID, vsn:String) {}
    })

    analyser.onChange(new UpstreamPairChangeEvent(id1, Map(), new DateTime, "vsnAAAA"), ackCallbackA)
    analyser.onChange(new DownstreamPairChangeEvent(id1, Map(), new DateTime, "vsnAAAA"), ackCallbackB)

    mb.poll(1, TimeUnit.SECONDS) match {
      case null => fail("Failed to recieve a matched pair event")
      case _ =>
    }
    ackCallbackAListener.poll(100, TimeUnit.MILLISECONDS) match {
      case null => fail("Ack Callback for upstream not triggered")
      case _       =>
    }
    ackCallbackBListener.poll(100, TimeUnit.MILLISECONDS) match {
      case null => fail("Ack Callback for downstream not triggered")
      case _       =>
    }
  }

  @Test
  def addingSeriesOfBalancedPairsShouldResultInPairedMessages() {
    val mb = new LinkedBlockingQueue[VersionID]
    analyser.addListener(new MatchingStatusListener {
      def onUpstreamExpired(id: VersionID, vsn:String) {}
      def onPaired(id: VersionID, vsn:String) {
        mb.add(id)
      }
      def onDownstreamExpired(id: VersionID, vsn:String) {}
    })

    analyser.onChange(new UpstreamPairChangeEvent(id1, Map(), new DateTime, "vsnAAAA"), ackCallbackA)
    analyser.onChange(new DownstreamPairChangeEvent(id1, Map(), new DateTime, "vsnAAAA"), ackCallbackB)
    analyser.onChange(new UpstreamPairChangeEvent(id2, Map(), new DateTime, "vsnAAAB"), ackCallbackA)
    analyser.onChange(new DownstreamPairChangeEvent(id2, Map(), new DateTime, "vsnAAAB"), ackCallbackB)

    mb.poll(1, TimeUnit.SECONDS) match {
      case null => fail("Failed to recieve first matched pair event")
      case id => assertEquals(id1, id)
    }
    mb.poll(10, TimeUnit.MILLISECONDS) match {
      case null => fail("Failed to recieve second matched pair event")
      case id => assertEquals(id2, id)
    }
  }

  @Test
  def addingUnbalancedUpstreamShouldResultInUpstreamExpiredMessageAndUpstreamAckCalled() {
    val mb = new LinkedBlockingQueue[VersionID]
    analyser.addListener(new MatchingStatusListener {
      def onUpstreamExpired(id: VersionID, vsn:String) {
        mb.add(id)
      }
      def onPaired(id: VersionID, vsn:String) {}
      def onDownstreamExpired(id: VersionID, vsn:String) {}
    })

    analyser.onChange(new UpstreamPairChangeEvent(id2, Map(), new DateTime, "vsnAAAA"), ackCallbackA)

    mb.poll(10, TimeUnit.SECONDS) match {
      case null         => fail("Failed to recieve a expired upstream event")
      case id:VersionID => assertEquals(id2, id)
    }
    ackCallbackAListener.poll(100, TimeUnit.MILLISECONDS) match {
      case null    => fail("Ack Callback for upstream not triggered")
      case _       =>
    }
  }

  @Test
  def addingSeriesOfUnbalancedUpstreamsShouldResultInUpstreamExpiredMessages() {
    val mb = new LinkedBlockingQueue[VersionID]
    analyser.addListener(new MatchingStatusListener {
      def onUpstreamExpired(id: VersionID, vsn:String) {
        mb.add(id)
      }
      def onPaired(id: VersionID, vsn:String) {}
      def onDownstreamExpired(id: VersionID, vsn:String) {}
    })

    analyser.onChange(new UpstreamPairChangeEvent(id2, Map(), new DateTime, "vsnAAAA"), ackCallbackA)
    analyser.onChange(new UpstreamPairChangeEvent(id3, Map(), new DateTime, "vsnAAAB"), ackCallbackA)
    analyser.onChange(new UpstreamPairChangeEvent(id4, Map(), new DateTime, "vsnAAAC"), ackCallbackA)

    mb.poll(10, TimeUnit.SECONDS) match {
      case null => fail("Failed to recieve first expired upstream event")
      case id   => assertEquals(id2, id)
    }
    mb.poll(1, TimeUnit.SECONDS) match {
      case null => fail("Failed to recieve second expired upstream event")
      case id   => assertEquals(id3, id)
    }
    mb.poll(1, TimeUnit.SECONDS) match {
      case null => fail("Failed to recieve third expired upstream event")
      case id   => assertEquals(id4, id)
    }
  }

  @Test
  def addingUnbalancedDownstreamShouldResultInDownstreamExpiredMessageAndDownstreamAckCalled() {
    val mb = new LinkedBlockingQueue[VersionID]
    analyser.addListener(new MatchingStatusListener {
      def onUpstreamExpired(id: VersionID, vsn:String) {}
      def onPaired(id: VersionID, vsn:String) {}
      def onDownstreamExpired(id: VersionID, vsn:String) {
        mb.add(id)
      }
    })

    analyser.onChange(new DownstreamPairChangeEvent(id3, Map(), new DateTime, "vsnAAAA"), ackCallbackB)

    mb.poll(2, TimeUnit.SECONDS) match {
      case null         => fail("Failed to recieve a expired downstream event")
      case id:VersionID => assertEquals(id3, id)
    }
    ackCallbackBListener.poll(100, TimeUnit.MILLISECONDS) match {
      case null    => fail("Ack Callback for downstream not triggered")
      case _       =>
    }
  }

  @Test
  def addingBalancedPairShouldNotResultInUpstreamExpiredMessage() {
    val mb = new LinkedBlockingQueue[Object]
    slowAnalyser.addListener(new MatchingStatusListener {
      def onUpstreamExpired(id: VersionID, vsn:String) {
        mb.add(new Object)
      }
      def onPaired(id: VersionID, vsn:String) {}
      def onDownstreamExpired(id: VersionID, vsn:String) {}
    })

    slowAnalyser.onChange(new DownstreamPairChangeEvent(id4, Map(), new DateTime, "vsnAAAA"), ackCallbackB)
    Thread.sleep(1000)
    slowAnalyser.onChange(new UpstreamPairChangeEvent(id4, Map(), new DateTime, "vsnAAAA"), ackCallbackA)

    mb.poll(5, TimeUnit.SECONDS) match {
      case null =>
      case _    => fail("Should not have received an expired upstream event")
    }
  }

  @Test
  def addingBalancedPairShouldNotResultInDownstreamExpiredMessage() {
    val mb = new LinkedBlockingQueue[Object]
    slowAnalyser.addListener(new MatchingStatusListener {
      def onUpstreamExpired(id: VersionID, vsn:String) {}
      def onPaired(id: VersionID, vsn:String) {}
      def onDownstreamExpired(id: VersionID, vsn:String) {
        mb.add(new Object)
      }
    })

    slowAnalyser.onChange(new UpstreamPairChangeEvent(id5, Map(), new DateTime, "vsnAAAA"), ackCallbackA)
    Thread.sleep(1000)
    slowAnalyser.onChange(new DownstreamPairChangeEvent(id5, Map(), new DateTime, "vsnAAAA"), ackCallbackB)

    mb.poll(5, TimeUnit.SECONDS) match {
      case null =>
      case _ => fail("Should not have received an expired downstream event")
    }
  }

  @Test
  def addingPairWithDifferentIdsShouldNotResultInPairedMessage() {
    val mb = new LinkedBlockingQueue[Object]
    analyser.addListener(new MatchingStatusListener {
      def onUpstreamExpired(id: VersionID, vsn:String) {}
      def onPaired(id: VersionID, vsn:String) {
        mb.add(new Object)
      }
      def onDownstreamExpired(id: VersionID, vsn:String) {}
    })

    analyser.onChange(new UpstreamPairChangeEvent(id6, Map(), new DateTime, "vsnAAAA"), ackCallbackA)
    analyser.onChange(new DownstreamPairChangeEvent(idB6, Map(), new DateTime, "vsnAAAA"), ackCallbackB)

    mb.poll(2, TimeUnit.SECONDS) match {
      case null =>
      case _    => fail("Should not receive paired event")
    }
  }

  @Test
  def addingPairWithDifferentVersionsShouldNotResultInPairedMessage() {
    val mb = new LinkedBlockingQueue[Object]
    analyser.addListener(new MatchingStatusListener {
      def onUpstreamExpired(id: VersionID, vsn:String) {}
      def onPaired(id: VersionID, vsn:String) {
        mb.add(new Object)
      }
      def onDownstreamExpired(id: VersionID, vsn:String) {}
    })

    analyser.onChange(new UpstreamPairChangeEvent(id7, Map(), new DateTime, "vsnAAAA"), ackCallbackA)
    analyser.onChange(new DownstreamPairChangeEvent(id7, Map(), new DateTime, "vsnBBBB"), ackCallbackB)

    mb.poll(2, TimeUnit.SECONDS) match {
      case null =>
      case _    => fail("Should not receive paired event")
    }
  }

  @Test
  def addedUpstreamObjectsShouldBeMarkedAsInProgressIfTheyArentMatchedOrExpired() {
    analyser.onChange(new UpstreamPairChangeEvent(id1, Map(), new DateTime,"vsnAAAA"), ackCallbackA)
    assertTrue(analyser.isVersionIDActive(id1))
  }

  @Test
  def multipleAddedUpstreamObjectsShouldBeMarkedAsInProgressIfOnlySomeAreMatchedOrExpired() {
    analyser.onChange(new UpstreamPairChangeEvent(id1, Map(), new DateTime, "vsnAAAA"), ackCallbackA)
    analyser.onChange(new UpstreamPairChangeEvent(id1, Map(), new DateTime, "vsnAAAB"), ackCallbackA)
    analyser.onChange(new DownstreamPairChangeEvent(id1, Map(), new DateTime, "vsnAAAA"), ackCallbackA)
    assertTrue(analyser.isVersionIDActive(id1))
  }


  @Test
  def addedDownstreamObjectsShouldBeMarkedAsInProgressIfTheyArentMatchedOrExpired() {
    analyser.onChange(new DownstreamPairChangeEvent(id1, Map(), new DateTime, "vsnAAAA"), ackCallbackA)
    assertTrue(analyser.isVersionIDActive(id1))
  }

  @Test
  def addedObjectsShouldNotBeMarkedAsInProgressIfTheyAreMatched() {
    analyser.onChange(new UpstreamPairChangeEvent(id1, Map(), new DateTime, "vsnAAAA"), ackCallbackA)
    analyser.onChange(new DownstreamPairChangeEvent(id1, Map(), new DateTime, "vsnAAAA"), ackCallbackB)
    assertFalse(analyser.isVersionIDActive(id1))
  }

  @Test
  def addedUpstreamObjectsShouldNotBeMarkedAsInProgressIfTheyHaveExpired() {
    val mb = new LinkedBlockingQueue[Object]
    analyser.addListener(new MatchingStatusListener {
      def onUpstreamExpired(id: VersionID, vsn:String) { mb.add(new Object) }
      def onPaired(id: VersionID, vsn:String) {}
      def onDownstreamExpired(id: VersionID, vsn:String) {}
    })
    analyser.onChange(new UpstreamPairChangeEvent(id7, Map(), new DateTime, "vsnAAAA"), ackCallbackA)

    mb.poll(5, TimeUnit.SECONDS) match {
      case null => fail("Should have received expiry event")
      case _    =>
    }
    assertFalse(analyser.isVersionIDActive(id7))
  }

  @Test
  def addedDownstreamObjectsShouldNotBeMarkedAsInProgressIfTheyHaveExpired() {
    val mb = new LinkedBlockingQueue[Object]
    analyser.addListener(new MatchingStatusListener {
      def onUpstreamExpired(id: VersionID, vsn:String) {}
      def onPaired(id: VersionID, vsn:String) {}
      def onDownstreamExpired(id: VersionID, vsn:String) { mb.add(new Object) }
    })

    analyser.onChange(new DownstreamPairChangeEvent(id7, Map(), new DateTime, "vsnAAAA"), ackCallbackA)

    mb.poll(2, TimeUnit.SECONDS) match {
      case null => fail("Should have received expiry event")
      case _    =>
    }
    assertFalse(analyser.isVersionIDActive(id7))
  }
}
object AbstractMatcherTest {
  private val counter = new AtomicInteger(1)

  def nextPairId = counter.getAndIncrement
}