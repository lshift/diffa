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

package net.lshift.diffa.messaging.json

import net.lshift.diffa.kernel.protocol.ProtocolMapper
import net.lshift.diffa.kernel.frontend.Changes
import net.lshift.diffa.kernel.lifecycle.AgentLifecycleAware
import net.lshift.diffa.kernel.participants._

/**
 * Utility class responsible for registering the JSON protocol support with necessary factories
 * and request handlers.
 */
class JsonMessagingRegistrar(val protocolMapper:ProtocolMapper,
                             val participantFactory:ParticipantFactory,
                             val changes:Changes)
  extends AgentLifecycleAware {

  val scanningFactory = new ScanningParticipantFactory {
    def supportsAddress(address: String, protocol: String) = isValidProtocol(address, protocol)
    def createParticipantRef(address: String, protocol: String) = new ScanningParticipantRestClient(address)
  }
  val contentFactory = new ContentParticipantFactory {
    def supportsAddress(address: String, protocol: String) = isValidProtocol(address, protocol)
    def createParticipantRef(address: String, protocol: String) = new ContentParticipantRestClient(address)
  }
  val versioningFactory = new VersioningParticipantFactory {
    def supportsAddress(address: String, protocol: String) = isValidProtocol(address, protocol)
    def createParticipantRef(address: String, protocol: String) = new VersioningParticipantRestClient(address)
  }

  // Register the outbound participant factory for JSON/HTTP
  participantFactory.registerScanningFactory(scanningFactory)
  participantFactory.registerContentFactory(contentFactory)
  participantFactory.registerVersioningFactory(versioningFactory)

  private def isValidProtocol(address: String, protocol: String) =
      protocol == "application/json" && address.startsWith("http://")
}