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

package net.lshift.diffa.kernel.config

import net.lshift.diffa.kernel.util.SessionHelper._
import net.lshift.diffa.kernel.frontend.FrontendConversions._
import org.hibernate.{Session, SessionFactory}
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.config.{Pair => DiffaPair}
import net.lshift.diffa.kernel.util.HibernateQueryUtils
import net.lshift.diffa.kernel.frontend._

class HibernateDomainConfigStore(val sessionFactory: SessionFactory, pairCache:PairCache)
    extends DomainConfigStore
    with HibernateQueryUtils {

  def createOrUpdateEndpoint(domainName:String, e: EndpointDef) : Endpoint = sessionFactory.withSession(s => {

    pairCache.invalidate(domainName)

    val domain = getDomain(domainName)
    val endpoint = fromEndpointDef(domain, e)
    s.saveOrUpdate(endpoint)

    // Update the view definitions
    val existingViews = listEndpointViews(s, domainName, e.name)
    val viewsToRemove = existingViews.filter(existing => e.views.find(v => v.name == existing.name).isEmpty)
    viewsToRemove.foreach(r => s.delete(r))
    e.views.foreach(v => s.saveOrUpdate(fromEndpointViewDef(endpoint, v)))

    endpoint
  })

  def deleteEndpoint(domain:String, name: String): Unit = sessionFactory.withSession(s => {

    pairCache.invalidate(domain)

    val endpoint = getEndpoint(s, domain, name)

    // TODO (see ticket #324) Delete children manually - Hibernate can't cascade on delete without a one-to-many relationship,
    // which would create an infinite loop in computing the hashCode of pairs
    s.createQuery("FROM Pair WHERE upstream = :endpoint OR downstream = :endpoint").
            setEntity("endpoint", endpoint).list.foreach(p => deletePairInSession(s, domain, p.asInstanceOf[DiffaPair]))

    endpoint.views.foreach(s.delete(_))

    s.delete(endpoint)
  })

  def listEndpoints(domain:String): Seq[EndpointDef] = sessionFactory.withSession(s => {
    listQuery[Endpoint](s, "endpointsByDomain", Map("domain_name" -> domain)).map(toEndpointDef(_))
  })

  def createOrUpdateRepairAction(domain:String, a: RepairActionDef) = sessionFactory.withSession(s => {
    val pair = getPair(s, domain, a.pair)
    s.saveOrUpdate(fromRepairActionDef(pair, a))
  })

  def deleteRepairAction(domain:String, name: String, pairKey: String) {
    sessionFactory.withSession(s => {
      val action = getRepairAction(s, domain, name, pairKey)
      s.delete(action)
    })
  }

  def createOrUpdatePair(domain:String, p: PairDef): Unit = sessionFactory.withSession(s => {
    p.validate()

    pairCache.invalidate(domain)

    val up = getEndpoint(s, domain, p.upstreamName)
    val down = getEndpoint(s, domain, p.downstreamName)
    val dom = getDomain(domain)
    val toUpdate = new Pair(p.key, dom, up, down, p.versionPolicyName, p.matchingTimeout, p.scanCronSpec, p.allowManualScans)
    s.saveOrUpdate(toUpdate)

    // Update the view definitions
    val existingViews = listPairViews(s, domain, p.key)
    val viewsToRemove = existingViews.filter(existing => p.views.find(v => v.name == existing.name).isEmpty)
    viewsToRemove.foreach(r => s.delete(r))
    p.views.foreach(v => s.saveOrUpdate(fromPairViewDef(toUpdate, v)))
  })

  def deletePair(domain:String, key: String): Unit = sessionFactory.withSession(s => {

    pairCache.invalidate(domain)

    val pair = getPair(s, domain, key)
    deletePairInSession(s, domain, pair)
  })

  // TODO This read through cache should not be necessary when the 2L cache miss issue is resolved
  def listPairs(domain:String) = pairCache.readThrough(domain, () => listPairsFromPersistence(domain))

  def listPairsFromPersistence(domain:String) = sessionFactory.withSession(s => listQuery[Pair](s, "pairsByDomain", Map("domain_name" -> domain)).map(toPairDef(_)))

  def listPairsForEndpoint(domain:String, endpoint:String) = sessionFactory.withSession(s =>
    listQuery[Pair](s, "pairsByEndpoint", Map("domain_name" -> domain, "endpoint_name" -> endpoint)))

  def listRepairActionsForPair(domain:String, pairKey: String) : Seq[RepairActionDef] =
    sessionFactory.withSession(s => {
      getRepairActionsInPair(s, domain, pairKey).map(toRepairActionDef(_))
    })

  def listEscalations(domain:String) = sessionFactory.withSession(s => {
    listQuery[Escalation](s, "escalationsByDomain", Map("domain_name" -> domain)).map(toEscalationDef(_))
  })

  def deleteEscalation(domain:String, name: String, pairKey: String) = {
    sessionFactory.withSession(s => {
      val escalation = getEscalation(s, domain, name, pairKey)
      s.delete(escalation)
    })
  }

  def createOrUpdateEscalation(domain:String, e: EscalationDef) = sessionFactory.withSession( s => {
    val pair = getPair(s, domain, e.pair)
    val escalation = fromEscalationDef(pair,e)
    s.saveOrUpdate(escalation)
  })

  def listEscalationsForPair(domain:String, pairKey: String) : Seq[EscalationDef] =
    sessionFactory.withSession(s => getEscalationsForPair(s, domain, pairKey).map(toEscalationDef(_)))

  def listReports(domain:String) = sessionFactory.withSession(s => {
    listQuery[PairReport](s, "reportsByDomain", Map("domain_name" -> domain)).map(toPairReportDef(_))
  })

  def deleteReport(domain:String, name: String, pairKey: String) = {
    sessionFactory.withSession(s => {
      val report = getReport(s, domain, name, pairKey)
      s.delete(report)
    })
  }

  def createOrUpdateReport(domain:String, r: PairReportDef) = sessionFactory.withSession( s => {
    val pair = getPair(s, domain, r.pair)
    val report = fromPairReportDef(pair, r)
    s.saveOrUpdate(report)
  })

  def listReportsForPair(domain:String, pairKey: String) : Seq[PairReportDef] =
    sessionFactory.withSession(s => getReportsForPair(s, domain, pairKey).map(toPairReportDef(_)))

  private def getRepairActionsInPair(s: Session, domain:String, pairKey: String): Seq[RepairAction] =
    listQuery[RepairAction](s, "repairActionsByPair", Map("pair_key" -> pairKey,
                                                          "domain_name" -> domain))

  private def getEscalationsForPair(s: Session, domain:String, pairKey:String): Seq[Escalation] =
    listQuery[Escalation](s, "escalationsByPair", Map("pair_key" -> pairKey,
                                                      "domain_name" -> domain))

  private def getReportsForPair(s: Session, domain:String, pairKey:String): Seq[PairReport] =
    listQuery[PairReport](s, "reportsByPair", Map("pair_key" -> pairKey,
                                                  "domain_name" -> domain))

  def listRepairActions(domain:String) : Seq[RepairActionDef] = sessionFactory.withSession(s =>
    listQuery[RepairAction](s, "repairActionsByDomain", Map("domain_name" -> domain)).map(toRepairActionDef(_)))

  /*
  def getPairsForEndpoint(domain:String, epName:String):Seq[Pair] = sessionFactory.withSession(s => {
    val q = s.createQuery("SELECT p FROM Pair p WHERE p.upstream.name = :epName OR p.downstream.name = :epName")
    q.setParameter("epName", epName)

    q.list.map(_.asInstanceOf[Pair]).toSeq
  })
  */

  def getEndpointDef(domain:String, name: String) = sessionFactory.withSession(s => toEndpointDef(getEndpoint(s, domain, name)))
  def getPairDef(domain:String, key: String) = sessionFactory.withSession(s => toPairDef(getPair(s, domain, key)))
  def getRepairActionDef(domain:String, name: String, pairKey: String) = sessionFactory.withSession(s => toRepairActionDef(getRepairAction(s, domain, name, pairKey)))
  def getPairReportDef(domain:String, name: String, pairKey: String) = sessionFactory.withSession(s => toPairReportDef(getReport(s, domain, name, pairKey)))

  def allConfigOptions(domain:String) = {
    sessionFactory.withSession(s => {
      listQuery[ConfigOption](s, "configOptionsByDomain", Map("domain_name" -> domain)).map(opt => opt.key -> opt.value).toMap
    })
  }

  def maybeConfigOption(domain:String, key:String) =
    sessionFactory.withSession(s => singleQueryOpt[String](s, "configOptionByNameAndKey", Map("key" -> key, "domain_name" -> domain)))

  def configOptionOrDefault(domain:String, key: String, defaultVal: String) =
    maybeConfigOption(domain, key) match {
      case Some(str) => str
      case None      => defaultVal
    }

  def setConfigOption(domain:String, key:String, value:String) = writeConfigOption(domain, key, value)
  def clearConfigOption(domain:String, key:String) = deleteConfigOption(domain, key)

  private def deletePairInSession(s:Session, domain:String, pair:DiffaPair) = {
    getRepairActionsInPair(s, domain, pair.key).foreach(s.delete)
    getEscalationsForPair(s, domain, pair.key).foreach(s.delete)
    getReportsForPair(s, domain, pair.key).foreach(s.delete)
    pair.views.foreach(s.delete(_))
    deleteStoreCheckpoint(pair.asRef)
    s.delete(pair)
  }

  def makeDomainMember(domain:String, userName:String) = sessionFactory.withSession(s => {
    val member = Member(User(name = userName), Domain(name = domain))
    s.saveOrUpdate(member)
    member
  })

  def removeDomainMembership(domain:String, userName:String) = sessionFactory.withSession(s => {
    s.delete(Member(User(name = userName), Domain(name = domain)))
  })

  def listDomainMembers(domain:String) = sessionFactory.withSession(s => {
    listQuery[Member](s, "membersByDomain", Map("domain_name" -> domain))
  })

  def listEndpointViews(s:Session, domain:String, endpointName:String) =
    listQuery[EndpointView](s, "endpointViewsByEndpoint", Map("domain_name" -> domain, "endpoint_name" -> endpointName))
  def listPairViews(s:Session, domain:String, pairKey:String) =
    listQuery[PairView](s, "pairViewsByPair", Map("domain_name" -> domain, "pair_key" -> pairKey))
}
