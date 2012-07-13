/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.client

import net.lshift.diffa.kernel.config._
import net.lshift.diffa.participant.scanning._
import javax.servlet.http.HttpServletRequest
import org.junit.experimental.theories.{Theory, Theories, DataPoint}
import net.lshift.diffa.schema.servicelimits.ServiceLimit
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.Assert._
import org.hamcrest.Matchers._
import net.lshift.diffa.kernel.config.DiffaPairRef
import net.lshift.diffa.kernel.config.Endpoint
import scala.collection.JavaConversions._
import java.io.IOException


object ScanOrderVerificationTest {

  val pair = new DiffaPairRef("some-domain", "some-pair")

  val serverPort = 41559

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


  case class Example(ordering: Collation, collationName: String, correctOrdering: Seq[String], invalidOrdering: Seq[String])

  @DataPoint def ascii = Example(AsciiCollationOrdering, "ascii", Seq("B", "a", "c"), Seq("a", "B", "C"))
  @DataPoint def unicode = Example(UnicodeCollationOrdering, "unicode", Seq("a", "B", "c"), Seq("a", "B", "a"))

  def withResponseEntities[T](entityIds: Seq[String])(thunk: => T): T = {
    val oldValue = scanningParticipant.response
    scanningParticipant.response = entityIds map {  id => ScanResultEntry.forEntity(id, "%sv0".format(id), null) }
    try
      thunk
    finally
      scanningParticipant.response = oldValue
  }

  lazy val limits = new PairServiceLimitsView {
    def getEffectiveLimitByNameForPair(domainName: String, pairKey: String, limit: ServiceLimit): Int = limit.defaultLimit
  }

  def scanningRestClientFor(ex: Example) = {
    val endpoint = Endpoint(name = "limitsEndpointFor%s".format(ex.collationName),
      scanUrl = "http://localhost:" + serverPort + "/scan",
      collation = ex.collationName)
    ScanningParticipantRestClientFactory.create(
      pair, endpoint, limits, domainCredentialsLookup)
  }

}

@RunWith(classOf[Theories])
class ScanOrderVerificationTest {
  import ScanOrderVerificationTest._
  @Before def startServer() = ensureServerStarted

  @Theory
  def shouldScanSuccessfullyWhenWellOrderedEntitiesEncountered(ex: Example) {
    withResponseEntities(ex.correctOrdering) {
      assertThat(scanningRestClientFor(ex).scan(Seq(), Seq()).map(_.getId),
        equalTo(ex.correctOrdering))

    }
  }

  @Theory
  def shouldRaiseErrorWhenIllOrderedEntitiesEncountered(ex: Example) {
    try {
      withResponseEntities(ex.invalidOrdering) { scanningRestClientFor(ex).scan(Seq(), Seq()) }
      fail("Scan on ill ordered entities: %s should fail with exception".format(ex.invalidOrdering))
    } catch {
      case ex: OutOfOrderException => assertTrue("Failed correctly", true)
      // When the id order validator is running as a ScanEntityValidator in
      // passed to JSONHelper, then any exceptions it throws will get
      // automagically be wrapped in an IOException. So, because the
      // PairActor (top of the call stack for the scanning code) will treat
      // most IOException and OutOfOrderException equally, it's okay for it
      // to not be explicitly unwrapped.
      case ex: IOException if ex.getCause.isInstanceOf[OutOfOrderException] => assertTrue("Failed correctly", true)
    }
  }
}