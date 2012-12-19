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
import org.easymock.EasyMock._
import org.easymock.classextension.{EasyMock => E4}
import org.junit.Test
import net.lshift.diffa.kernel.util.cache.HazelcastCacheProvider
import org.jooq.impl.Factory
import net.lshift.diffa.kernel.config.system.PolicyKey
import net.lshift.diffa.kernel.actors.IncrementingIdProvider

class DomainMembershipAwareTest {

  val sf = createStrictMock(classOf[SessionFactory])
  val jf = E4.createStrictMock(classOf[JooqDatabaseFacade])

  val cp = new HazelcastCacheProvider

  val membershipListener = createStrictMock(classOf[DomainMembershipAware])

  val domainConfigStore = new JooqDomainConfigStore(jf, cp, IncrementingIdProvider, membershipListener)

  val member = Member("user",0L,0L,"User","domain")

  @Test
  def shouldEmitDomainMembershipCreationEvent() = {
    expect(membershipListener.onMembershipCreated(member)).once()

    // TODO This is only necessary because of the deprecated resolveSpaceName call
    expect(jf.execute(anyObject[Function1[Factory,String]]())).andStubReturn("domain")

    replay(membershipListener)
    E4.replay(jf)

    domainConfigStore.makeDomainMember(0L, "user", PolicyKey(0L, "User"))

    verify(membershipListener)
    E4.verify(jf)
  }

  @Test
  def shouldEmitDomainMembershipRemovalEvent() = {
    expect(membershipListener.onMembershipRemoved(member)).once()

    expect(jf.execute(anyObject[Function1[Factory,String]]())).andStubReturn("domain")

    replay(membershipListener)
    E4.replay(jf)

    domainConfigStore.removeDomainMembership(0L, "user", "User")

    verify(membershipListener)
    E4.verify(jf)
  }


}
