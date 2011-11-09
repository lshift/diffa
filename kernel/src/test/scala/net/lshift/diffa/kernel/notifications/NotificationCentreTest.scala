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
package net.lshift.diffa.kernel.notifications

import org.junit.Test
import net.lshift.diffa.kernel.lifecycle.NotificationCentre
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.events.VersionID
import org.joda.time.DateTime
import net.lshift.diffa.kernel.config.DiffaPair
import net.lshift.diffa.kernel.config.Domain
import net.lshift.diffa.kernel.config.DiffaPairRef
import net.lshift.diffa.kernel.differencing._

/**
 * Test cases for the Notification Centre.
 */
class NotificationCentreTest {
  val nc = new NotificationCentre

  @Test
  def shouldDispatchToDifferencingListeners() {
    val l1 = createStrictMock("l1", classOf[DifferencingListener])
    val l2 = createStrictMock("l2", classOf[DifferencingListener])
    val l3 = createStrictMock("l2", classOf[DifferencingListener])
    val now = new DateTime(2011, 07, 14, 14, 49, 0, 0)

    nc.registerForDifferenceEvents(l1, Unfiltered)
    nc.registerForDifferenceEvents(l2, Unfiltered)
    nc.registerForDifferenceEvents(l3, MatcherFiltered)

    l1.onMatch(VersionID(DiffaPairRef("p","d"), "e"), "v", TriggeredByScan)
    l2.onMatch(VersionID(DiffaPairRef("p","d"), "e"), "v", TriggeredByScan)
    l3.onMatch(VersionID(DiffaPairRef("p","d"), "e"), "v", TriggeredByScan)
    l1.onMismatch(VersionID(DiffaPairRef("p","d"), "e"), now, "uv", "dv", TriggeredByScan, Unfiltered)
    l2.onMismatch(VersionID(DiffaPairRef("p","d"), "e"), now, "uv", "dv", TriggeredByScan, Unfiltered)
    l3.onMismatch(VersionID(DiffaPairRef("p2","d2"), "e2"), now, "uv2", "dv2", TriggeredByScan, MatcherFiltered)
    replay(l1, l2)

    nc.onMatch(VersionID(DiffaPairRef("p","d"), "e"), "v", TriggeredByScan)
    nc.onMismatch(VersionID(DiffaPairRef("p","d"), "e"), now, "uv", "dv", TriggeredByScan, Unfiltered)
    nc.onMismatch(VersionID(DiffaPairRef("p2","d2"), "e2"), now, "uv2", "dv2", TriggeredByScan, MatcherFiltered)
    verify(l1, l2)
  }

  @Test
  def shouldDispatchToPairScanListeners() {
    val l1 = createStrictMock("l1", classOf[PairScanListener])
    val l2 = createStrictMock("l2", classOf[PairScanListener])
    
    nc.registerForPairScanEvents(l1)
    nc.registerForPairScanEvents(l2)

    val pair = DiffaPair(key = "p", domain = Domain(name="domain"))

    l1.pairScanStateChanged(pair.asRef, PairScanState.SCANNING)
    l2.pairScanStateChanged(pair.asRef, PairScanState.SCANNING)
    replay(l1, l2)

    nc.pairScanStateChanged(pair.asRef, PairScanState.SCANNING)
    verify(l1, l2)
  }
}