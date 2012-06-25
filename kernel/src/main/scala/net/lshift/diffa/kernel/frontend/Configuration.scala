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

package net.lshift.diffa.kernel.frontend

import org.slf4j.{Logger, LoggerFactory}
import net.lshift.diffa.kernel.config._
import limits.ValidServiceLimits
import net.lshift.diffa.kernel.matching.MatchingManager
import net.lshift.diffa.kernel.differencing.{DifferencesManager, VersionCorrelationStoreFactory}
import net.lshift.diffa.kernel.participants.EndpointLifecycleListener
import net.lshift.diffa.kernel.scheduler.ScanScheduler
import system.SystemConfigStore
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.actors.{PairPolicyClient, ActivePairManager}
import org.joda.time.{Interval, DateTime, Period}
import net.lshift.diffa.kernel.util.{CategoryUtil, MissingObjectException}
import scala.collection.JavaConversions._

class Configuration(val configStore: DomainConfigStore,
                    val systemConfigStore: SystemConfigStore,
                    val serviceLimitsStore: ServiceLimitsStore,
                    val matchingManager: MatchingManager,
                    val versionCorrelationStoreFactory: VersionCorrelationStoreFactory,
                    val supervisors:java.util.List[ActivePairManager],
                    val differencesManager: DifferencesManager,
                    val endpointListener: EndpointLifecycleListener,
                    val scanScheduler: ScanScheduler,
                    val diagnostics: DiagnosticsManager,
                    val pairPolicyClient: PairPolicyClient) {

  private val log:Logger = LoggerFactory.getLogger(getClass)

  /**
   * This is used to re-assert the state of a domain's configuration.
   * You can optionally supply a calling user - if this is supplied, then if that user accidentally
   * asserts a set of domain members that doesn't include themselves, they will not get deleted (or  re-added)
   * as part of the config application. This is to prevent a non-superuser domain member from inadvertently locking themselves
   * out of the domain they are attempting to configure.
   */
  def applyConfiguration(domain:String, diffaConfig:DiffaConfig, callingUser:Option[String] = None) {

    // Ensure that the configuration is valid upfront
    diffaConfig.validate()
    
    // Apply configuration updates
    val removedProps = configStore.allConfigOptions(domain).keys.filter(currK => !diffaConfig.properties.contains(currK))
    removedProps.foreach(p => configStore.clearConfigOption(domain, p))
    diffaConfig.properties.foreach { case (k, v) => configStore.setConfigOption(domain, k, v) }

    // Remove missing members, and create/update the rest
    val removedMembers = configStore.listDomainMembers(domain).filter(m => diffaConfig.members.find(newM => newM == m.user).isEmpty)
    removedMembers.foreach(m => {
      val userToRemove = m.user
      callingUser match {
        case Some(currentUser) if userToRemove == currentUser => // don't this guy kill himself
        case _                                                => configStore.removeDomainMembership(domain, userToRemove)
      }
    })

    diffaConfig.members.foreach(member => {
      callingUser match {
        case Some(currentUser) if member == currentUser => // this guy must already be there
        case _                                          => configStore.makeDomainMember(domain, member)
      }
    })

    // Apply endpoint and pair updates
    diffaConfig.endpoints.foreach(createOrUpdateEndpoint(domain, _, false))   // Don't restart pairs - that'll happen in the next step
    diffaConfig.pairs.foreach(p => createOrUpdatePair(domain, p))

    // Remove missing repair actions, and create/update the rest
    val removedActions =
      configStore.listRepairActions(domain).filter(a => diffaConfig.repairActions
        .find(newA => newA.name == a.name && newA.pair == a.pair).isEmpty)
    removedActions.foreach(a => deleteRepairAction(domain, a.name, a.pair))
    diffaConfig.repairActions.foreach(createOrUpdateRepairAction(domain,_))
      
    // Remove missing escalations, and create/update the rest
    val removedEscalations =
      configStore.listEscalations(domain).filter(e => diffaConfig.escalations
        .find(newE => newE.name == e.name && newE.pair == e.pair).isEmpty)
    removedEscalations.foreach(e => deleteEscalation(domain, e.name, e.pair))
    diffaConfig.escalations.foreach(createOrUpdateEscalation(domain,_))

    // Remove missing reports, and create/update the rest
    val removedReports =
      configStore.listReports(domain).filter(r => diffaConfig.reports
        .find(newR => newR.name == r.name && newR.pair == r.pair).isEmpty)
    removedReports.foreach(r => deleteReport(domain, r.name, r.pair))
    diffaConfig.reports.foreach(createOrUpdateReport(domain,_))

    // Remove old pairs and endpoints
    val removedPairs = configStore.listPairs(domain).filter(currP => diffaConfig.pairs.find(newP => newP.key == currP.key).isEmpty)
    removedPairs.foreach(p => deletePair(domain, p.key))
    val removedEndpoints = configStore.listEndpoints(domain).filter(currE => diffaConfig.endpoints.find(newE => newE.name == currE.name).isEmpty)
    removedEndpoints.foreach(e => deleteEndpoint(domain, e.name))
  }

  def doesDomainExist(domain: String) = systemConfigStore.doesDomainExist(domain)

  def retrieveConfiguration(domain:String) : Option[DiffaConfig] =
    if (doesDomainExist(domain))
      Some(DiffaConfig(
        properties = configStore.allConfigOptions(domain),
        members = configStore.listDomainMembers(domain).map(_.user).toSet,
        endpoints = configStore.listEndpoints(domain).toSet,
        pairs = configStore.listPairs(domain).map(_.withoutDomain).toSet,
        repairActions = configStore.listRepairActions(domain).map(
          a => RepairActionDef(a.name, a.url, a.scope, a.pair)).toSet,
        escalations = configStore.listEscalations(domain).toSet,
        reports = configStore.listReports(domain).toSet
      ))
    else
      None

  def clearDomain(domain:String) {
    applyConfiguration(domain, DiffaConfig())
  }

  /*
  * Endpoint CRUD
  * */
  def declareEndpoint(domain:String, endpoint: EndpointDef): Unit = createOrUpdateEndpoint(domain, endpoint)

  def createOrUpdateEndpoint(domain:String, endpointDef: EndpointDef, restartPairs:Boolean = true) = {

    endpointDef.validate()

    // Ensure that the data stored for each pair can be upgraded.
    try {
      val existing = configStore.getEndpointDef(domain, endpointDef.name)
      val changes = CategoryUtil.differenceCategories(existing.categories.toMap, endpointDef.categories.toMap)

      if (changes.length > 0) {
        configStore.listPairsForEndpoint(domain, endpointDef.name).foreach(p => {
          versionCorrelationStoreFactory(p.asRef).ensureUpgradeable(p.withoutDomain.whichSide(existing), changes)
        })
      }
    } catch {
      case mo:MissingObjectException => // Ignore. The endpoint didn't previously exist
    }

    val endpoint = configStore.createOrUpdateEndpoint(domain, endpointDef)
    endpointListener.onEndpointAvailable(endpoint)

    // Inform each related pair that it has been updated
    if (restartPairs) {
      configStore.listPairsForEndpoint(domain, endpoint.name).foreach(p => notifyPairUpdate(p.asRef))
    }
  }

  def deleteEndpoint(domain:String, endpoint: String) = {
    configStore.deleteEndpoint(domain, endpoint)
    endpointListener.onEndpointRemoved(domain, endpoint)
  }

  def listPairs(domain:String) : Seq[PairDef] = configStore.listPairs(domain).map(_.withoutDomain)
  def listEndpoints(domain:String) : Seq[EndpointDef] = configStore.listEndpoints(domain)
  def listUsers(domain:String) : Seq[User] = systemConfigStore.listUsers

  // TODO There is no particular reason why these are just passed through
  // basically the value of this Configuration frontend is that the matching Manager
  // is invoked when you perform CRUD ops for pairs
  // This might have to get refactored in light of the fact that we are now pretty much
  // just using REST to configure the agent
  def getEndpointDef(domain:String, x:String) = configStore.getEndpointDef(domain, x)
  def getPairDef(domain:String, x:String) : PairDef = configStore.getPairDef(domain, x).withoutDomain
  def getUser(x:String) = systemConfigStore.getUser(x)

  def createOrUpdateUser(domain:String, u: User): Unit = {
    systemConfigStore.createOrUpdateUser(u)
  }

  def deleteUser(name: String): Unit = {
    systemConfigStore.deleteUser(name)
  }
  /*
  * Pair CRUD
  * */
  def declarePair(domain:String, pairDef: PairDef): Unit = createOrUpdatePair(domain, pairDef)

  def createOrUpdatePair(domain:String, pairDef: PairDef): Unit = {
    pairDef.validate(null, configStore.listEndpoints(domain).toSet)
    configStore.createOrUpdatePair(domain, pairDef)
    withCurrentPair(domain, pairDef.key, notifyPairUpdate(_))
  }

  def deletePair(domain:String, key: String): Unit = {

    withCurrentPair(domain, key, (p:DiffaPairRef) => {

      supervisors.foreach(_.stopActor(p))
      matchingManager.onDeletePair(p)
      versionCorrelationStoreFactory.remove(p)
      scanScheduler.onDeletePair(p)
      differencesManager.onDeletePair(p)
      diagnostics.onDeletePair(p)
    })

    serviceLimitsStore.deletePairLimitsByDomain(domain)
    configStore.deletePair(domain, key)
  }

  /**
   * This will execute the lambda if the pair exists. If the pair does not exist
   * this will return normally.
   * @see withCurrentPair
   */
  def maybeWithPair(domain:String, pairKey: String, f:Function1[DiffaPairRef,Unit]) = {
    try {
      withCurrentPair(domain,pairKey,f)
    }
    catch {
      case e:MissingObjectException => // Do nothing, the pair doesn't currently exist
    }
  }

  /**
   * This will execute the lambda if the pair exists.
   * @throws MissingObjectException If the pair does not exist.
   */
  def withCurrentPair(domain:String, pairKey: String, f:Function1[DiffaPairRef,Unit]) = {
    val current = configStore.getPairDef(domain, pairKey).asRef
    f(current)
  }

  def declareRepairAction(domain:String, action: RepairActionDef) {
    createOrUpdateRepairAction(domain, action)
  }

  def createOrUpdateRepairAction(domain:String, action: RepairActionDef) {
    action.validate()
    configStore.createOrUpdateRepairAction(domain, action)
  }

  def deleteRepairAction(domain:String, name: String, pairKey: String) {
    configStore.deleteRepairAction(domain, name, pairKey)
  }

  def listRepairActions (domain:String) : Seq[RepairActionDef] = {
    configStore.listRepairActions(domain)
  }

  def listRepairActionsForPair(domain:String, pairKey: String): Seq[RepairActionDef] = {
    configStore.listRepairActionsForPair(domain, pairKey)
  }

  def createOrUpdateEscalation(domain:String, escalation: EscalationDef) {
    escalation.validate()
    configStore.createOrUpdateEscalation(domain, escalation)
  }

  def deleteEscalation(domain:String, name: String, pairKey: String) {
    configStore.deleteEscalation(domain, name, pairKey)
  }

  def listEscalations(domain:String) : Seq[EscalationDef] = {
    configStore.listEscalations(domain)
  }

  def listEscalationForPair(domain:String, pairKey: String): Seq[EscalationDef] = {
    configStore.listEscalationsForPair(domain, pairKey)
  }

  def deleteReport(domain:String, name: String, pairKey: String) {
    configStore.deleteReport(domain, name, pairKey)
  }

  def createOrUpdateReport(domain:String, report: PairReportDef) {
    report.validate()
    configStore.createOrUpdateReport(domain, report)
  }

  def makeDomainMember(domain:String, userName:String) = configStore.makeDomainMember(domain,userName)
  def removeDomainMembership(domain:String, userName:String) = configStore.removeDomainMembership(domain, userName)
  def listDomainMembers(domain:String) = configStore.listDomainMembers(domain)

  def notifyPairUpdate(p:DiffaPairRef) {
    supervisors.foreach(_.startActor(p))
    matchingManager.onUpdatePair(p)
    differencesManager.onUpdatePair(p)
    scanScheduler.onUpdatePair(p)
    pairPolicyClient.difference(p)
  }

  def getEffectiveDomainLimit(domain:String, limitName:String) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.getEffectiveLimitByNameForDomain(domain, limit)
  }

  def getEffectivePairLimit(pair:DiffaPairRef, limitName:String) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.getEffectiveLimitByNameForPair(pair.domain, pair.key, limit)
  }

  def setHardDomainLimit(domain:String, limitName:String, value:Int) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.setDomainHardLimit(domain, limit, value)
  }

  def setDefaultDomainLimit(domain:String, limitName:String, value:Int) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.setDomainDefaultLimit(domain, limit, value)
  }

  def setPairLimit(pair:DiffaPairRef, limitName:String, value:Int) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.setPairLimit(pair.domain, pair.key, limit, value)
  }

}
