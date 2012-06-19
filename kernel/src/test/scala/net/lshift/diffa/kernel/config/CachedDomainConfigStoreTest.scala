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
import org.hibernate.SessionFactory
import org.easymock.EasyMock._
import net.lshift.diffa.schema.jooq.{DatabaseFacade => JooqDatabaseFacade}
import net.lshift.diffa.kernel.util.db.DatabaseFacade
import net.lshift.diffa.kernel.hooks.HookManager
import net.lshift.diffa.kernel.util.cache.HazelcastCacheProvider
import org.easymock.classextension.{EasyMock => E4}
import org.jooq.impl.Factory
import net.lshift.diffa.kernel.frontend.{EndpointDef, DomainPairDef}

class CachedDomainConfigStoreTest {

  val sf = createStrictMock(classOf[SessionFactory])
  val db = createStrictMock(classOf[DatabaseFacade])
  val jooq = E4.createStrictMock(classOf[JooqDatabaseFacade])
  val hm = E4.createNiceMock(classOf[HookManager])
  val ml = createStrictMock(classOf[DomainMembershipAware])

  val cp = new HazelcastCacheProvider

  val domainConfig = new HibernateDomainConfigStore(sf, db, jooq, hm, cp, ml)

  @Before
  def resetCaches {
    domainConfig.reset
  }

  @Test
  def shouldCacheIndividualPairDefs {

    val pair = DomainPairDef(key = "pair", domain = "domain")
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

    val pair1 = DomainPairDef(key = "pair1", domain = "domain")
    val pair2 = DomainPairDef(key = "pair2", domain = "domain")
    expect(jooq.execute(anyObject[Function1[Factory,Seq[DomainPairDef]]]())).andReturn(Seq(pair1, pair2)).once()

    E4.replay(jooq)

    val firstCall = domainConfig.listPairs("domain")
    assertEquals(Seq(pair1, pair2), firstCall)

    val secondCall = domainConfig.listPairs("domain")
    assertEquals(Seq(pair1, pair2), secondCall)

    E4.verify(jooq)
  }

  @Test
  def shouldCacheListingPairDefsByEndpoint = {

    val pair1 = DomainPairDef(key = "pair1", domain = "domain")
    val pair2 = DomainPairDef(key = "pair2", domain = "domain")
    expect(jooq.execute(anyObject[Function1[Factory,Seq[DomainPairDef]]]())).andReturn(Seq(pair1, pair2)).once()

    E4.replay(jooq)

    val firstCall = domainConfig.listPairsForEndpoint("domain", "endpoint")
    assertEquals(Seq(pair1, pair2), firstCall)

    val secondCall = domainConfig.listPairsForEndpoint("domain", "endpoint")
    assertEquals(Seq(pair1, pair2), secondCall)

    E4.verify(jooq)
  }

  // TODO Comment this back in when caching for endpoints lands
  //@Test
  def shouldCacheIndividualEndpoints {

    val endpoint = EndpointDef(name = "endpoint")
    expect(jooq.execute(anyObject[Function1[Factory,EndpointDef]]())).andReturn(endpoint).once()

    E4.replay(jooq)

    val firstCall = domainConfig.getEndpointDef("domain", "endpoint")
    assertEquals(endpoint, firstCall)

    val secondCall = domainConfig.getEndpointDef("domain", "endpoint")
    assertEquals(endpoint, secondCall)

    E4.verify(jooq)
  }
}
