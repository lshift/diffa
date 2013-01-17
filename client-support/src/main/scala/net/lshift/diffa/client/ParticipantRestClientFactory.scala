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


import net.lshift.diffa.kernel.config._

import net.lshift.diffa.schema.servicelimits.{ScanReadTimeout, ScanConnectTimeout}
import net.lshift.diffa.kernel.config.PairRef

trait ParticipantRestClientFactory {

  def supportsAddress(address: String) = address.startsWith("http://") || address.startsWith("https://")
}

class ScanningParticipantRestClientFactory(credentialsLookup:DomainCredentialsLookup, limits: PairServiceLimitsView)
  extends ParticipantRestClientFactory {
  def supports(endpoint: Endpoint) = supportsAddress(endpoint.scanUrl)

  def createParticipantRef(endpoint: Endpoint, pairRef:PairRef) = {
    val connectTimeout = limits.getEffectiveLimitByNameForPair(pairRef.space, pairRef.name, ScanConnectTimeout)
    val readTimeout =limits.getEffectiveLimitByNameForPair(pairRef.space, pairRef.name, ScanReadTimeout)

    val client = new ApacheHttpClient(connectTimeout, readTimeout)
    val validatorFactory = new CollationOrderEntityValidatorFactory(endpoint.lookupCollation)

    val parser = new ValidatingScanResultParser(validatorFactory) with LengthCheckingParser  {
      val serviceLimitsView = limits
      val pair = pairRef
    }
    new ScanParticipantRestClient(pairRef, endpoint.scanUrl, credentialsLookup, client, parser)
  }
}

object ScanningParticipantRestClientFactory {
  def create(pair: PairRef, endpoint: Endpoint, serviceLimitsView: PairServiceLimitsView, credentialsLookup: DomainCredentialsLookup) =
      new ScanningParticipantRestClientFactory(credentialsLookup, serviceLimitsView).createParticipantRef(endpoint, pair)
}

class ContentParticipantRestClientFactory(credentialsLookup:DomainCredentialsLookup, limits: PairServiceLimitsView)
  extends  ParticipantRestClientFactory {
  def supports(endpoint: Endpoint) = supportsAddress(endpoint.contentRetrievalUrl)

  def createParticipantRef(endpoint: Endpoint, pair:PairRef)
    = new ContentParticipantRestClient(serviceLimitsView = limits,
                                       scanUrl = endpoint.contentRetrievalUrl,
                                       credentialsLookup = credentialsLookup,
                                       pair = pair)
}

class VersioningParticipantRestClientFactory(credentialsLookup:DomainCredentialsLookup, limits: PairServiceLimitsView)
  extends ParticipantRestClientFactory {
  def supports(endpoint: Endpoint) = supportsAddress(endpoint.versionGenerationUrl)

  def createParticipantRef(endpoint: Endpoint, pair:PairRef)
    = new VersioningParticipantRestClient(serviceLimitsView = limits,
                                          scanUrl = endpoint.versionGenerationUrl,
                                          credentialsLookup = credentialsLookup,
                                          pair = pair)
}