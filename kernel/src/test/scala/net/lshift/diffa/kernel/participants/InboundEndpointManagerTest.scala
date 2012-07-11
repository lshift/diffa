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

package net.lshift.diffa.kernel.participants

import org.junit.Test
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.config.Endpoint
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import org.junit.Assert._
import net.lshift.diffa.kernel.frontend.DomainEndpointDef

/**
 * Test cases for the InboundEndpointManager.
 */
class InboundEndpointManagerTest {
  val configStore = createMock(classOf[SystemConfigStore])
  val manager = new InboundEndpointManager(configStore)
  val inboundEndpointFactory = new InboundEndpointFactory {
    var lastEp:DomainEndpointDef = null

    def canHandleInboundEndpoint(url: String) = url.startsWith("amqp")
    def ensureEndpointReceiver(e: DomainEndpointDef) = lastEp = e
    def endpointGone(domain: String, endpoint: String) = null
  }

  @Test
  def shouldIgnoreEndpointWhereNoInboundUrlIsConfigured {
    // TODO [#146] Wire in log verification for this test
    manager.onEndpointAvailable(DomainEndpointDef(name = "e", scanUrl = "http://localhost/1234/scan"))
  }

  @Test
  def shouldHandleEndpointWhereInboundUrlIsNotSupported {
    // TODO [#146] Wire in log verification for this test
    manager.onEndpointAvailable(DomainEndpointDef(name = "e", scanUrl = "http://localhost/1234/scan", inboundUrl = "amqp:queue.name"))
  }

  @Test
  def shouldInformFactoryWhenValidEndpointIsAvailable {
    manager.registerFactory(inboundEndpointFactory)
    manager.onEndpointAvailable(DomainEndpointDef(name = "e", scanUrl = "http://localhost/1234/scan", inboundUrl = "amqp:queue.name"))

    assertNotNull(inboundEndpointFactory.lastEp)
    assertEquals("e", inboundEndpointFactory.lastEp.name)
  }

  @Test
  def shouldActivateStoredEndpoint {
    manager.registerFactory(inboundEndpointFactory)

    expect(configStore.listEndpoints).andReturn(Seq(DomainEndpointDef(name = "e", scanUrl = "http://localhost/1234/scan", inboundUrl = "amqp:queue.name")))
    replay(configStore)

    manager.onAgentConfigurationActivated
    assertNotNull(inboundEndpointFactory.lastEp)
    assertEquals("e", inboundEndpointFactory.lastEp.name)
  }
}