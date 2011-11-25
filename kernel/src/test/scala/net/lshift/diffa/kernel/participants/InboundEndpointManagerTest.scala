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

/**
 * Test cases for the InboundEndpointManager.
 */
class InboundEndpointManagerTest {
  val configStore = createMock(classOf[SystemConfigStore])
  val manager = new InboundEndpointManager(configStore)
  val jsonFactory = new InboundEndpointFactory {
    var lastEp:Endpoint = null

    def canHandleInboundEndpoint(url: String) = url.startsWith("amqp")
    def ensureEndpointReceiver(e: Endpoint) = lastEp = e
    def endpointGone(key: String) = null
  }

  @Test
  def shouldIgnoreEndpointWhereNoInboundUrlIsConfigured {
    // TODO [#146] Wire in log verification for this test
    manager.onEndpointAvailable(Endpoint(name = "e", scanUrl = "http://localhost/1234/scan", contentType = "application/json"))
  }

  @Test
  def shouldHandleEndpointWhereInboundUrlIsNotSupported {
    // TODO [#146] Wire in log verification for this test
    manager.onEndpointAvailable(Endpoint(name = "e", scanUrl = "http://localhost/1234/scan", contentType = "application/json", inboundUrl = "amqp:queue.name"))
  }

  @Test
  def shouldInformFactoryWhenValidEndpointIsAvailable {
    manager.registerFactory(jsonFactory)
    manager.onEndpointAvailable(Endpoint(name = "e", scanUrl = "http://localhost/1234/scan", contentType = "application/json", inboundUrl = "amqp:queue.name"))

    assertNotNull(jsonFactory.lastEp)
    assertEquals("e", jsonFactory.lastEp.name)
  }

  @Test
  def shouldActivateStoredEndpoint {
    manager.registerFactory(jsonFactory)

    expect(configStore.listEndpoints).andReturn(Seq(Endpoint(name = "e", scanUrl = "http://localhost/1234/scan", contentType = "application/json", inboundUrl = "amqp:queue.name")))
    replay(configStore)

    manager.onAgentConfigurationActivated
    assertNotNull(jsonFactory.lastEp)
    assertEquals("e", jsonFactory.lastEp.name)
  }
}