/**
 * Copyright (C) 2010-2012 LShift Ltd.
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
package net.lshift.diffa.kernel.config

import org.junit.{Before, Test}
import org.junit.Assert._
import org.easymock.EasyMock._
import net.lshift.diffa.schema.jooq.{DatabaseFacade => JooqDatabaseFacade}
import net.lshift.diffa.kernel.util.cache.HazelcastCacheProvider
import org.easymock.classextension.{EasyMock => E4}
import org.jooq.impl.Factory
import net.lshift.diffa.kernel.frontend._
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.frontend.DomainPairDef
import scala.Some
import net.lshift.diffa.kernel.frontend.EndpointDef
import net.lshift.diffa.kernel.util.MissingObjectException
import system.PolicyKey
import net.lshift.diffa.kernel.util.sequence.HazelcastSequenceProvider
import net.lshift.diffa.kernel.actors.IncrementingIdProvider

class CachedDomainConfigStoreTest {

  val jooq = E4.createStrictMock(classOf[JooqDatabaseFacade])
  val ml = createStrictMock(classOf[DomainMembershipAware])

  val cp = new HazelcastCacheProvider
  val sp = new HazelcastSequenceProvider

  val domainConfig = new JooqDomainConfigStore(jooq, cp, sp, IncrementingIdProvider, ml)

  val spaceId = System.currentTimeMillis()

  @Before
  def resetCaches {
    domainConfig.reset
  }

  @Test
  def shouldCacheDomainMembershipsAndThenInvalidateOnUpdate {

    val members = new java.util.ArrayList[String]()
    members.add("m1")
    members.add("m2")

    expect(jooq.execute(anyObject[Function1[Factory,java.util.List[String]]]())).andReturn(members).once()

    E4.replay(jooq)

    // The first call to get maybeConfigOption should propagate against the DB, but the second call will be cached

    val firstCall = domainConfig.listDomainMembers(spaceId)
    assertEquals(members.toList, firstCall)

    val secondCall = domainConfig.listDomainMembers(spaceId)
    assertEquals(members.toList, secondCall)

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Remove one of the underlying values and expect the DB to be updated. A subsequent call to
    // get listDomainMembers should also propagate against the DB.

    members.remove("m1")
    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,String]]())).andReturn("domain").once() // resolveSpaceName is deprecated, BTW
    expect(jooq.execute(anyObject[Function1[Factory,java.util.List[String]]]())).andReturn(members).once()

    E4.replay(jooq)

    domainConfig.removeDomainMembership(spaceId, "m1", "User")

    val thirdCall = domainConfig.listDomainMembers(spaceId)
    assertEquals(members.toList, thirdCall)

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Add a new underlying value values and expect the DB to be updated. A subsequent call to
    // get listDomainMembers should also propagate against the DB.

    members.remove("m3")
    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,String]]())).andReturn("domain").once() // resolveSpaceName is deprecated, BTW
    expect(jooq.execute(anyObject[Function1[Factory,java.util.List[String]]]())).andReturn(members).once()

    E4.replay(jooq)

    domainConfig.makeDomainMember(spaceId, "m3", PolicyKey(0, "User"))

    val fourthCall = domainConfig.listDomainMembers(spaceId)
    assertEquals(members.toList, fourthCall)

    E4.verify(jooq)
  }

  @Test
  def shouldCacheIndividualDomainConfigOptionsAndThenInvalidateOnUpdate {

    expect(jooq.execute(anyObject[Function1[Factory,String]]())).andReturn("firstValue").once()

    E4.replay(jooq)

    // The first call to get maybeConfigOption should propagate against the DB, but the second call will be cached

    val firstCall = domainConfig.maybeConfigOption(spaceId, "key")
    assertEquals(Some("firstValue"), firstCall)

    val secondCall = domainConfig.maybeConfigOption(spaceId, "key")
    assertEquals(Some("firstValue"), secondCall)

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Remove one of the underlying values and expect the DB to be updated. A subsequent call to
    // get maybeConfigOption should also propagate against the DB.


    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,String]]())).andReturn("\u0000").once()

    E4.replay(jooq)

    domainConfig.clearConfigOption(spaceId, "key")

    val thirdCall = domainConfig.maybeConfigOption(spaceId, "key")
    assertEquals(None, thirdCall)

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Add a new underlying value values and expect the DB to be updated. A subsequent call to
    // get allConfigOptions should also propagate against the DB.

    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,String]]())).andReturn("secondValue").once()

    E4.replay(jooq)

    domainConfig.setConfigOption(spaceId, "key", "secondValue")

    val fourthCall = domainConfig.maybeConfigOption(spaceId, "key")
    assertEquals(Some("secondValue"), fourthCall)

    E4.verify(jooq)
  }

  @Test
  def shouldCacheListingDomainConfigOptionsAndThenInvalidateOnUpdate {

    val opts = new java.util.HashMap[String,String]()

    opts.put("k1","v1")
    opts.put("k2","v2")

    expect(jooq.execute(anyObject[Function1[Factory,java.util.Map[String,String]]]())).andReturn(opts).once()

    E4.replay(jooq)

    // The first call to get allConfigOptions should propagate against the DB, but the second call will be cached

    val firstCall = domainConfig.allConfigOptions(spaceId)
    assertEquals(opts.toMap, firstCall)

    val secondCall = domainConfig.allConfigOptions(spaceId)
    assertEquals(opts.toMap, secondCall)

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Remove one of the underlying values and expect the DB to be updated. A subsequent call to
    // get allConfigOptions should also propagate against the DB.

    opts.remove("k1")
    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,java.util.Map[String,String]]]())).andReturn(opts).once()

    E4.replay(jooq)

    domainConfig.clearConfigOption(spaceId, "k1")

    val thirdCall = domainConfig.allConfigOptions(spaceId)
    assertEquals(opts.toMap, thirdCall)

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Add a new underlying value and expect the DB to be updated. A subsequent call to
    // get allConfigOptions should also propagate against the DB.

    opts.put("k3","v3")
    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,java.util.Map[String,String]]]())).andReturn(opts).once()

    E4.replay(jooq)

    domainConfig.setConfigOption(spaceId, "k3", "v3")

    val fourthCall = domainConfig.allConfigOptions(spaceId)
    assertEquals(opts.toMap, fourthCall)

    E4.verify(jooq)
  }

  @Test
  def shouldCacheIndividualEndpointsAndThenInvalidateOnUpdate {

    val originalEndpoint = DomainEndpointDef(name = "a", scanUrl = "http://acme.com/1")

    val originalEndpointInList = new java.util.ArrayList[DomainEndpointDef]()
    originalEndpointInList.add(originalEndpoint)

    expect(jooq.execute(anyObject[Function1[Factory,java.util.List[DomainEndpointDef]]]())).andReturn(originalEndpointInList).once()

    E4.replay(jooq)

    // The first call to get getEndpointDef should propagate against the DB, but the second call will be cached

    val firstCall = domainConfig.getEndpointDef(spaceId, "a")
    assertEquals(originalEndpoint.withoutDomain(), firstCall)

    val secondCall = domainConfig.getEndpointDef(spaceId, "a")
    assertEquals(originalEndpoint.withoutDomain(), secondCall)

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Remove one of the underlying values and expect the DB to be updated. A subsequent call to
    // get getEndpointDef should also propagate against the DB.


    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,java.util.List[DomainEndpointDef]]]())).andThrow(new MissingObjectException("endpoint")).once()

    E4.replay(jooq)

    domainConfig.deleteEndpoint(spaceId, "a")

    try {
      domainConfig.getEndpointDef(spaceId, "a")
      fail("Should have thrown a MissingObjectException")
    }
    catch {
      case x:MissingObjectException => // ignore
    }

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Add a new underlying value values and expect the DB to be updated. A subsequent call to
    // get getEndpointDef should also propagate against the DB.

    val modifiedEndpoint = originalEndpoint.copy(scanUrl = "http://acme.com/2")

    val modifiedEndpointInList = new java.util.ArrayList[DomainEndpointDef]()
    modifiedEndpointInList.add(modifiedEndpoint)

    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,java.util.List[DomainEndpointDef]]]())).andReturn(modifiedEndpointInList).once()

    E4.replay(jooq)

    domainConfig.createOrUpdateEndpoint(spaceId, modifiedEndpoint.withoutDomain())

    val fourthCall = domainConfig.getEndpointDef(spaceId, "a")
    assertEquals(modifiedEndpoint.withoutDomain(), fourthCall)

    E4.verify(jooq)
  }

  @Test
  def shouldCacheListingEndpointsAndThenInvalidateOnUpdate {

    val endpoints = new java.util.ArrayList[DomainEndpointDef]()
    endpoints.add(DomainEndpointDef(name = "a"))
    endpoints.add(DomainEndpointDef(name = "b"))

    expect(jooq.execute(anyObject[Function1[Factory,java.util.List[DomainEndpointDef]]]())).andReturn(endpoints).once()

    E4.replay(jooq)

    // The first call to get listEndpoints should propagate against the DB, but the second call will be cached

    val firstCall = domainConfig.listEndpoints(spaceId)
    assertEquals(endpoints.map(_.withoutDomain()), firstCall)

    val secondCall = domainConfig.listEndpoints(spaceId)
    assertEquals(endpoints.map(_.withoutDomain()), secondCall)

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Remove one of the underlying values and expect the DB to be updated. A subsequent call to
    // get listEndpoints should also propagate against the DB.

    endpoints.remove(DomainEndpointDef(name = "a"))

    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,java.util.List[DomainEndpointDef]]]())).andReturn(endpoints).once()

    E4.replay(jooq)

    domainConfig.deleteEndpoint(spaceId, "a")

    val thirdCall = domainConfig.listEndpoints(spaceId)
    assertEquals(endpoints.map(_.withoutDomain()), thirdCall)

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Add a new underlying value and expect the DB to be updated. A subsequent call to
    // get listEndpoints should also propagate against the DB.

    endpoints.add(DomainEndpointDef(name = "c"))

    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,java.util.List[DomainEndpointDef]]]())).andReturn(endpoints).once()

    E4.replay(jooq)

    domainConfig.createOrUpdateEndpoint(spaceId, EndpointDef(name = "c"))

    val fourthCall = domainConfig.listEndpoints(spaceId)
    assertEquals(endpoints.map(_.withoutDomain()), fourthCall)

    E4.verify(jooq)
  }


  @Test
  def shouldCacheIndividualPairDefs {

    val pair = DomainPairDef(key = "pair", space = spaceId)
    expect(jooq.execute(anyObject[Function1[Factory,DomainPairDef]]())).andReturn(pair).once()

    E4.replay(jooq)

    val firstCall = domainConfig.getPairDef(pair.asRef)
    assertEquals(pair, firstCall)

    val secondCall = domainConfig.getPairDef(pair.asRef)
    assertEquals(pair, secondCall)

    E4.verify(jooq)
  }

  @Test
  def shouldCacheListingPairDefs = {

    val pair1 = DomainPairDef(key = "pair1", space = spaceId)
    val pair2 = DomainPairDef(key = "pair2", space = spaceId)
    expect(jooq.execute(anyObject[Function1[Factory,Seq[DomainPairDef]]]())).andReturn(Seq(pair1, pair2)).once()

    E4.replay(jooq)

    val firstCall = domainConfig.listPairs(spaceId)
    assertEquals(Seq(pair1, pair2), firstCall)

    val secondCall = domainConfig.listPairs(spaceId)
    assertEquals(Seq(pair1, pair2), secondCall)

    E4.verify(jooq)
  }

  @Test
  def shouldCacheListingPairDefsByEndpoint = {

    val pair1 = DomainPairDef(key = "pair1", space = spaceId)
    val pair2 = DomainPairDef(key = "pair2", space = spaceId)
    expect(jooq.execute(anyObject[Function1[Factory,Seq[DomainPairDef]]]())).andReturn(Seq(pair1, pair2)).once()

    E4.replay(jooq)

    val firstCall = domainConfig.listPairsForEndpoint(spaceId, "endpoint")
    assertEquals(Seq(pair1, pair2), firstCall)

    val secondCall = domainConfig.listPairsForEndpoint(spaceId, "endpoint")
    assertEquals(Seq(pair1, pair2), secondCall)

    E4.verify(jooq)
  }

}
