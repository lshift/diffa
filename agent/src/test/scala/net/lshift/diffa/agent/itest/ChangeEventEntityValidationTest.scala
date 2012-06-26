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
package net.lshift.diffa.agent.itest

import net.lshift.diffa.client.{InvalidChangeEventException, ChangesRestClient}
import support.TestConstants.{ agentURL, defaultDomain, yesterday }
import com.eaio.uuid.UUID
import org.junit.{BeforeClass, Test}
import org.junit.Assert.fail
import net.lshift.diffa.participant.changes.ChangeEvent
import net.lshift.diffa.agent.client.ConfigurationRestClient
import net.lshift.diffa.kernel.frontend.EndpointDef


class ChangeEventEntityValidationTest {
  import ChangeEventEntityValidationTest._

  lazy val changeClient = new ChangesRestClient(agentURL, defaultDomain, endpoint)
  lazy val event = ChangeEvent.forChange("\u2603", "aVersion", yesterday)

  @Test(expected=classOf[InvalidChangeEventException])
  def rejectsChangesForInvalidEntities = {
      changeClient.onChangeEvent(event)
  }
}

object ChangeEventEntityValidationTest {
  val endpoint = new UUID().toString
  @BeforeClass
  def configure {
    new ConfigurationRestClient(agentURL, defaultDomain).declareEndpoint(
      EndpointDef(name = endpoint))
  }

}