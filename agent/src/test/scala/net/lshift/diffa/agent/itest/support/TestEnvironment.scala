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

package net.lshift.diffa.agent.itest.support

import net.lshift.diffa.kernel.participants.{UpstreamMemoryParticipant, DownstreamMemoryParticipant, UpstreamParticipant, DownstreamParticipant}
import net.lshift.diffa.kernel.client._
import net.lshift.diffa.kernel.util.Placeholders
import org.joda.time.DateTime
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.differencing.AttributesUtil
import net.lshift.diffa.agent.itest.support.TestConstants._
import org.restlet.data.Protocol
import org.restlet.routing.Router
import org.restlet.resource.{ServerResource, Post}
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.schema.servicelimits.ChangeEventRate
import org.restlet.{Application, Component}
import collection.mutable.HashMap
import net.lshift.diffa.agent.client._
import java.util.List
import net.lshift.diffa.participant.scanning.{ScanAggregation, ScanConstraint}
import net.lshift.diffa.kernel.frontend._
import net.lshift.diffa.participant.changes.ChangeEvent
import net.lshift.diffa.kernel.limiting.SystemClock

/**
 * An assembled environment consisting of a downstream and upstream participant. Provides a factory for the
 * various parts, along with convenience methods for making the configuration valid.
 */
class TestEnvironment(val pairKey: String,
                      val participants: Participants,
                      changesClientBuilder: (TestEnvironment, String) => ChangesClient,
                      versionScheme: VersionScheme,
                      inboundURLBuilder:String => String = (_) => null) {
  import TestEnvironment._

  // Keep a tally of the amount of requested resends
  val entityResendTally = new HashMap[String,Int]

  private val repairActionsComponent = {
    val component = new Component
    component.getServers.add(Protocol.HTTP, 8123)
    component.getDefaultHost.attach("/repair", new RepairActionsApplication(entityResendTally))
    component
  }

  def startActionServer() {
    repairActionsComponent.start()
  }

  def stopActionServer() {
    repairActionsComponent.stop()
  }

  def withActionsServer(op: => Unit) {
    try {
      startActionServer()
      op
    }
    finally {
      stopActionServer()
    }
  }

  // Domain
  val domain = TestEnvironment.domain

  // Pair
  val pairRef = DiffaPairRef(key = pairKey, domain = domain.name)

  val serverRoot = TestEnvironment.serverRoot

  // Version Generation
  val versionForUpstream = versionScheme.upstreamVersionGen
  val versionForDownstream = versionScheme.downstreamVersionGen

  // Participants
  val upstreamEpName = pairKey + "-us"
  val downstreamEpName = pairKey + "-ds"
  val upstream = new UpstreamMemoryParticipant(versionScheme.upstreamVersionGen) {
    var queryResponseDelay = 0

    override protected def doQuery(constraints: List[ScanConstraint], aggregations: List[ScanAggregation]) = {
      if (queryResponseDelay > 0)
        Thread.sleep(queryResponseDelay)

      super.doQuery(constraints, aggregations)
    }
  }
  val downstream = new DownstreamMemoryParticipant(versionScheme.upstreamVersionGen, versionScheme.downstreamVersionGen)

  // Clients
  lazy val upstreamChangesClient:ChangesClient = changesClientBuilder(this, upstreamEpName)
  lazy val downstreamChangesClient:ChangesClient = changesClientBuilder(this, downstreamEpName)

  lazy val configurationClient:ConfigurationRestClient = TestEnvironment.configurationClient
  lazy val diffClient:DifferencesRestClient = TestEnvironment.diffClient
  lazy val actionsClient:ActionsClient = TestEnvironment.actionsClient
  lazy val escalationsClient:EscalationsRestClient = TestEnvironment.escalationsClient
  lazy val usersClient:SecurityRestClient = TestEnvironment.usersClient
  lazy val scanningClient:ScanningRestClient = TestEnvironment.scanningClient
  lazy val systemConfig = TestEnvironment.systemConfig
  lazy val inventoryClient = TestEnvironment.inventoryClient


  // Helpers
  val differencesHelper = new DifferencesHelper(pairKey, diffClient)

  // Actions
  val entityScopedActionName = "Resend Source"
  val entityScopedActionUrl = "http://localhost:8123/repair/resend/{id}"
  val pairScopedActionName = "Resend All"
  val pairScopedActionUrl = "http://localhost:8123/repair/resend-all"

  // Escalations
  val escalationName = "Repair By Resending"

  // Categories
  val categories = Map("someDate" -> new RangeCategoryDescriptor("datetime"), "someString" -> new SetCategoryDescriptor(Set("ss", "tt")))

  val views = Seq(EndpointViewDef(name = "tt-only", categories = Map("someString" -> new SetCategoryDescriptor(Set("tt")))))

  // Participants' RPC server setup
  participants.startUpstreamServer(upstream, upstream)
  participants.startDownstreamServer(downstream, downstream, downstream)

  // Ensure that the configuration exists
  systemConfig.declareDomain(domain)
  configurationClient.declareEndpoint(EndpointDef(name = upstreamEpName,
    scanUrl = participants.upstreamScanUrl, contentRetrievalUrl = participants.upstreamContentUrl,
    inboundUrl = inboundURLBuilder(upstreamEpName),
    categories = categories,
    views = views))
  configurationClient.declareEndpoint(EndpointDef(name = downstreamEpName,
    scanUrl = participants.downstreamScanUrl, contentRetrievalUrl = participants.downstreamContentUrl,
    versionGenerationUrl = participants.downstreamVersionUrl,
    inboundUrl = inboundURLBuilder(downstreamEpName),
    categories = categories,
    views = views))

  createPair

  configurationClient.declareRepairAction(entityScopedActionName, entityScopedActionUrl, RepairAction.ENTITY_SCOPE, pairKey)
  configurationClient.declareEscalation(escalationName, pairKey, entityScopedActionName, EscalationActionType.REPAIR, EscalationEvent.DOWNSTREAM_MISSING, EscalationOrigin.SCAN)


  def createPair = configurationClient.declarePair(PairDef(key = pairKey,
    versionPolicyName = versionScheme.policyName,
    matchingTimeout = matchingTimeout,
    upstreamName = upstreamEpName, downstreamName = downstreamEpName,
    scanCronSpec = "0 15 10 15 * ?",
    views = Seq(PairViewDef("tt-only"))))
  def deletePair() {
   configurationClient.deletePair(pairKey)
  }
  def createPairScopedAction = configurationClient.declareRepairAction(pairScopedActionName, pairScopedActionUrl, RepairAction.PAIR_SCOPE, pairKey)
  
  val username = "foo"
  val mail = "foo@bar.com"
  usersClient.declareUser(UserDef(name = username,email = mail,superuser=false,password="bar"))
  // Add a user to the domain so that at least 1 mail will be sent
  configurationClient.makeDomainMember(username)

  /**
   * Requests that the environment remove all stored state from the participants.
   */
  def clearParticipants() {
    upstream.clearEntities
    downstream.clearEntities
  }

  // TODO: remove this when limiting can be configured via REST
  private def pauseToAvoidRateLimitingFailure() {
    def timeSince(t: Long) = SystemClock.currentTimeMillis - t

    val t0 = SystemClock.currentTimeMillis
    val minimumPauseInterval = 1000L / ChangeEventRate.defaultLimit

    while (timeSince(t0) < minimumPauseInterval) {
      try {
        Thread.sleep(minimumPauseInterval)
      } catch {
        case _ =>
      }
    }
  }

  def addAndNotifyUpstream(id:String, content:String, someDate:DateTime, someString:String) {
    pauseToAvoidRateLimitingFailure()
    val attributes = pack(someDate = someDate, someString = someString)

    upstream.addEntity(id, someDate, someString, Placeholders.dummyLastUpdated, content)
    upstreamChangesClient.onChangeEvent(ChangeEvent.forChange(id, versionForUpstream(content), Placeholders.dummyLastUpdated, attributes))
  }

  def addAndNotifyDownstream(id:String, content:String, someDate:DateTime, someString:String) {
    pauseToAvoidRateLimitingFailure()
    val attributes = pack(someDate = someDate, someString = someString)

    downstream.addEntity(id, someDate, someString, Placeholders.dummyLastUpdated, content)
    versionScheme match {
      case SameVersionScheme =>
        downstreamChangesClient.onChangeEvent(ChangeEvent.forChange(id, versionForDownstream(content), Placeholders.dummyLastUpdated, attributes))
      case CorrelatedVersionScheme =>
        downstreamChangesClient.onChangeEvent(ChangeEvent.forTriggeredChange(id,
          versionForUpstream(content), versionForDownstream(content), Placeholders.dummyLastUpdated, attributes))
    }
  }

  def pack(someDate:DateTime, someString:String) = Map("someDate" -> someDate.toString(), "someString" -> someString)

}

object TestEnvironment {

  // Domain
  val domain = DomainDef(name="domain")

  def serverRoot = agentURL
  val matchingTimeout = 1  // 1 second


  // Clients
  lazy val configurationClient:ConfigurationRestClient = new ConfigurationRestClient(serverRoot, domain.name)
  lazy val diffClient:DifferencesRestClient = new DifferencesRestClient(serverRoot, domain.name)
  lazy val actionsClient:ActionsClient = new ActionsRestClient(serverRoot, domain.name)
  lazy val escalationsClient:EscalationsRestClient = new EscalationsRestClient(serverRoot, domain.name)
  lazy val usersClient:SecurityRestClient = new SecurityRestClient(serverRoot)
  lazy val scanningClient:ScanningRestClient = new ScanningRestClient(serverRoot, domain.name)
  lazy val systemConfig = new SystemConfigRestClient(serverRoot)
  lazy val inventoryClient = new InventoryRestClient(serverRoot, domain.name)

}

class ResendAllResource extends ServerResource {
  @Post def resendAll = "resending all"
}
class ResendEntityResource extends ServerResource {

  /**
   * Update the resend tally for each entity
   */
  @Post def resend = {
    val entityId = getRequest.getResourceRef.getLastSegment
    val tally = getContext.getAttributes.get("tally").asInstanceOf[HashMap[String,Int]]
    tally.synchronized {
      tally.get(entityId) match {
        case Some(x) => tally(entityId) = x + 1
        case None    => tally(entityId) = 1
      }
      tally.notifyAll
    }
    "resending entity"
  }
}
class RepairActionsApplication(tally:HashMap[String,Int]) extends Application {
  override def createInboundRoot = {

    // Pass the tally to the underlying resource
    // NOTE: This is due to Restlets API - it feels like they should provide a resource that you can constructor inject
    getContext.setAttributes(Map("tally"-> tally))

    val router = new Router(getContext)
    router.attach("/resend-all", classOf[ResendAllResource])
    router.attach("/resend/abc", classOf[ResendEntityResource])
    router
  }
}

abstract class VersionScheme {
  def policyName:String
  def upstreamVersionGen:Function1[String, String]
  def downstreamVersionGen:Function1[String, String]
}
object SameVersionScheme extends VersionScheme {
  val policyName = "same"
  val upstreamVersionGen = (content) => "vsn_" + content
  val downstreamVersionGen = (content) => "vsn_" + content
}
object CorrelatedVersionScheme extends VersionScheme {
  val policyName = "correlated"
  val upstreamVersionGen = (content) => "uvsn_" + content
  val downstreamVersionGen = (content) => "dvsn_" + content
}
