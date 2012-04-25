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
                    val supervisor:ActivePairManager,
                    val differencesManager: DifferencesManager,
                    val endpointListener: EndpointLifecycleListener,
                    val scanScheduler: ScanScheduler,
                    val diagnostics: DiagnosticsManager,
                    val pairPolicyClient: PairPolicyClient) {

  private val log:Logger = LoggerFactory.getLogger(getClass)

  def applyConfiguration(domain:String, diffaConfig:DiffaConfig) {

    // Ensure that the configuration is valid upfront
    diffaConfig.validate()
    
    // Apply configuration updates
    val removedProps = configStore.allConfigOptions(domain).keys.filter(currK => !diffaConfig.properties.contains(currK))
    removedProps.foreach(p => configStore.clearConfigOption(domain, p))
    diffaConfig.properties.foreach { case (k, v) => configStore.setConfigOption(domain, k, v) }

    // Remove missing members, and create/update the rest
    val removedMembers = configStore.listDomainMembers(domain).filter(m => diffaConfig.members.find(newM => newM == m.user.name).isEmpty)
    removedMembers.foreach(m => configStore.removeDomainMembership(domain,m.user.name))
    diffaConfig.members.foreach(configStore.makeDomainMember(domain,_))

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
        members = configStore.listDomainMembers(domain).map(_.user.name).toSet,
        endpoints = configStore.listEndpoints(domain).toSet,
        pairs = configStore.listPairs(domain).toSet,
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
    log.debug("[%s] Processing endpoint declare/update request: %s".format(domain, endpointDef.name))
    endpointDef.validate()

    // Ensure that the data stored for each pair can be upgraded.
    try {
      val existing = configStore.getEndpointDef(domain, endpointDef.name)
      val changes = CategoryUtil.differenceCategories(existing.categories.toMap, endpointDef.categories.toMap)

      if (changes.length > 0) {
        configStore.listPairsForEndpoint(domain, endpointDef.name).foreach(p => {
          versionCorrelationStoreFactory(p.asRef).ensureUpgradeable(p.whichSide(existing), changes)
        })
      }
    } catch {
      case mo:MissingObjectException => // Ignore. The endpoint didn't previously exist
    }

    val endpoint = configStore.createOrUpdateEndpoint(domain, endpointDef)
    endpointListener.onEndpointAvailable(endpoint)

    // Inform each related pair that it has been updated
    if (restartPairs) {
      configStore.listPairsForEndpoint(domain, endpoint.name).foreach(notifyPairUpdate(_))
    }
  }

  def deleteEndpoint(domain:String, endpoint: String) = {
    log.debug("[%s] Processing endpoint delete request: %s".format(domain,endpoint))
    configStore.deleteEndpoint(domain, endpoint)
    endpointListener.onEndpointRemoved(domain, endpoint)
  }

  def listPairs(domain:String) : Seq[PairDef] = configStore.listPairs(domain)
  def listEndpoints(domain:String) : Seq[EndpointDef] = configStore.listEndpoints(domain)
  def listUsers(domain:String) : Seq[User] = systemConfigStore.listUsers

  // TODO There is no particular reason why these are just passed through
  // basically the value of this Configuration frontend is that the matching Manager
  // is invoked when you perform CRUD ops for pairs
  // This might have to get refactored in light of the fact that we are now pretty much
  // just using REST to configure the agent
  def getEndpointDef(domain:String, x:String) = configStore.getEndpointDef(domain, x)
  def getPairDef(domain:String, x:String) = configStore.getPairDef(domain, x)
  def getPair(domain:String, x:String) = systemConfigStore.getPair(domain, x)
  def getUser(x:String) = systemConfigStore.getUser(x)

  def createOrUpdateUser(domain:String, u: User): Unit = {
    log.debug("Processing user declare/update request: %s".format(u))
    systemConfigStore.createOrUpdateUser(u)
  }

  def deleteUser(name: String): Unit = {
    log.debug("Processing user delete request: %s".format(name))
    systemConfigStore.deleteUser(name)
  }
  /*
  * Pair CRUD
  * */
  def declarePair(domain:String, pairDef: PairDef): Unit = createOrUpdatePair(domain, pairDef)

  def createOrUpdatePair(domain:String, pairDef: PairDef): Unit = {

    val pairRef = DiffaPairRef(pairDef.key, domain)
    log.info("%s -> Processing pair declare/update request ....".format(pairRef))

    pairDef.validate(null, configStore.listEndpoints(domain).toSet)
    configStore.createOrUpdatePair(domain, pairDef)
    withCurrentPair(domain, pairDef.key, notifyPairUpdate(_))

    log.info("%s -> Completed pair declare/update request".format(pairRef))
  }

  def deletePair(domain:String, key: String): Unit = {

    val pairRef = DiffaPairRef(key,domain)
    log.info("%s -> Processing pair delete request ...".format(pairRef))

    withCurrentPair(domain, key, (p:DiffaPair) => {
      val pair = p.asRef
      log.info("%s -> Stopping pair actor  (%s)".format(pair, benchmark( () => supervisor.stopActor(pair)) ))
      log.info("%s -> Removing from matching manager (%s)".format(pair, benchmark( () => matchingManager.onDeletePair(p)) ))
      log.info("%s -> Removing from correlation store (%s)".format(pair, benchmark( () => versionCorrelationStoreFactory.remove(pair)) ))
      log.info("%s -> Unregistering from scheduler (%s)".format(pair, benchmark( () => scanScheduler.onDeletePair(p)) ))
      log.info("%s -> Removing from differences store (%s)".format(pair, benchmark( () => differencesManager.onDeletePair(pair)) ))
      log.info("%s -> Unregistering from diagnostics (%s)".format(pair, benchmark( () => diagnostics.onDeletePair(pair)) ))
    })

    serviceLimitsStore.deletePairLimitsByDomain(domain)
    configStore.deletePair(domain, key)

    log.info("%s -> Completed pair delete request".format(pairRef))
  }

  private def benchmark(f:() => Unit) : Period = {
    val start = new DateTime()
    f()
    val end = new DateTime()
    new Interval(start,end).toPeriod()
  }

  /**
   * This will execute the lambda if the pair exists. If the pair does not exist
   * this will return normally.
   * @see withCurrentPair
   */
  def maybeWithPair(domain:String, pairKey: String, f:Function1[DiffaPair,Unit]) = {
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
  def withCurrentPair(domain:String, pairKey: String, f:Function1[DiffaPair,Unit]) = {
    val current = systemConfigStore.getPair(domain, pairKey)
    f(current)
  }

  def declareRepairAction(domain:String, action: RepairActionDef) {
    createOrUpdateRepairAction(domain, action)
  }

  def createOrUpdateRepairAction(domain:String, action: RepairActionDef) {
    log.debug("Processing repair action declare/update request: " + action.name)
    action.validate()
    configStore.createOrUpdateRepairAction(domain, action)
  }

  def deleteRepairAction(domain:String, name: String, pairKey: String) {
    log.debug("Processing repair action delete request: (name="+name+", pairKey="+pairKey+")")
    configStore.deleteRepairAction(domain, name, pairKey)
  }

  def listRepairActions (domain:String) : Seq[RepairActionDef] = {
    log.debug("Processing repair action list request")
    configStore.listRepairActions(domain)
  }

  def listRepairActionsForPair(domain:String, pairKey: String): Seq[RepairActionDef] = {
    configStore.listRepairActionsForPair(domain, pairKey)
  }

  def createOrUpdateEscalation(domain:String, escalation: EscalationDef) {
    log.debug("Processing escalation declare/update request: " + escalation.name)
    escalation.validate()
    configStore.createOrUpdateEscalation(domain, escalation)
  }

  def deleteEscalation(domain:String, name: String, pairKey: String) {
    log.debug("Processing escalation delete request: (name="+name+", pairKey="+pairKey+")")
    configStore.deleteEscalation(domain, name, pairKey)
  }

  def listEscalations(domain:String) : Seq[EscalationDef] = {
    log.debug("Processing escalation list request")
    configStore.listEscalations(domain)
  }

  def listEscalationForPair(domain:String, pairKey: String): Seq[EscalationDef] = {
    configStore.listEscalationsForPair(domain, pairKey)
  }

  def deleteReport(domain:String, name: String, pairKey: String) {
    log.debug("Processing report delete request: (name="+name+", pairKey="+pairKey+")")
    configStore.deleteReport(domain, name, pairKey)
  }

  def createOrUpdateReport(domain:String, report: PairReportDef) {
    log.debug("Processing report declare/update request: " + report.name)
    report.validate()
    configStore.createOrUpdateReport(domain, report)
  }

  def makeDomainMember(domain:String, userName:String) = configStore.makeDomainMember(domain,userName)
  def removeDomainMembership(domain:String, userName:String) = configStore.removeDomainMembership(domain, userName)
  def listDomainMembers(domain:String) = configStore.listDomainMembers(domain)

  def notifyPairUpdate(p:DiffaPair) {
    val pairRef = p.asRef

    log.info("%s -> Starting pair actor  (%s)".format(pairRef, benchmark( () => supervisor.startActor(p)) ))
    log.info("%s -> Adding to matching manager (%s)".format(pairRef, benchmark( () => matchingManager.onUpdatePair(p)) ))
    log.info("%s -> Adding to differences store (%s)".format(pairRef, benchmark( () => differencesManager.onUpdatePair(pairRef)) ))
    log.info("%s -> Registering with scheduler (%s)".format(pairRef, benchmark( () => scanScheduler.onUpdatePair(p)) ))
    log.info("%s -> Running initial difference (%s)".format(pairRef, benchmark( () => pairPolicyClient.difference(pairRef)) ))
  }
}
