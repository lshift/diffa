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
package net.lshift.diffa.kernel.frontend

import net.lshift.diffa.kernel.StoreReferenceContainer
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.actors.PairPolicyClient
import net.lshift.diffa.kernel.matching.MatchingManager
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import org.junit.{Test, BeforeClass, AfterClass}
import net.lshift.diffa.participant.changes.ChangeEvent
import org.joda.time.DateTime
import net.lshift.diffa.kernel.events.{DownstreamPairChangeEvent, VersionID, UpstreamPairChangeEvent, PairChangeEvent}
import net.lshift.diffa.kernel.differencing.StringAttribute
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.config.{Space, SetCategoryDescriptor, Domain, PairRef}
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import org.apache.commons.lang.RandomStringUtils

class ChangesTest {
  private val storeReferences = ChangesTest.storeReferences

  val changeEventClient = createMock(classOf[PairPolicyClient])
  val matchingManager = createMock("matchingManager", classOf[MatchingManager])
  val diagnosticsManager = createMock("diagnosticsManager", classOf[DiagnosticsManager])
  val changes = new Changes(storeReferences.domainConfigStore, changeEventClient, matchingManager, diagnosticsManager)

  val now = new DateTime

  var space:Space = ChangesTest.space
  var pairRef:PairRef = ChangesTest.pairRef

  @Test
  def shouldAcceptChangeWithoutAttributes() {
    changeEventClient.propagateChangeEvent(UpstreamPairChangeEvent(VersionID(pairRef, "id1"), Map(), now, "v1")); expectLastCall
    expect(matchingManager.getMatcher(anyObject[PairRef])).andStubReturn(None)
    replay(changeEventClient, matchingManager)

    changes.onChange(space.id, "e1", ChangeEvent.forChange("id1", "v1", now))
    verify(changeEventClient, matchingManager)
  }

  @Test
  def shouldAcceptChangeWithValidAttributes() {
    changeEventClient.propagateChangeEvent(
      DownstreamPairChangeEvent(VersionID(pairRef, "id1"), Map("s" -> StringAttribute("a")), now, "v1")); expectLastCall
    expect(matchingManager.getMatcher(anyObject[PairRef])).andStubReturn(None)
    replay(changeEventClient, matchingManager)

    changes.onChange(space.id, "e2", ChangeEvent.forChange("id1", "v1", now, Map("s" -> "a")))
    verify(changeEventClient, matchingManager)
  }

  @Test
  def shouldDropChangeWithInvalidAttributes() {
    replay(changeEventClient, matchingManager)

    changes.onChange(space.id, "e2", ChangeEvent.forChange("id1", "v1", now, Map("s" -> "c")))
    changes.onChange(space.id, "e2", ChangeEvent.forChange("id1", "v1", now, Map("t" -> "123")))
    changes.onChange(space.id, "e2", ChangeEvent.forChange("id1", "v1", now))
    verify(changeEventClient, matchingManager)
  }
}

object ChangesTest {

  var space:Space = null
  var pairRef:PairRef = null

  private[ChangesTest] val env = TestDatabaseEnvironments.uniqueEnvironment("target/changesTest")

  private[ChangesTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  @BeforeClass
  def setupEnv() {
    space = storeReferences.systemConfigStore.createOrUpdateSpace(RandomStringUtils.randomAlphanumeric(10))

    pairRef = PairRef(space = space.id , name = "p1")

    storeReferences.domainConfigStore.createOrUpdateEndpoint(space.id, EndpointDef(name = "e1"))
    storeReferences.domainConfigStore.createOrUpdateEndpoint(space.id,
      EndpointDef(name = "e2", categories = Map("s" -> new SetCategoryDescriptor(Set("a", "b")))))
    storeReferences.domainConfigStore.createOrUpdatePair(space.id, PairDef(key = "p1", upstreamName = "e1", downstreamName = "e2"))
  }

  @AfterClass
  def cleanupSchema() {
    storeReferences.tearDown
  }
}