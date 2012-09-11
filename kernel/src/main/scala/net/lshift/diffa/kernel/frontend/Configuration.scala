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
import net.lshift.diffa.kernel.preferences.UserPreferencesStore

class Configuration(val configStore: DomainConfigStore,
                    val systemConfigStore: SystemConfigStore,
                    val serviceLimitsStore: ServiceLimitsStore,
                    val preferencesStore:UserPreferencesStore,
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
  def applyConfiguration(space:Long, diffaConfig:DiffaConfig, callingUser:Option[String] = None) {

    // Ensure that the configuration is valid upfront
    diffaConfig.validate()
    
    // Apply configuration updates
    val removedProps = configStore.allConfigOptions(space).keys.filter(currK => !diffaConfig.properties.contains(currK))
    removedProps.foreach(p => configStore.clearConfigOption(space, p))
    diffaConfig.properties.foreach { case (k, v) => configStore.setConfigOption(space, k, v) }

    // Remove missing members, and create/update the rest
    val existingMembers = listSpaceMembers(space)
    val removedMembers = existingMembers.diff(diffaConfig.members.toSeq)
    removedMembers.foreach(m => {
      if (Some(m.username) != callingUser) {      // Users aren't allowed to remove memberships for themselves
        removeDomainMembership(space, m.username, m.policy)
      }
    })
    val newMembers = diffaConfig.members.toSeq.diff(existingMembers)
    newMembers.foreach(m => {
      if (Some(m.username) != callingUser) {      // Users aren't allowed to add memberships for themselves
        makeDomainMember(space, m.username, m.policy)
      }
    })

    // Apply endpoint and pair updates
    diffaConfig.endpoints.foreach(createOrUpdateEndpoint(space, _, false))   // Don't restart pairs - that'll happen in the next step
    diffaConfig.pairs.foreach(p => createOrUpdatePair(space, p))

    // Remove old pairs and endpoints
    val removedPairs = configStore.listPairs(space).filter(currP => diffaConfig.pairs.find(newP => newP.key == currP.key).isEmpty)
    removedPairs.foreach(p => deletePair(space, p.key))
    val removedEndpoints = configStore.listEndpoints(space).filter(currE => diffaConfig.endpoints.find(newE => newE.name == currE.name).isEmpty)
    removedEndpoints.foreach(e => deleteEndpoint(space, e.name))
  }

  @Deprecated def doesDomainExist(domain: String) = systemConfigStore.doesDomainExist(domain)
  def doesSpaceExist(space: Long) = systemConfigStore.doesSpaceExist(space)

  def lookupSpacePath(path:String) = systemConfigStore.lookupSpaceByPath(path)

  def retrieveConfiguration(space:Long) : Option[DiffaConfig] =
    if (doesSpaceExist(space))
      Some(DiffaConfig(
        properties = configStore.allConfigOptions(space),
        members = listSpaceMembers(space).toSet,
        endpoints = configStore.listEndpoints(space).toSet,
        pairs = configStore.listPairs(space).map(_.withoutDomain).toSet
      ))
    else
      None

  def clearDomain(space:Long) {
    applyConfiguration(space, DiffaConfig())
  }

  /*
  * Endpoint CRUD
  * */
  def declareEndpoint(space:Long, endpoint: EndpointDef): Unit = createOrUpdateEndpoint(space, endpoint)

  def createOrUpdateEndpoint(space:Long, endpointDef: EndpointDef, restartPairs:Boolean = true) = {

    endpointDef.validate()

    // Ensure that the data stored for each pair can be upgraded.
    try {
      val existing = configStore.getEndpointDef(space, endpointDef.name)
      val changes = CategoryUtil.differenceCategories(existing.categories.toMap, endpointDef.categories.toMap)

      if (changes.length > 0) {
        configStore.listPairsForEndpoint(space, endpointDef.name).foreach(p => {
          versionCorrelationStoreFactory(p.asRef).ensureUpgradeable(p.withoutDomain.whichSide(existing), changes)
        })
      }
    } catch {
      case mo:MissingObjectException => // Ignore. The endpoint didn't previously exist
    }

    val endpoint = configStore.createOrUpdateEndpoint(space, endpointDef)
    endpointListener.onEndpointAvailable(endpoint)

    // Inform each related pair that it has been updated
    if (restartPairs) {
      configStore.listPairsForEndpoint(space, endpoint.name).foreach(p => notifyPairUpdate(p.asRef))
    }
  }

  def deleteEndpoint(space:Long, endpoint: String) = {
    configStore.deleteEndpoint(space, endpoint)
    endpointListener.onEndpointRemoved(space, endpoint)
  }

  def listPairs(space:Long) : Seq[PairDef] = configStore.listPairs(space).map(_.withoutDomain)
  def listEndpoints(space:Long) : Seq[EndpointDef] = configStore.listEndpoints(space)
  def listUsers(space:Long) : Seq[User] = systemConfigStore.listUsers

  // TODO There is no particular reason why these are just passed through
  // basically the value of this Configuration frontend is that the matching Manager
  // is invoked when you perform CRUD ops for pairs
  // This might have to get refactored in light of the fact that we are now pretty much
  // just using REST to configure the agent
  def getEndpointDef(space:Long, x:String) = configStore.getEndpointDef(space, x)
  def getPairDef(space:Long, x:String) : PairDef = configStore.getPairDef(space, x).withoutDomain
  def getUser(x:String) = systemConfigStore.getUser(x)

  def createOrUpdateUser(space:Long, u: User): Unit = {
    systemConfigStore.createOrUpdateUser(u)
  }

  def deleteUser(name: String): Unit = {
    systemConfigStore.deleteUser(name)
  }
  /*
  * Pair CRUD
  * */
  def declarePair(space:Long, pairDef: PairDef): Unit = createOrUpdatePair(space, pairDef)

  def createOrUpdatePair(space:Long, pairDef: PairDef) {
    pairDef.validate(null, configStore.listEndpoints(space).toSet)
    configStore.createOrUpdatePair(space, pairDef)
    withCurrentPair(space, pairDef.key, notifyPairUpdate(_))
  }

  def deletePair(space:Long, key: String): Unit = {

    withCurrentPair(space, key, (p:PairRef) => {

      supervisors.foreach(_.stopActor(p))
      matchingManager.onDeletePair(p)
      versionCorrelationStoreFactory.remove(p)
      scanScheduler.onDeletePair(p)
      differencesManager.onDeletePair(p)
      diagnostics.onDeletePair(p)
    })

    serviceLimitsStore.deletePairLimitsByDomain(space)
    configStore.deletePair(space, key)
  }

  /**
   * This will execute the lambda if the pair exists. If the pair does not exist
   * this will return normally.
   * @see withCurrentPair
   */
  def maybeWithPair(space:Long, pairKey: String, f:Function1[PairRef,Unit]) = {
    try {
      withCurrentPair(space,pairKey,f)
    }
    catch {
      case e:MissingObjectException => // Do nothing, the pair doesn't currently exist
    }
  }

  /**
   * This will execute the lambda if the pair exists.
   * @throws MissingObjectException If the pair does not exist.
   */
  def withCurrentPair(space:Long, pairKey: String, f:Function1[PairRef,Unit]) = {
    val current = configStore.getPairDef(space, pairKey).asRef
    f(current)
  }

  def declareRepairAction(space:Long, pairKey:String, action: RepairActionDef) {
    createOrUpdateRepairAction(space, pairKey, action)
  }

  def createOrUpdateRepairAction(space:Long, pairKey:String, action: RepairActionDef) {
    action.validate()
    updatePair(space, pairKey, pair => replaceByName(pair.repairActions, action))
  }

  def deleteRepairAction(space:Long, name: String, pairKey: String) {
    updatePair(space, pairKey, pair => pair.repairActions.retain(_.name != name))
  }

  def clearRepairActions(space:Long, pairKey:String) {
    updatePair(space, pairKey, pair => pair.repairActions.clear())
  }

  def listRepairActionsForPair(space:Long, pairKey: String): Seq[RepairActionDef] = {
    configStore.getPairDef(space, pairKey).repairActions.toSeq
  }

  def createOrUpdateEscalation(space:Long, pairKey:String, escalation: EscalationDef) {
    escalation.validate()
    updatePair(space, pairKey, pair => replaceByName(pair.escalations, escalation))
  }

  def deleteEscalation(space:Long, name: String, pairKey: String) {
    updatePair(space, pairKey, pair => pair.escalations.retain(_.name != name))
  }

  def clearEscalations(space:Long, pairKey:String) {
    updatePair(space, pairKey, pair => pair.escalations.clear())
  }

  def listEscalationForPair(space:Long, pairKey: String): Seq[EscalationDef] = {
    configStore.getPairDef(space, pairKey).escalations.toSeq
  }

  def deleteReport(space:Long, name: String, pairKey: String) {
    updatePair(space, pairKey, pair => pair.reports.retain(_.name != name))
  }

  def createOrUpdateReport(space:Long, pairKey:String, report: PairReportDef) {
    report.validate()
    updatePair(space, pairKey, pair => replaceByName(pair.reports, report))
  }

  def makeDomainMember(space:Long, userName:String, policy:String) {
    val policyKey = configStore.lookupPolicy(space, policy)
    configStore.makeDomainMember(space, userName, policyKey)
  }
  def removeDomainMembership(space:Long, userName:String, policy:String) {
    preferencesStore.removeAllFilteredItemsForDomain(space, userName)
    configStore.removeDomainMembership(space, userName, policy)
  }
  def listSpaceMembers(space:Long) = configStore.listDomainMembers(space).map(m => PolicyMember(m.user, m.policy))

  def notifyPairUpdate(p:PairRef) {
    supervisors.foreach(_.startActor(p))
    matchingManager.onUpdatePair(p)
    differencesManager.onUpdatePair(p)
    scanScheduler.onUpdatePair(p)
    pairPolicyClient.difference(p)
  }

  def getEffectiveDomainLimit(space:Long, limitName:String) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.getEffectiveLimitByNameForDomain(space, limit)
  }

  def getEffectivePairLimit(pair:PairRef, limitName:String) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.getEffectiveLimitByNameForPair(pair.space, pair.name, limit)
  }

  def setHardDomainLimit(space:Long, limitName:String, value:Int) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.setDomainHardLimit(space, limit, value)
  }

  def setDefaultDomainLimit(space:Long, limitName:String, value:Int) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.setDomainDefaultLimit(space, limit, value)
  }

  def setPairLimit(pair:PairRef, limitName:String, value:Int) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.setPairLimit(pair.space, pair.name, limit, value)
  }

  private def updatePair(space:Long, pairKey:String, f:PairDef => Unit) {
    val pair = configStore.getPairDef(space, pairKey).withoutDomain
    f(pair)
    configStore.createOrUpdatePair(space, pair)
  }

  private def replaceByName[T <: {def name:String}](list:java.util.Set[T], obj:T) {
    list.find(_.name == obj.name) match {
      case Some(a) => list.remove(a)
      case None    =>
    }
    list.add(obj)
  }

}
