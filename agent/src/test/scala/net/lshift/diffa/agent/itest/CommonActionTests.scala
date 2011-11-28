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

package net.lshift.diffa.agent.itest

import support.TestEnvironment
import org.junit.Assert._
import net.lshift.diffa.kernel.client.ActionableRequest
import net.lshift.diffa.client.BadRequestException
import org.junit._
import net.lshift.diffa.kernel.util.AlertCodes
import net.lshift.diffa.kernel.config.DiffaPairRef

trait CommonActionTests {

  def env:TestEnvironment

  @Test
  def shouldListEntityScopedActions {
    val pairRef = DiffaPairRef(env.pairKey,env.domain.name)
    val actions = env.actionsClient.listEntityScopedActions(pairRef)
    assertNotNull(actions)
    assertEquals(1, actions.size)
    assertEquals("Resend Source", actions(0).name)
  }

  @Test
  def invokeEntityScopedAction {
    env.withActionsServer {
      val request = ActionableRequest(env.pairKey, env.domain.name, env.entityScopedActionName, "abc")
      val response = env.actionsClient.invoke(request)
      assertNotNull(response)
      assertEquals("200", response.code)
      assertEquals("resending entity", response.output)
    }
  }

  @Test
  def invokeInvalidAction {
    // Note: Actions test server should NOT be running for this test
    val request = ActionableRequest(env.pairKey, env.domain.name, env.entityScopedActionName, "abc")
    val response = env.actionsClient.invoke(request)
    assertEquals(AlertCodes.ACTION_ENDPOINT_FAILURE, response.code)
  }

  @Test
  def shouldListPairScopedActions {
    env.createPairScopedAction
    val pairRef = DiffaPairRef(env.pairKey,env.domain.name)
    val actions = env.actionsClient.listPairScopedActions(pairRef)
    assertNotNull(actions)
    assertEquals(Some(env.pairScopedActionName), actions.headOption.map(_.name))
  }

  @Test
  def invokePairScopedAction {
    env.withActionsServer {
      env.createPairScopedAction
      val request = ActionableRequest(env.pairKey, env.domain.name, env.pairScopedActionName, null)
      val response = env.actionsClient.invoke(request)
      assertNotNull(response)
      assertEquals("200", response.code)
      assertEquals("resending all", response.output)
    }
  }

  @Test
  def canDeleteAction {
    val pairRef = DiffaPairRef(env.pairKey,env.domain.name)
    def actionName = env.actionsClient.listEntityScopedActions(pairRef).headOption.map(_.name)
    assertEquals(Some(env.entityScopedActionName), actionName)
    env.configurationClient.removeRepairAction(env.entityScopedActionName, env.pairKey)
    assertEquals(None, actionName)
  }

  @Test(expected=classOf[BadRequestException])
  def shouldRejectInvalidActionScope {
    env.configurationClient.declareRepairAction(env.entityScopedActionName, "resend", "INVALID SCOPE", env.pairKey)
  }



}
