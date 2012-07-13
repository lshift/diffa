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

import javax.servlet.http.HttpServletRequest
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.participant.scanning._
import org.junit.{Test, Before}
import net.lshift.diffa.schema.servicelimits.{ScanReadTimeout, ScanConnectTimeout, ScanResponseSizeLimit}
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.differencing.ScanLimitBreachedException
import net.lshift.diffa.kernel.config.DiffaPairRef
import net.lshift.diffa.kernel.config.Endpoint


object ScanLimitsTest {

  val pair = new DiffaPairRef("some-domain", "some-pair")

  val serverPort = 41557

  val domainCredentialsLookup = new FixedDomainCredentialsLookup(pair.domain, None)

  object scanningParticipant extends ScanningParticipantHandler {
    import java.util.List
    var response : List[ScanResultEntry] = Seq[ScanResultEntry]()
    def determineConstraints(req : HttpServletRequest) : List[ScanConstraint] = Seq()
    def determineAggregations(req : HttpServletRequest) : List[ScanAggregation] = Seq ()
    def doQuery(constraints : List[ScanConstraint], aggregations : List[ScanAggregation])  = response
  }

  lazy val server = new ParticipantServer(serverPort, scanningParticipant)

  def ensureServerStarted {
    if (!server.isRunning) server.start()
  }
}

class ScanLimitsTest {
  import ScanLimitsTest._

  val limits = createMock(classOf[PairServiceLimitsView])
  lazy val endpoint = Endpoint(name = "limitsEndpoint", scanUrl = "http://localhost:" + serverPort + "/scan")

  lazy val scanningRestClient = ScanningParticipantRestClientFactory.create(
    pair, endpoint, limits, domainCredentialsLookup)

  @Before def startServer() = ensureServerStarted

/*
 I realize that this isn't a fantastic test, but I've ended up layering
 configuration lookup into the ScanningParticipantRestClientFactory,
 which probably isn't a hugely optimal choice. I could add more indirection
 to verify that the timeouts get passed into the http client, but that's
 maybe not the best choice, and I can't think of a better way to do that
 right now  --CS
*/

  @Test
  def shouldQueryResponseLimitsForPair {
    val arbitrarilyLargeResponseSize = 10 * 1024 * 1024
    configureLimitsWithResponseSizeOf(arbitrarilyLargeResponseSize)
    scanningRestClient.scan(Seq(), Seq())
    verify(limits)
  }

  @Test(expected=classOf[ScanLimitBreachedException])
  def shouldThrowExceptionWhenResponseSizeLimitBreached {
    configureLimitsWithResponseSizeOf(0)
    scanningRestClient.scan(Seq(), Seq())
  }


  def configureLimitsWithResponseSizeOf(responseSize: Int) {
    expect(limits.getEffectiveLimitByNameForPair(pair.domain, pair.key, ScanResponseSizeLimit)).
      andReturn(responseSize).anyTimes()

    List(ScanConnectTimeout, ScanReadTimeout).foreach {
      limit =>
        expect(limits.getEffectiveLimitByNameForPair(pair.domain, pair.key, limit)).
          andReturn(limit.defaultLimit)
    }
    replay(limits)

  }
}