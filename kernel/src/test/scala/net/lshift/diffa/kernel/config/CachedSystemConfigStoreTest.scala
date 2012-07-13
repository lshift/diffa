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
import org.junit.Test
import org.easymock.EasyMock._
import org.jooq.impl.Factory
import org.junit.Assert._
import net.lshift.diffa.kernel.config.system.JooqSystemConfigStore

class CachedSystemConfigStoreTest {

  val jooq = E4.createStrictMock(classOf[DatabaseFacade])
  val cp = new HazelcastCacheProvider

  val configStore = new JooqSystemConfigStore(jooq,cp)

  @Test
  def shouldCacheDomainExistenceAndInvalidateOnRemoval {
    val domain = "a"

    expect(jooq.execute(anyObject[Function1[Factory,java.lang.Long]]())).andReturn(1L).once()

    E4.replay(jooq)

    // The first call to get doesDomainExist should propagate against the DB, but the second call will be cached

    assertTrue(configStore.doesDomainExist(domain))
    assertTrue(configStore.doesDomainExist(domain))

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Remove the domain and verify that this result is also cached

    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,java.lang.Long]]())).andReturn(0L).once()

    E4.replay(jooq)

    configStore.deleteDomain(domain)

    assertFalse(configStore.doesDomainExist(domain))
    assertFalse(configStore.doesDomainExist(domain))

    E4.verify(jooq)

    // Reset the mocks control and an intermediate step to verify the calls to the underlying mock are all in order

    E4.reset(jooq)

    // Now re-add the old domain and make sure that doesDomainExist reflects this coherently
    expect(jooq.execute(anyObject[Function1[Factory,Unit]]())).andReturn(Unit).once()
    expect(jooq.execute(anyObject[Function1[Factory,java.lang.Long]]())).andReturn(1L).once()

    E4.replay(jooq)

    configStore.createOrUpdateDomain(domain)

    // The first call to get doesDomainExist should propagate against the DB, but the second call will be cached

    assertTrue(configStore.doesDomainExist(domain))
    assertTrue(configStore.doesDomainExist(domain))

    E4.verify(jooq)

  }
}
