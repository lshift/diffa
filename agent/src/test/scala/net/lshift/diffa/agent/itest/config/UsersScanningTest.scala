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
package net.lshift.diffa.agent.itest.config

import org.junit.Assert._
import net.lshift.diffa.agent.itest.support.TestConstants._
import scala.collection.JavaConversions._
import net.lshift.diffa.client.ScanningParticipantRestClientFactory
import net.lshift.diffa.participant.scanning.StringPrefixConstraint
import org.junit.Test
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.schema.servicelimits.ServiceLimit

class UsersScanningTest {
  val limits = new PairServiceLimitsView {
    def getEffectiveLimitByNameForPair(space:Long, pairKey: String, limit:ServiceLimit): Int = limit.defaultLimit
  }

  val pair = PairRef("foo", 801L)
  val endpoint = Endpoint(name = "usersScanningTestEndpoint", scanUrl = agentURL + "/security/scan")

  val domainCredentialsLookup = new FixedDomainCredentialsLookup(pair.space, Some(BasicAuthCredentials("guest", "guest")))
  val participant = ScanningParticipantRestClientFactory.create(pair, endpoint, limits, domainCredentialsLookup)

  @Test
  def aggregationShouldIncludeGuestUser {

    val constraints = Seq(new StringPrefixConstraint("name", "g"))
    val bucketing = Seq()

    val results = participant.scan(constraints, bucketing).toSeq

    assertFalse(results.filter( r => {r.getId == "guest"} ).isEmpty)
  }

  @Test
  def aggregationShouldNotIncludeGuestUser {

    val constraints = Seq(new StringPrefixConstraint("name", "x"))
    val bucketing = Seq()

    val results = participant.scan(constraints, bucketing).toSeq

    assertTrue(results.filter( r => {r.getId == "guest"} ).isEmpty)
  }
}