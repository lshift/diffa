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
import net.lshift.diffa.kernel.util.db.DatabaseFacade
import net.lshift.diffa.kernel.hooks.HookManager
import net.sf.ehcache.CacheManager
import org.easymock.EasyMock._
import org.easymock.classextension.{EasyMock => E4}
import org.junit.Test
import org.easymock.EasyMock

class DomainMembershipAwareTest {

  val sf = createStrictMock(classOf[SessionFactory])
  val db = createStrictMock(classOf[DatabaseFacade])
  val pc = E4.createStrictMock(classOf[PairCache])
  val hm = E4.createNiceMock(classOf[HookManager])
  val cm = E4.createNiceMock(classOf[CacheManager])

  // TODO We shouldn't really need to get this far into the HibernateDomainConfigStore, ideally
  expect(cm.cacheExists(EasyMock.isA(classOf[String]))).andStubReturn(false)
  expect(cm.addCache(EasyMock.isA(classOf[String]))).anyTimes()

  E4.replay(cm)

  // TODO Nor should we get this far into the DatabaseFacade, ideally
  expect(db.execute(EasyMock.isA(classOf[String]), EasyMock.isA(classOf[Map[String,Any]]))).andStubReturn(1)

  replay(db)

  val membershipListener = createStrictMock(classOf[DomainMembershipAware])

  val domainConfigStore = new HibernateDomainConfigStore(sf,db,pc,hm,cm, membershipListener)

  val user = User(name = "u")
  val domain = Domain(name = "d")
  val member = Member(user,domain)

  @Test
  def shouldEmitDomainMembershipCreationEvent() = {
    expect(membershipListener.onMembershipCreated(member)).once()

    replay(membershipListener)

    domainConfigStore.makeDomainMember(domain.name, user.name)

    verify(membershipListener)
  }

  @Test
  def shouldEmitDomainMembershipRemovalEvent() = {
    expect(membershipListener.onMembershipRemoved(member)).once()

    replay(membershipListener)

    domainConfigStore.removeDomainMembership(domain.name, user.name)

    verify(membershipListener)
  }


}
