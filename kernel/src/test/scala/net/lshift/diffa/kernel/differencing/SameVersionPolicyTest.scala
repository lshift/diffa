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

package net.lshift.diffa.kernel.differencing

import java.lang.String

import org.junit.Test
import org.easymock.EasyMock._
import org.joda.time.DateTime
import org.easymock.EasyMock
import org.apache.commons.codec.digest.DigestUtils

import net.lshift.diffa.kernel.participants._
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.util.FullDateTimes._
import scala.collection.JavaConversions._
import net.lshift.diffa.participant.scanning.ScanResultEntry

/**
 * Test cases for the same version policy.
 */
class SameVersionPolicyTest extends AbstractPolicyTest {
  val policy = new SameVersionPolicy(stores, listener, systemConfigStore, diagnostics)

  def downstreamVersionFor(v:String) = v

  @Test
  def shouldUpdateDownstreamVersionsWhenStoreIsOutOfDateWithDownstreamParticipantForDateCategories =
    shouldUpdateDownstreamVersionsWhenStoreIsOutOfDateWithDownstreamParticipant(dateCategoryData)

  @Test
  def shouldUpdateDownstreamVersionsWhenStoreIsOutOfDateWithDownstreamParticipantForIntegerCategories =
    shouldUpdateDownstreamVersionsWhenStoreIsOutOfDateWithDownstreamParticipant(integerCategoryData)

  protected def shouldUpdateDownstreamVersionsWhenStoreIsOutOfDateWithDownstreamParticipant(testData: PolicyTestData) {
    upstream.categories = testData.upstreamCategories
    downstream.categories = testData.downstreamCategories
    val timestamp = new DateTime
    // Expect only a top-level scan for the upstream, but a full scan for the downstream
    expectUpstreamAggregateScan(testData.bucketing(0), testData.constraints(0),
      DigestsFromParticipant(
        ScanResultEntry.forAggregate(DigestUtils.md5Hex("vsn1"), testData.attributes(0)),
        ScanResultEntry.forAggregate(DigestUtils.md5Hex("vsn2"), testData.attributes(1))),
      VersionsFromStore(
        Up("id1", testData.values(0), "vsn1"),
        Up("id2", testData.values(1), "vsn2")))

    expectDownstreamAggregateScan(testData.bucketing(0), testData.constraints(0),
      DigestsFromParticipant(
        ScanResultEntry.forAggregate(DigestUtils.md5Hex(downstreamVersionFor("vsn1")), testData.attributes(0)),
        ScanResultEntry.forAggregate(DigestUtils.md5Hex(downstreamVersionFor("vsn2") + downstreamVersionFor("vsn3")), testData.attributes(1))),
      VersionsFromStore(
        Down("id1", testData.values(0), "vsn1", downstreamVersionFor("vsn1")),
        Down("id2", testData.values(1), "vsn2", downstreamVersionFor("vsn2")),
        Down("id4", testData.values(1), "vsn4", downstreamVersionFor("vsn4"))))
    expectDownstreamAggregateScan(testData.bucketing(1), testData.constraints(1),
      DigestsFromParticipant(
        ScanResultEntry.forAggregate(DigestUtils.md5Hex(downstreamVersionFor("vsn2") + downstreamVersionFor("vsn3")), testData.attributes(2))),
      VersionsFromStore(
        Down("id2", testData.values(1), "vsn2", downstreamVersionFor("vsn2")),
        Down("id4", testData.values(1), "vsn4", downstreamVersionFor("vsn4"))))
    expectDownstreamAggregateScan(testData.bucketing(2), testData.constraints(2),
      DigestsFromParticipant(
        ScanResultEntry.forAggregate(DigestUtils.md5Hex(downstreamVersionFor("vsn2") + downstreamVersionFor("vsn3")), testData.attributes(3))),
      VersionsFromStore(
        Down("id2", testData.values(1), "vsn2", downstreamVersionFor("vsn2")),
        Down("id4", testData.values(1), "vsn4", downstreamVersionFor("vsn4"))))
    expectDownstreamEntityScan2(testData.constraints(3),
      DigestsFromParticipant(
        ScanResultEntry.forEntity("id2", downstreamVersionFor("vsn2"), JUL_8_2010_1, testData.values(1)),
        ScanResultEntry.forEntity("id3", downstreamVersionFor("vsn3"), JUL_8_2010_1, testData.values(1))),
      VersionsFromStore(
        Down("id2", testData.values(1), "vsn2", downstreamVersionFor("vsn2")),
        Down("id4", testData.values(1), "vsn4", downstreamVersionFor("vsn4"))))

    // We should see id3 be updated, and id4 be removed
    expect(writer.storeDownstreamVersion(VersionID(pair.asRef, "id3"), testData.downstreamAttributes(1), JUL_8_2010_1, "vsn3", downstreamVersionFor("vsn3"))).
      andReturn(new Correlation(null, pair.asRef, "id3", null, toStrMap(testData.downstreamAttributes(1)), JUL_8_2010_1, timestamp, "vsn3", "vsn3", downstreamVersionFor("vsn3"), false))
    expect(writer.clearDownstreamVersion(VersionID(pair.asRef, "id4"))).
      andReturn(Correlation.asDeleted(pair.asRef, "id4", new DateTime))

    // We should see events indicating that id4 to enter a matched state (since the deletion made the sides line up)
    listener.onMatch(VersionID(pair.asRef, "id4"), null, TriggeredByScan); expectLastCall

    replayAll

    policy.scanUpstream(pair.asRef, upstream, None, writer, usMock, nullListener, feedbackHandle)
    policy.scanDownstream(pair.asRef, downstream, None, writer, usMock, dsMock, listener, feedbackHandle)

    verifyAll
  }
}