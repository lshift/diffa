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

import net.lshift.diffa.kernel.util.db.{HibernateQueryUtils, DatabaseFacade}
import net.lshift.diffa.schema.hibernate.SessionHelper._
import net.lshift.diffa.kernel.frontend.FrontendConversions._
import org.hibernate.{Session, SessionFactory}
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.frontend._
import net.lshift.diffa.kernel.hooks.HookManager
import java.util.List
import java.sql.SQLIntegrityConstraintViolationException
import net.lshift.diffa.schema.jooq.{DatabaseFacade => JooqDatabaseFacade}
import net.lshift.diffa.schema.tables.Pair.PAIR
import net.lshift.diffa.schema.tables.PairViews.PAIR_VIEWS
import net.lshift.diffa.schema.tables.Endpoint.ENDPOINT
import net.lshift.diffa.schema.tables.EndpointViews.ENDPOINT_VIEWS
import org.jooq.Record
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.lifecycle.DomainLifecycleAware
import net.lshift.diffa.kernel.util.cache.{KeyPredicate, CacheProvider}
import reflect.BeanProperty
import collection.mutable
import java.util
;

class HibernateDomainConfigStore(val sessionFactory: SessionFactory,
                                 db:DatabaseFacade,
                                 jooq:JooqDatabaseFacade,
                                 hookManager:HookManager,
                                 cacheProvider:CacheProvider,
                                 membershipListener:DomainMembershipAware)
    extends DomainConfigStore
    with DomainLifecycleAware
    with HibernateQueryUtils {

  val hook = hookManager.createDifferencePartitioningHook(sessionFactory)

  private val cachedConfigVersions = cacheProvider.getCachedMap[String,Int]("domain.config.versions")
  private val cachedPairs = cacheProvider.getCachedMap[String, List[DomainPairDef]]("domain.pairs")
  private val cachedPairsByKey = cacheProvider.getCachedMap[DomainPairKey, DomainPairDef]("domain.pairs.by.key")
  private val cachedEndpoints = cacheProvider.getCachedMap[String, List[EndpointDef]]("domain.endpoints")
  private val cachedEndpointsByKey = cacheProvider.getCachedMap[DomainEndpointKey, EndpointDef]("domain.endpoints.by.key")
  private val cachedPairsByEndpoint = cacheProvider.getCachedMap[DomainEndpointKey, List[DomainPairDef]]("domain.pairs.by.endpoint")

  def reset {
    cachedConfigVersions.evictAll()
    cachedPairs.evictAll()
    cachedPairsByKey.evictAll()
    cachedEndpoints.evictAll()
    cachedEndpointsByKey.evictAll()
    cachedPairsByEndpoint.evictAll()
  }

  private def invalidateAllCaches(domain:String) = {
    cachedConfigVersions.evict(domain)
    cachedEndpoints.evict(domain)
    cachedPairs.evict(domain)
    cachedPairsByEndpoint.keySubset(EndpointByDomainPredicate(domain)).evictAll()
    cachedPairsByKey.keySubset(PairByDomainPredicate(domain)).evictAll()
    cachedEndpointsByKey.keySubset(EndpointByDomainPredicate(domain)).evictAll()
  }

  private def invalidateEndpointCachesOnly(domain:String, endpointName: String) = {
    cachedEndpoints.evict(domain)
    cachedPairsByEndpoint.keySubset(PairByDomainAndEndpointPredicate(domain, endpointName)).evictAll()
    cachedEndpointsByKey.evict(DomainEndpointKey(domain,endpointName))

    // TODO This is a very coarse grained invalidation of the pair caches - this could be made finer at some stage
    cachedPairs.evict(domain)
    cachedPairsByKey.keySubset(PairByDomainPredicate(domain)).evictAll()
  }

  private def invalidatePairCachesOnly(domain:String) = {
    cachedPairs.evict(domain)
    cachedPairsByKey.keySubset(PairByDomainPredicate(domain)).evictAll()
    cachedPairsByEndpoint.keySubset(EndpointByDomainPredicate(domain)).evictAll()
  }

  def onDomainUpdated(domain: String) = invalidateAllCaches(domain)
  def onDomainRemoved(domain: String) = invalidateAllCaches(domain)

  def createOrUpdateEndpoint(domainName:String, e: EndpointDef) : Endpoint = withVersionUpgrade(domainName, s => {

    invalidateEndpointCachesOnly(domainName, e.name)

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

  def deleteEndpoint(domain:String, name: String): Unit = withVersionUpgrade(domain, s => {

    invalidateEndpointCachesOnly(domain, name)

    val endpoint = getEndpoint(s, domain, name)

    // Remove all pairs that reference the endpoint
    s.createQuery("FROM DiffaPair WHERE upstream = :endpoint OR downstream = :endpoint").
            setString("endpoint", name).list.foreach(p => deletePairInSession(s, domain, p.asInstanceOf[DiffaPair]))

    endpoint.views.foreach(s.delete(_))

    s.delete(endpoint)
  })

  def listEndpoints(domain:String): Seq[EndpointDef] = cachedEndpoints.readThrough(domain, () => {
    db.listQuery[Endpoint]("endpointsByDomain", Map("domain_name" -> domain)).map(toEndpointDef(_))
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

  def createOrUpdatePair(domain:String, p: PairDef): Unit = {
    withVersionUpgrade(domain, s => {
      p.validate()

      invalidatePairCachesOnly(domain)

      val dom = getDomain(domain)
      val toUpdate = DiffaPair(p.key, dom, p.upstreamName, p.downstreamName, p.versionPolicyName, p.matchingTimeout,
        p.scanCronSpec, p.allowManualScans)
      s.saveOrUpdate(toUpdate)

      // Update the view definitions
      val existingViews = listPairViews(s, domain, p.key)
      val viewsToRemove = existingViews.filter(existing => p.views.find(v => v.name == existing.name).isEmpty)
      viewsToRemove.foreach(r => s.delete(r))
      p.views.foreach(v => s.saveOrUpdate(fromPairViewDef(toUpdate, v)))
    })

    hook.pairCreated(domain, p.key)
  }

  def deletePair(domain:String, key: String) {
    withVersionUpgrade(domain, s => {

      invalidatePairCachesOnly(domain)

      val pair = getPair(s, domain, key)
      deletePairInSession(s, domain, pair)
    })

    hook.pairRemoved(domain, key)
  }

  def listPairs(domain:String) = cachedPairs.readThrough(domain, () => listPairsInternal(domain))

  def listPairsForEndpoint(domain:String, endpoint:String) =
    cachedPairsByEndpoint.readThrough(DomainEndpointKey(domain, endpoint), () => listPairsInternal(domain, Some(endpoint)))

  private def listPairsInternal(domain:String, endpoint:Option[String] = None) : Seq[DomainPairDef] = jooq.execute(t => {


    val baseQuery = t.select(PAIR.getFields).
                      select(PAIR_VIEWS.NAME, PAIR_VIEWS.SCAN_CRON_SPEC).
                      from(PAIR).
                        leftOuterJoin(PAIR_VIEWS).
                          on(PAIR_VIEWS.PAIR.equal(PAIR.PAIR_KEY)).
                          and(PAIR_VIEWS.DOMAIN.equal(PAIR.DOMAIN)).
                      where(PAIR.DOMAIN.equal(domain))

    val query = endpoint match {
      case None       => baseQuery
      case Some(name) => baseQuery.and(PAIR.UPSTREAM.equal(name).or(PAIR.DOWNSTREAM.equal(name)))
    }

    val results = query.fetch()

    val compressed = new mutable.HashMap[String, DomainPairDef]()

    def compressionKey(pairKey:String) = domain + "/" + pairKey

    results.iterator().map(record => {
      val pairKey = record.getValue(PAIR.PAIR_KEY)
      val compressedKey = compressionKey(pairKey)
      val pair = compressed.getOrElseUpdate(compressedKey,
        DomainPairDef(
          domain = record.getValue(PAIR.DOMAIN),
          key = record.getValue(PAIR.PAIR_KEY),
          upstreamName = record.getValue(PAIR.UPSTREAM),
          downstreamName = record.getValue(PAIR.DOWNSTREAM),
          versionPolicyName = record.getValue(PAIR.VERSION_POLICY_NAME),
          scanCronSpec = record.getValue(PAIR.SCAN_CRON_SPEC),
          matchingTimeout = record.getValue(PAIR.MATCHING_TIMEOUT),
          allowManualScans = record.getValue(PAIR.ALLOW_MANUAL_SCANS),
          views = new util.ArrayList[PairViewDef]()
        )
      )

      val viewScanCronSpec = record.getValue(PAIR_VIEWS.SCAN_CRON_SPEC)
      val viewName = record.getValue(PAIR_VIEWS.NAME)

      if (viewName != null) {
        pair.views.add(PairViewDef(
          name = viewName,
          scanCronSpec = viewScanCronSpec
        ))
      }

      pair

    }).toList
  })

  def listRepairActionsForPair(domain:String, pairKey: String) : Seq[RepairActionDef] =
    getRepairActionsInPair(domain, pairKey).map(toRepairActionDef(_))

  def listEscalations(domain:String) =
    db.listQuery[Escalation]("escalationsByDomain", Map("domain_name" -> domain)).map(toEscalationDef(_))

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
    getEscalationsForPair(domain, pairKey).map(toEscalationDef(_))

  def listReports(domain:String) = db.listQuery[PairReport]("reportsByDomain", Map("domain_name" -> domain)).map(toPairReportDef(_))


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

  def listReportsForPair(domain:String, pairKey: String) : Seq[PairReportDef]
    = getReportsForPair(domain, pairKey).map(toPairReportDef(_))

  private def getRepairActionsInPair(domain:String, pairKey: String): Seq[RepairAction] =
    db.listQuery[RepairAction]("repairActionsByPair", Map("pair_key" -> pairKey,
                                                          "domain_name" -> domain))

  private def getEscalationsForPair(domain:String, pairKey:String): Seq[Escalation] =
    db.listQuery[Escalation]("escalationsByPair", Map("pair_key" -> pairKey,
                                                      "domain_name" -> domain))

  private def getReportsForPair(domain:String, pairKey:String): Seq[PairReport] =
    db.listQuery[PairReport]("reportsByPair", Map("pair_key" -> pairKey,
                                                  "domain_name" -> domain))

  def listRepairActions(domain:String) : Seq[RepairActionDef] =
    db.listQuery[RepairAction]("repairActionsByDomain", Map("domain_name" -> domain)).map(toRepairActionDef(_))

  def getEndpointDef(domain:String, name: String) = sessionFactory.withSession(s => toEndpointDef(getEndpoint(s, domain, name)))
  def getEndpoint(domain:String, name: String) = sessionFactory.withSession(s => getEndpoint(s, domain, name))


  def getPairDef(domain:String, key: String) = cachedPairsByKey.readThrough(DomainPairKey(domain,key), () => jooq.execute { t =>

    val result =
      t.select(PAIR.getFields).
        select(PAIR_VIEWS.NAME, PAIR_VIEWS.SCAN_CRON_SPEC).
        from(PAIR).
          leftOuterJoin(PAIR_VIEWS).
            on(PAIR_VIEWS.PAIR.equal(PAIR.PAIR_KEY)).
            and(PAIR_VIEWS.DOMAIN.equal(PAIR.DOMAIN)).
        where(PAIR.DOMAIN.equal(domain).
          and(PAIR.PAIR_KEY.equal(key)).
          and(
            PAIR_VIEWS.DOMAIN.equal(domain).
            and(PAIR_VIEWS.PAIR.equal(key)).
            orNotExists(
              t.selectOne().
                from(PAIR_VIEWS).
                where(
                  PAIR_VIEWS.DOMAIN.equal(domain).
                  and(PAIR_VIEWS.PAIR.equal(key))
              )
            )
          )
        ).fetch()

    if (result.size() == 0) {
      //throw new MissingObjectException(domain + "/" + key)

      // TODO Ideally this code should throw something more descriptive like the above error
      // but for now, I'd like to keep this patch small

      throw new MissingObjectException("pair")
    }
    else {
      ResultMappingUtil.singleParentRecordToDomainPairDef(result)
    }

  })

  def getRepairActionDef(domain:String, name: String, pairKey: String) = sessionFactory.withSession(s => toRepairActionDef(getRepairAction(s, domain, name, pairKey)))
  def getPairReportDef(domain:String, name: String, pairKey: String) = sessionFactory.withSession(s => toPairReportDef(getReport(s, domain, name, pairKey)))

  def getConfigVersion(domain:String) = cachedConfigVersions.readThrough(domain, () => sessionFactory.withSession(s => {
    s.getNamedQuery("configVersionByDomain").setString("domain", domain).uniqueResult().asInstanceOf[Int]
  }))

  /**
   * Force the DB to uprev the config version column for this particular domain
   */
  private def upgradeConfigVersion(domain:String)(s:Session) = {
    s.getNamedQuery("upgradeConfigVersionByDomain").setString("domain", domain).executeUpdate()
  }

  /**
   * Force an upgrade of the domain config version in the db and the cache after the DB work has executed successfully.
   */
  private def withVersionUpgrade[T](domain:String, dbCommands:Function1[Session, T]) : T = {

    def beforeCommit(session:Session) = upgradeConfigVersion(domain)(session)
    def commandsToExecute(session:Session) = dbCommands(session)
    def afterCommit() = cachedConfigVersions.evict(domain)

    sessionFactory.withSession(
      beforeCommit _,
      commandsToExecute,
      afterCommit _
    )
  }

  def allConfigOptions(domain:String) = {
    db.listQuery[ConfigOption]("configOptionsByDomain", Map("domain_name" -> domain)).map(opt => opt.key -> opt.value).toMap
  }

  def maybeConfigOption(domain:String, key:String) =
    db.singleQueryMaybe[String]("configOptionByNameAndKey", Map("key" -> key, "domain_name" -> domain))

  def configOptionOrDefault(domain:String, key: String, defaultVal: String) =
    maybeConfigOption(domain, key) match {
      case Some(str) => str
      case None      => defaultVal
    }

  def setConfigOption(domain:String, key:String, value:String) = writeConfigOption(domain, key, value)
  def clearConfigOption(domain:String, key:String) = deleteConfigOption(domain, key)

  private def deletePairInSession(s:Session, domain:String, pair:DiffaPair) = {
    getRepairActionsInPair(domain, pair.key).foreach(s.delete)
    getEscalationsForPair(domain, pair.key).foreach(s.delete)
    getReportsForPair(domain, pair.key).foreach(s.delete)
    pair.views.foreach(s.delete(_))
    deleteStoreCheckpoint(pair.asRef)
    s.delete(pair)
  }

  def makeDomainMember(domain:String, userName:String) = {
    try {
      db.execute("createDomainMembership", Map("domain_name" -> domain, "user_name" -> userName))
    }
    catch {
      case x:SQLIntegrityConstraintViolationException =>
        handleMemberConstraintViolation(domain, userName)
      case e:Exception if e.getCause.isInstanceOf[SQLIntegrityConstraintViolationException] =>
        handleMemberConstraintViolation(domain, userName)
      // Otherwise just let the exception get thrown further
    }

    val member = Member(User(name = userName), Domain(name = domain))
    membershipListener.onMembershipCreated(member)
    member
  }

  def removeDomainMembership(domain:String, userName:String) = {
    val member = Member(User(name = userName), Domain(name = domain))

    db.execute("deleteDomainMembership", Map("domain_name" -> domain, "user_name" -> userName))

    membershipListener.onMembershipRemoved(member)
  }

  private def handleMemberConstraintViolation(domain:String, userName:String) = {
    log.info("Ignoring integrity constraint violation for domain = %s and user = %s".format(domain,userName))
  }

  def listDomainMembers(domain:String) = sessionFactory.withSession(s => {
    db.listQuery[Member]("membersByDomain", Map("domain_name" -> domain))
  })

  def listEndpointViews(s:Session, domain:String, endpointName:String) =
    db.listQuery[EndpointView]("endpointViewsByEndpoint", Map("domain_name" -> domain, "endpoint_name" -> endpointName))
  def listPairViews(s:Session, domain:String, pairKey:String) =
    db.listQuery[PairView]("pairViewsByPair", Map("domain_name" -> domain, "pair_key" -> pairKey))

}

// These key classes need to be serializable .......

case class DomainEndpointKey(
  @BeanProperty var domain: String = null,
  @BeanProperty var endpoint: String = null) {

  def this() = this(domain = null)

}

case class DomainPairKey(
  @BeanProperty var domain: String = null,
  @BeanProperty var pair: String = null) {

  def this() = this(domain = null)

}

case class PairByDomainAndEndpointPredicate(
  @BeanProperty domain:String = null,
  @BeanProperty endpoint:String = null) extends KeyPredicate[DomainEndpointKey] {
  def this() = this(domain = null)
  def constrain(key: DomainEndpointKey) = key.domain == domain && key.endpoint == endpoint
}

case class EndpointByDomainPredicate(@BeanProperty domain:String = null) extends KeyPredicate[DomainEndpointKey] {
  def this() = this(domain = null)
  def constrain(key: DomainEndpointKey) = key.domain == domain
}

case class PairByDomainPredicate(@BeanProperty domain:String = null) extends KeyPredicate[DomainPairKey] {
  def this() = this(domain = null)
  def constrain(key: DomainPairKey) = key.domain == domain
}

