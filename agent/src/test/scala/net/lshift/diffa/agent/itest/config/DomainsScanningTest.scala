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

import org.junit.Test
import net.lshift.diffa.participant.scanning.StringPrefixConstraint
import org.junit.Assert._
import net.lshift.diffa.client.ScanningParticipantRestClient
import net.lshift.diffa.agent.itest.support.TestConstants._

class DomainsScanningTest {

  val participant = new ScanningParticipantRestClient(agentURL + "/root/domains/scan")

  @Test
  def aggregationShouldIncludeDefaultDomain {

    val constraints = Seq(new StringPrefixConstraint("name", "di"))
    val bucketing = Seq()

    val results = participant.scan(constraints, bucketing).toSeq

    assertFalse(results.filter( r => {r.getId == "diffa"} ).isEmpty)
  }

  @Test
  def aggregationShouldNotIncludeDefaultDomain {

    val constraints = Seq(new StringPrefixConstraint("name", "fa"))
    val bucketing = Seq()

    val results = participant.scan(constraints, bucketing).toSeq

    assertTrue(results.filter( r => {r.getId == "diffa"} ).isEmpty)
  }
}
