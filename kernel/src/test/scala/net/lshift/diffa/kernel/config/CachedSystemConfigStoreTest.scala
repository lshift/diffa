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

import org.easymock.classextension.{EasyMock => E4}
import net.lshift.diffa.schema.jooq.DatabaseFacade
import net.lshift.diffa.kernel.util.cache.HazelcastCacheProvider
import org.junit.{Before, Test}
import org.easymock.EasyMock._
import org.jooq.impl.Factory
import org.junit.Assert._
import net.lshift.diffa.kernel.config.system.JooqSystemConfigStore
import net.lshift.diffa.kernel.util.sequence.HazelcastSequenceProvider
import org.easymock.IAnswer

class CachedSystemConfigStoreTest {

  val jooq = E4.createStrictMock(classOf[DatabaseFacade])
  val cp = new HazelcastCacheProvider
  val sp = new HazelcastSequenceProvider

  val spacePathCache = new SpacePathCache(jooq, cp)

  // When the JooqSystemConfigStore, boots it syncs the sequence provider with the database, so we need to mock this out

  val persistentSequenceValue = System.currentTimeMillis()

  expect(jooq.execute(anyObject[Function1[Factory,Long]]())).andReturn(persistentSequenceValue).once()

  E4.replay(jooq)

  val configStore = new JooqSystemConfigStore(jooq,cp, sp, spacePathCache)

  // Make sure the that booting the JooqSystemConfigStore has performed the sequence provider sync

  E4.verify(jooq)

  // Make the jooq mock available for use again

  E4.reset(jooq)

  @Before
  def invalidateCaches {
    spacePathCache.reset
  }

  @Test
  def shouldCacheDomainExistenceAndInvalidateOnRemoval {
    val domain = "a"

    expect(jooq.execute(anyObject[Function1[Factory,Space]]())).andReturn(Space(id = persistentSequenceValue + 1)).once()

    E4.replay(jooq)

    // The first call to get doesDomainExist should propagate against the DB, but the second call will be cached

    assertTrue(configStore.doesDomainExist(domain))
    assertTrue(configStore.doesDomainExist(domain))

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Remove the domain and verify that this result is also cached

    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,Space]]())).andReturn(spacePathCache.NON_EXISTENT_SPACE).times(2)

    E4.replay(jooq)

    configStore.deleteDomain(domain)

    assertFalse(configStore.doesDomainExist(domain))
    assertFalse(configStore.doesDomainExist(domain))

    // Due to the fact that we have a forwards and reverse cache, make sure that the number of invocations of what
    // we expect to be cached is greater than the number of DB calls

    assertFalse(configStore.doesDomainExist(domain))

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Now re-add the old domain and make sure that doesDomainExist reflects this coherently
    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andAnswer(new IAnswer[Unit] {
      def answer = {
        spacePathCache.reset
      }
    }).once()

    expect(jooq.execute(anyObject[Function1[Factory,Space]]())).andReturn(Space(id = persistentSequenceValue + 2)).once()

    E4.replay(jooq)

    configStore.createOrUpdateDomain(domain)

    // The first call to get doesDomainExist should propagate against the DB, but the second call will be cached

    assertTrue(configStore.doesDomainExist(domain))
    assertTrue(configStore.doesDomainExist(domain))

    E4.verify(jooq)

  }
}
