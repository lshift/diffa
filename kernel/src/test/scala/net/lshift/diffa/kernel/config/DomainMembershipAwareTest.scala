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

import org.hibernate.SessionFactory
import net.lshift.diffa.schema.jooq.{DatabaseFacade => JooqDatabaseFacade}
import net.lshift.diffa.kernel.hooks.HookManager
import org.easymock.EasyMock._
import org.easymock.classextension.{EasyMock => E4}
import org.junit.Test
import net.lshift.diffa.kernel.util.cache.HazelcastCacheProvider

class DomainMembershipAwareTest {

  val sf = createStrictMock(classOf[SessionFactory])
  val jf = E4.createStrictMock(classOf[JooqDatabaseFacade])
  val hm = E4.createNiceMock(classOf[HookManager])

  val cp = new HazelcastCacheProvider

  val spacePathCache = E4.createStrictMock(classOf[SpacePathCache])
  val membershipListener = createStrictMock(classOf[DomainMembershipAware])

  val domainConfigStore = new JooqDomainConfigStore(jf, hm, cp, membershipListener, spacePathCache)

  val member = Member("user",0L,"domain")

  @Test
  def shouldEmitDomainMembershipCreationEvent() = {
    expect(membershipListener.onMembershipCreated(member)).once()
    expect(spacePathCache.resolveSpacePathOrDie("domain")).andReturn(Space(id = 0L)).once()

    replay(membershipListener)
    E4.replay(spacePathCache)

    domainConfigStore.makeDomainMember("domain", "user")

    verify(membershipListener)
    E4.verify(spacePathCache)
  }

  @Test
  def shouldEmitDomainMembershipRemovalEvent() = {
    expect(membershipListener.onMembershipRemoved(member)).once()
    expect(spacePathCache.resolveSpacePathOrDie("domain")).andReturn(Space(id = 0L)).once()

    replay(membershipListener)
    E4.replay(spacePathCache)

    domainConfigStore.removeDomainMembership("domain", "user")

    verify(membershipListener)
    E4.verify(spacePathCache)
  }


}
