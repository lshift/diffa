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

package net.lshift.diffa.kernel.matching

import net.lshift.diffa.kernel.util.{ConcurrentJunitRunner, Concurrent}
import org.junit.runner.RunWith
import org.junit.After
import net.lshift.diffa.kernel.config.Domain
import net.lshift.diffa.kernel.frontend.DomainPairDef

/**
 * Test cases for the locally maintained matching engine. Attempts to exercise various matching and expiration code paths.
 */
@RunWith(classOf[ConcurrentJunitRunner])
@Concurrent(threads = 20)
class LocalEventMatcherTest extends AbstractMatcherTest {
  val reaper = new LocalEventMatcherReaper

  @After
  def closeReaper {
    reaper.dispose
  }

  def createMatcher(id: String, timeout: Int) =
    new LocalEventMatcher(DomainPairDef(key = id, domain = "domain", matchingTimeout = timeout), reaper)
}