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

package net.lshift.diffa.client

import net.lshift.diffa.kernel.participants._
import net.lshift.diffa.kernel.config.{DomainCredentialsLookup, DiffaPairRef, DomainCredentialsManager, PairServiceLimitsView}

trait ParticipantRestClientFactory {

  def supportsAddress(address: String) = address.startsWith("http://") || address.startsWith("https://")
}

class ScanningParticipantRestClientFactory(credentialsLookup:DomainCredentialsLookup, limits: PairServiceLimitsView)
  extends ScanningParticipantFactory with ParticipantRestClientFactory {

  def createParticipantRef(address: String, pair:DiffaPairRef) =
    new ScanningParticipantRestClient(serviceLimitsView = limits,
                                      scanUrl = address,
                                      credentialsLookup = credentialsLookup,
                                      pair = pair)
}

class ContentParticipantRestClientFactory(credentialsLookup:DomainCredentialsLookup, limits: PairServiceLimitsView)
  extends ContentParticipantFactory with ParticipantRestClientFactory {

  def createParticipantRef(address: String, pair:DiffaPairRef)
    = new ContentParticipantRestClient(serviceLimitsView = limits,
                                       scanUrl = address,
                                       credentialsLookup = credentialsLookup,
                                       pair = pair)
}

class VersioningParticipantRestClientFactory(credentialsLookup:DomainCredentialsLookup, limits: PairServiceLimitsView)
  extends VersioningParticipantFactory with ParticipantRestClientFactory {

  def createParticipantRef(address: String, pair:DiffaPairRef)
    = new VersioningParticipantRestClient(serviceLimitsView = limits,
                                          scanUrl = address,
                                          credentialsLookup = credentialsLookup,
                                          pair = pair)
}