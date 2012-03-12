/**
 * Copyright (C) 2012 LShift Ltd.
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

import net.lshift.diffa.agent.itest.support.TestConstants._
import org.junit.Test
import org.junit.Assert._
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.differencing.{MatchState, DifferenceEvent}
import support.{IncludesObjId, DoesntIncludeObjId, DiffCount, TestEnvironments}
import net.lshift.diffa.participant.scanning.SetConstraint
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.frontend.InvalidInventoryException

class InventoryTest extends AbstractEnvironmentTest {
  val envFactory = TestEnvironments.same _

  @Test
  def shouldGenerateDifferencesBasedUponAnInventoryBeingUploaded() {
    env.inventoryClient.uploadInventory(env.upstreamEpName, Seq(), csv(
      "id,vsn,someString,someDate",
      "id1,v1,ss,2012-03-09T09:04:00Z",
      "id2,v2,tt,2012-03-10T10:05:12Z"
    ))

    val diffs = env.differencesHelper.waitFor(yesterday, tomorrow, DiffCount(2)).sortBy(e => e.objId.id)
    
    assertDiffEquals(
      DifferenceEvent(objId = VersionID(env.pairRef, "id1"), state = MatchState.UNMATCHED, upstreamVsn = "v1"),
      diffs(0))
    assertDiffEquals(
      DifferenceEvent(objId = VersionID(env.pairRef, "id2"), state = MatchState.UNMATCHED, upstreamVsn = "v2"),
      diffs(1))
  }

  @Test
  def shouldResolveDifferencesWhenMatchingInventoryIsUploadedForDownstream() {
    env.inventoryClient.uploadInventory(env.upstreamEpName, Seq(), csv(
      "id,vsn,someString,someDate",
      "id1,v1,ss,2012-03-09T09:04:00Z",
      "id2,v2,tt,2012-03-10T10:05:12Z"
    ))
    env.differencesHelper.waitFor(yesterday, tomorrow, DiffCount(2))

    env.inventoryClient.uploadInventory(env.downstreamEpName, Seq(), csv(
      "id,vsn,someString,someDate",
      "id1,v1,ss,2012-03-09T09:04:00Z",
      "id2,v2,tt,2012-03-10T10:05:12Z"
    ))
    env.differencesHelper.waitFor(yesterday, tomorrow, DiffCount(0))
  }

  @Test
  def shouldSeeTheDifferencesBetweenTwoInventories() {
    env.inventoryClient.uploadInventory(env.upstreamEpName, Seq(), csv(
      "id,vsn,someString,someDate",
      "id1,v1,ss,2012-03-09T09:04:00Z",
      "id2,v2,tt,2012-03-10T10:05:12Z"
    ))
    env.inventoryClient.uploadInventory(env.downstreamEpName, Seq(), csv(
      "id,vsn,someString,someDate",
      "id1,v1,ss,2012-03-09T09:04:00Z",
      "id2,v3,tt,2012-03-10T10:05:12Z",
      "id3,v3,tt,2012-03-10T10:05:12Z"
    ))
    val diffs = env.differencesHelper.
      waitFor(yesterday, tomorrow, DiffCount(2), DoesntIncludeObjId("id1")).
      sortBy(e => e.objId.id)

    assertDiffEquals(
      DifferenceEvent(objId = VersionID(env.pairRef, "id2"), state = MatchState.UNMATCHED, upstreamVsn = "v2", downstreamVsn = "v3"),
      diffs(0))
    assertDiffEquals(
      DifferenceEvent(objId = VersionID(env.pairRef, "id3"), state = MatchState.UNMATCHED, downstreamVsn = "v3"),
      diffs(1))
  }

  @Test
  def shouldAllowInventoryRegionToBeRestrictedToAllowPartialUpload() {
    env.inventoryClient.uploadInventory(env.upstreamEpName, Seq(), csv(
      "id,vsn,someString,someDate",
      "id1,v1,ss,2012-03-09T09:04:00Z",
      "id2,v2,tt,2012-03-10T10:05:12Z"
    ))
    env.differencesHelper.waitFor(yesterday, tomorrow, DiffCount(2))

    // Upload the downstream inventory in two parts
    env.inventoryClient.uploadInventory(env.downstreamEpName, Seq(new SetConstraint("someString", Set("ss"))), csv(
      "id,vsn,someString,someDate",
      "id1,v1,ss,2012-03-09T09:04:00Z"
    ))
    env.inventoryClient.uploadInventory(env.downstreamEpName, Seq(new SetConstraint("someString", Set("tt"))), csv(
      "id,vsn,someString,someDate",
      "id2,v2,tt,2012-03-10T10:05:12Z",
      "id3,v3,tt,2012-03-10T10:05:12Z"
    ))

    // Wait for us to reach a state of having only one difference, that being the additional v3
    env.differencesHelper.waitFor(yesterday, tomorrow, DiffCount(1), IncludesObjId("id3"))
  }

  @Test
  def shouldRejectAnInventoryUploadWithMissingColumnsWithABadRequestResponse() {
    try {
      env.inventoryClient.uploadInventory(env.upstreamEpName, Seq(), csv(
        "id,vsn,someString",
        "id1,v1,ss",
        "id2,v2,tt"
      ))
      fail("Request should have failed with BadInventoryException")
    } catch {
      case e:InvalidInventoryException =>
        assertEquals(
          "Inventory was invalid: Entry 1 was invalid. Identified issues were: someDate: property is missing",
          e.getMessage)
    }
  }

  @Test
  def shouldRejectAnInventoryUploadWithInvalidConstraintsWithABadRequestResponse() {
    try {
      // The constraint someString=qq on the upload isn't valid, since the someString category only
      // supports ss and tt.
      env.inventoryClient.uploadInventory(env.upstreamEpName, Seq(new SetConstraint("someString", Set("qq"))), csv(
        "id,vsn,someString,someDate",
        "id2,v2,qq,2012-03-10T10:05:12Z",
        "id3,v3,qq,2012-03-10T10:05:12Z"
      ))
      fail("Request should have failed with BadInventoryException")
    } catch {
      case e:InvalidInventoryException =>
        assertEquals(
          "Constraint was invalid: someString: Not all of the values [qq] are supported by category [ss, tt]",
          e.getMessage)
    }
  }

  private def csv(lines:String*) = lines.mkString("\n")
  private def assertDiffEquals(expected:DifferenceEvent, actual:DifferenceEvent) {
    assertEquals(expected.objId, actual.objId)
    assertEquals(expected.state, actual.state)
    assertEquals(expected.upstreamVsn, actual.upstreamVsn)
    assertEquals(expected.downstreamVsn, actual.downstreamVsn)
  }
}