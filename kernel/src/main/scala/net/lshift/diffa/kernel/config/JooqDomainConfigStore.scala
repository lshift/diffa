/**
 * Copyright (C) 2010-2012 LShift Ltd.
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

import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.hooks.HookManager
import net.lshift.diffa.schema.jooq.{DatabaseFacade => JooqDatabaseFacade}
import net.lshift.diffa.schema.tables.Members.MEMBERS
import net.lshift.diffa.schema.tables.ConfigOptions.CONFIG_OPTIONS
import net.lshift.diffa.schema.tables.RepairActions.REPAIR_ACTIONS
import net.lshift.diffa.schema.tables.Escalations.ESCALATIONS
import net.lshift.diffa.schema.tables.PairReports.PAIR_REPORTS
import net.lshift.diffa.schema.tables.PairViews.PAIR_VIEWS
import net.lshift.diffa.schema.tables.Endpoints.ENDPOINTS
import net.lshift.diffa.schema.tables.Pairs.PAIRS
import net.lshift.diffa.schema.tables.Spaces.SPACES
import net.lshift.diffa.schema.tables.EndpointViews.ENDPOINT_VIEWS
import net.lshift.diffa.schema.tables.Breakers.BREAKERS
import JooqConfigStoreCompanion._
import net.lshift.diffa.kernel.naming.CacheName._
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.lifecycle.{PairLifecycleAware, DomainLifecycleAware}
import net.lshift.diffa.kernel.util.cache.{KeyPredicate, CacheProvider}
import reflect.BeanProperty
import java.util
import collection.mutable.ListBuffer
import org.jooq.impl.Factory
import org.jooq.impl.Factory._
import net.lshift.diffa.kernel.frontend.DomainEndpointDef
import net.lshift.diffa.kernel.frontend.DomainPairDef
import net.lshift.diffa.kernel.frontend.PairDef
import net.lshift.diffa.kernel.frontend.EndpointDef
import org.jooq.{Record, Field, Condition, Table}
import java.lang.{Long => LONG}

class JooqDomainConfigStore(jooq:JooqDatabaseFacade,
                            hookManager:HookManager,
                            cacheProvider:CacheProvider,
                            membershipListener:DomainMembershipAware,
                            spacePathCache:SpacePathCache)
    extends DomainConfigStore
    with DomainLifecycleAware {

  val hook = hookManager.createDifferencePartitioningHook(jooq)

  private val pairEventSubscribers = new ListBuffer[PairLifecycleAware]
  def registerPairEventListener(p:PairLifecycleAware) = pairEventSubscribers += p

  private val cachedConfigVersions = cacheProvider.getCachedMap[Long,Int]("domain.config.versions")
  private val cachedPairs = cacheProvider.getCachedMap[Long, java.util.List[DomainPairDef]]("domain.pairs")
  private val cachedPairsByKey = cacheProvider.getCachedMap[DomainPairKey, DomainPairDef]("domain.pairs.by.key")
  private val cachedEndpoints = cacheProvider.getCachedMap[Long, java.util.List[DomainEndpointDef]]("domain.endpoints")
  private val cachedEndpointsByKey = cacheProvider.getCachedMap[DomainEndpointKey, DomainEndpointDef]("domain.endpoints.by.key")
  private val cachedPairsByEndpoint = cacheProvider.getCachedMap[DomainEndpointKey, java.util.List[DomainPairDef]]("domain.pairs.by.endpoint")
  private val cachedBreakers = cacheProvider.getCachedMap[BreakerKey, Boolean](DOMAIN_PAIR_BREAKERS)

  // Config options
  private val cachedDomainConfigOptionsMap = cacheProvider.getCachedMap[Long, java.util.Map[String,String]](DOMAIN_CONFIG_OPTIONS_MAP)
  private val cachedDomainConfigOptions = cacheProvider.getCachedMap[DomainConfigKey, String](DOMAIN_CONFIG_OPTIONS)

  // Members
  private val cachedMembers = cacheProvider.getCachedMap[Long, java.util.List[Member]](USER_DOMAIN_MEMBERS)

  def reset {
    cachedConfigVersions.evictAll()
    cachedPairs.evictAll()
    cachedPairsByKey.evictAll()
    cachedEndpoints.evictAll()
    cachedEndpointsByKey.evictAll()
    cachedPairsByEndpoint.evictAll()
    cachedBreakers.evictAll()

    cachedDomainConfigOptionsMap.evictAll()
    cachedDomainConfigOptions.evictAll()

    cachedMembers.evictAll()
  }

  private def invalidateMembershipCache(space:Long) = {
    cachedMembers.evict(space)
  }

  private def invalidateConfigCaches(space:Long) = {
    cachedDomainConfigOptionsMap.evict(space)
    cachedDomainConfigOptions.keySubset(ConfigOptionByDomainPredicate(space)).evictAll()
  }

  private def invalidateAllCaches(space:Long) = {
    cachedConfigVersions.evict(space)
    cachedEndpoints.evict(space)
    cachedPairs.evict(space)
    cachedPairsByEndpoint.keySubset(EndpointByDomainPredicate(space)).evictAll()
    cachedPairsByKey.keySubset(PairByDomainPredicate(space)).evictAll()
    cachedEndpointsByKey.keySubset(EndpointByDomainPredicate(space)).evictAll()
    cachedBreakers.keySubset(BreakerByDomainPredicate(space)).evictAll()

    invalidateConfigCaches(space)

    invalidateMembershipCache(space)
  }

  private def invalidateEndpointCachesOnly(space:Long, endpointName: String) = {
    cachedEndpoints.evict(space)
    cachedPairsByEndpoint.keySubset(PairByDomainAndEndpointPredicate(space, endpointName)).evictAll()
    cachedEndpointsByKey.evict(DomainEndpointKey(space,endpointName))

    // TODO This is a very coarse grained invalidation of the pair caches - this could be made finer at some stage
    cachedPairs.evict(space)
    cachedPairsByKey.keySubset(PairByDomainPredicate(space)).evictAll()
  }

  private def invalidatePairCachesOnly(space:Long) = {
    cachedPairs.evict(space)
    cachedPairsByKey.keySubset(PairByDomainPredicate(space)).evictAll()
    cachedPairsByEndpoint.keySubset(EndpointByDomainPredicate(space)).evictAll()
  }

  def onDomainUpdated(space: Long) = invalidateAllCaches(space)

  def onDomainRemoved(space: Long) = invalidateAllCaches(space)

  def createOrUpdateEndpoint(domain:String, endpointDef: EndpointDef) : DomainEndpointDef = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    jooq.execute(t => {

      t.insertInto(ENDPOINTS).
          set(ENDPOINTS.SPACE, space.id).
          set(ENDPOINTS.NAME, endpointDef.name).
          set(ENDPOINTS.COLLATION_TYPE, endpointDef.collation).
          set(ENDPOINTS.CONTENT_RETRIEVAL_URL, endpointDef.contentRetrievalUrl).
          set(ENDPOINTS.SCAN_URL, endpointDef.scanUrl).
          set(ENDPOINTS.VERSION_GENERATION_URL, endpointDef.versionGenerationUrl).
          set(ENDPOINTS.INBOUND_URL, endpointDef.inboundUrl).
        onDuplicateKeyUpdate().
          set(ENDPOINTS.COLLATION_TYPE, endpointDef.collation).
          set(ENDPOINTS.CONTENT_RETRIEVAL_URL, endpointDef.contentRetrievalUrl).
          set(ENDPOINTS.SCAN_URL, endpointDef.scanUrl).
          set(ENDPOINTS.VERSION_GENERATION_URL, endpointDef.versionGenerationUrl).
          set(ENDPOINTS.INBOUND_URL, endpointDef.inboundUrl).
        execute()

      // Don't attempt to update to update any rows per se, just delete every associated
      // category and re-insert the new definitions, irrespective of
      // whether they are identical to the previous definitions

      deleteCategories(t, space.id, endpointDef.name)

      // Insert categories for the endpoint proper
      insertCategories(t, space.id, endpointDef)

      // Update the view definitions

      if (endpointDef.views.isEmpty) {

        t.delete(ENDPOINT_VIEWS).
          where(ENDPOINT_VIEWS.SPACE.equal(space.id)).
            and(ENDPOINT_VIEWS.ENDPOINT.equal(endpointDef.name)).
          execute()

      } else {

        t.delete(ENDPOINT_VIEWS).
          where(ENDPOINT_VIEWS.NAME.notIn(endpointDef.views.map(v => v.name))).
            and(ENDPOINT_VIEWS.SPACE.equal(space.id)).
            and(ENDPOINT_VIEWS.ENDPOINT.equal(endpointDef.name)).
          execute()

      }

      endpointDef.views.foreach(v => {
        t.insertInto(ENDPOINT_VIEWS).
            set(ENDPOINT_VIEWS.SPACE, space.id).
            set(ENDPOINT_VIEWS.ENDPOINT, endpointDef.name).
            set(ENDPOINT_VIEWS.NAME, v.name).
          onDuplicateKeyIgnore().
          execute()

          // Insert categories for the endpoint view
        insertCategoriesForView(t, space.id, endpointDef.name, v)
      })

      upgradeConfigVersion(t, space.id)

    })

    invalidateEndpointCachesOnly(space.id, endpointDef.name)

    DomainEndpointDef(
      space = space.id,
      name = endpointDef.name,
      collation = endpointDef.collation,
      contentRetrievalUrl = endpointDef.contentRetrievalUrl,
      scanUrl = endpointDef.scanUrl,
      versionGenerationUrl = endpointDef.versionGenerationUrl,
      inboundUrl = endpointDef.inboundUrl,
      categories = endpointDef.categories,
      views = endpointDef.views
    )
  }



  def deleteEndpoint(domain:String, endpoint: String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    jooq.execute(t => {

      // Remove all pairs that reference the endpoint

      val results = t.select(PAIRS.SPACE, PAIRS.NAME).
                      from(PAIRS).
                      where(PAIRS.SPACE.equal(space.id)).
                        and(PAIRS.UPSTREAM.equal(endpoint).
                            or(PAIRS.DOWNSTREAM.equal(endpoint))).fetch()

      results.iterator().foreach(r => {
        val ref = PairRef(r.getValue(PAIRS.NAME), r.getValue(PAIRS.SPACE))
        deletePairWithDependencies(t, ref)
      })

      deleteCategories(t, space.id, endpoint)

      t.delete(ENDPOINT_VIEWS).
        where(ENDPOINT_VIEWS.SPACE.equal(space.id)).
          and(ENDPOINT_VIEWS.ENDPOINT.equal(endpoint)).
        execute()

      var deleted = t.delete(ENDPOINTS).
                      where(ENDPOINTS.SPACE.equal(space.id)).
                        and(ENDPOINTS.NAME.equal(endpoint)).
                      execute()

      if (deleted == 0) {
        throw new MissingObjectException("endpoint")
      }

      upgradeConfigVersion(t, space.id)

    })

    invalidatePairCachesOnly(space.id)
    invalidateEndpointCachesOnly(space.id, endpoint)

  }

  def getEndpointDef(domain:String, endpoint: String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    cachedEndpointsByKey.readThrough(DomainEndpointKey(space.id, endpoint), () => {

      val endpoints = JooqConfigStoreCompanion.listEndpoints(jooq, Some(space.id), Some(endpoint))

      if (endpoints.isEmpty) {
        throw new MissingObjectException("endpoint")
      } else {
        endpoints.head
      }

    })
  }.withoutDomain()


  def listEndpoints(domain:String): Seq[EndpointDef] = {

    if (spacePathCache.doesDomainExist(domain)) {

      val space = spacePathCache.resolveSpacePathOrDie(domain)
      val endpoints = cachedEndpoints.readThrough(space.id, () => JooqConfigStoreCompanion.listEndpoints(jooq, Some(space.id)))
      endpoints.map(_.withoutDomain())

    } else {

      Seq[EndpointDef]()

    }
  }

  def createOrUpdatePair(domain:String, pair: PairDef) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    pair.validate()

    jooq.execute(t => {
      t.insertInto(PAIRS).
          set(PAIRS.SPACE, space.id:LONG).
          set(PAIRS.NAME, pair.key).
          set(PAIRS.UPSTREAM, pair.upstreamName).
          set(PAIRS.DOWNSTREAM, pair.downstreamName).
          set(PAIRS.ALLOW_MANUAL_SCANS, pair.allowManualScans).
          set(PAIRS.MATCHING_TIMEOUT, pair.matchingTimeout.asInstanceOf[Integer]).
          set(PAIRS.SCAN_CRON_SPEC, pair.scanCronSpec).
          set(PAIRS.SCAN_CRON_ENABLED, boolean2Boolean(pair.scanCronEnabled)).
          set(PAIRS.VERSION_POLICY_NAME, pair.versionPolicyName).
        onDuplicateKeyUpdate().
          set(PAIRS.UPSTREAM, pair.upstreamName).
          set(PAIRS.DOWNSTREAM, pair.downstreamName).
          set(PAIRS.ALLOW_MANUAL_SCANS, pair.allowManualScans).
          set(PAIRS.MATCHING_TIMEOUT, pair.matchingTimeout.asInstanceOf[Integer]).
          set(PAIRS.SCAN_CRON_SPEC, pair.scanCronSpec).
          set(PAIRS.SCAN_CRON_ENABLED, boolean2Boolean(pair.scanCronEnabled)).
          set(PAIRS.VERSION_POLICY_NAME, pair.versionPolicyName).
        execute()

      type HasName = {def name: String}
      def clearUnused[R <: Record](t:Factory, table:Table[R], namesSource:Iterable[HasName], pairCondition:Condition, nameField:Field[String]) {
        val names = namesSource.map(_.name).toSeq

        if (names.length == 0) {
          t.delete(table).
            where(pairCondition).
            execute()
        } else {
          t.delete(table).
            where(nameField.notIn(names)).
              and(pairCondition).
            execute()
        }
      }
      def insertOrUpdate[R <: Record](t:Factory, table:Table[R], finders:Map[_ <: Field[_], _], values:Map[_ <: Field[_], _]) {
        t.insertInto(table).
            set(finders).
            set(values).
          onDuplicateKeyUpdate().
            set(values).
          execute()
      }

      clearUnused(t, PAIR_VIEWS, pair.views,
        PAIR_VIEWS.SPACE.equal(space.id).and(PAIR_VIEWS.PAIR.equal(pair.key)),
        PAIR_VIEWS.NAME)
      clearUnused(t, ESCALATIONS, pair.escalations,
        ESCALATIONS.SPACE.equal(space.id).and(ESCALATIONS.PAIR.equal(pair.key)),
        ESCALATIONS.NAME)
      clearUnused(t, PAIR_REPORTS, pair.reports,
        PAIR_REPORTS.SPACE.equal(space.id).and(PAIR_REPORTS.PAIR.equal(pair.key)),
        PAIR_REPORTS.NAME)
      clearUnused(t, REPAIR_ACTIONS, pair.repairActions,
        REPAIR_ACTIONS.SPACE.equal(space.id).and(REPAIR_ACTIONS.PAIR.equal(pair.key)),
        REPAIR_ACTIONS.NAME)

      pair.views.foreach(v => {
        insertOrUpdate(t, PAIR_VIEWS,
          Map(PAIR_VIEWS.SPACE -> space.id, PAIR_VIEWS.PAIR -> pair.key, PAIR_VIEWS.NAME -> v.name),
          Map(PAIR_VIEWS.SCAN_CRON_SPEC -> v.scanCronSpec, PAIR_VIEWS.SCAN_CRON_ENABLED -> boolean2Boolean(v.scanCronEnabled)))
      })
      pair.repairActions.foreach(a => {
        insertOrUpdate(t, REPAIR_ACTIONS,
          Map(REPAIR_ACTIONS.SPACE -> space.id, REPAIR_ACTIONS.PAIR -> pair.key, REPAIR_ACTIONS.NAME -> a.name),
          Map(REPAIR_ACTIONS.URL -> a.url, REPAIR_ACTIONS.SCOPE -> a.scope))
      })
      pair.reports.foreach(r => {
        insertOrUpdate(t, PAIR_REPORTS,
          Map(PAIR_REPORTS.SPACE -> space.id, PAIR_REPORTS.PAIR -> pair.key, PAIR_REPORTS.NAME -> r.name),
          Map(PAIR_REPORTS.REPORT_TYPE -> r.reportType, PAIR_REPORTS.TARGET -> r.target))
      })
      pair.escalations.foreach(e => {
        insertOrUpdate(t, ESCALATIONS,
          Map(ESCALATIONS.SPACE -> space.id, ESCALATIONS.PAIR -> pair.key, ESCALATIONS.NAME -> e.name),
          Map(ESCALATIONS.ACTION -> e.action, ESCALATIONS.ACTION_TYPE -> e.actionType,
            ESCALATIONS.RULE -> e.rule, ESCALATIONS.DELAY -> e.delay))
      })

      upgradeConfigVersion(t, space.id)

    })

    invalidatePairCachesOnly(space.id)

    hook.pairCreated(space.id, pair.key)
    pairEventSubscribers.foreach(_.onPairUpdated(pair.asRef(domain)))
  }

  def deletePair(domain:String, key: String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    jooq.execute(t => {
      val ref = PairRef(key, space.id)
      invalidatePairCachesOnly(space.id)
      deletePairWithDependencies(t, ref)
      upgradeConfigVersion(t, space.id)
      pairEventSubscribers.foreach(_.onPairDeleted(DiffaPairRef(key, domain)))
      hook.pairRemoved(space.id, key)
    })
  }

  def listPairs(domain:String) = {
    if (spacePathCache.doesDomainExist(domain)) {
      val space = spacePathCache.resolveSpacePathOrDie(domain)
      cachedPairs.readThrough(space.id, () => JooqConfigStoreCompanion.listPairs(jooq, domain, space.id))
    } else {
      Seq[DomainPairDef]()
    }
  }

  def listPairsForEndpoint(domain:String, endpoint:String) = {
    val space = spacePathCache.resolveSpacePathOrDie(domain)
    cachedPairsByEndpoint.readThrough(DomainEndpointKey(space.id, endpoint), () => JooqConfigStoreCompanion.listPairs(jooq, domain, space.id, endpoint = Some(endpoint)))
  }


  @Deprecated def getEndpoint(domain:String, endpoint: String) = {

    val endpointDef = getEndpointDef(domain, endpoint)

    val ep = Endpoint(
      name = endpointDef.name,
      domain = Domain(name = domain),
      scanUrl = endpointDef.scanUrl,
      versionGenerationUrl = endpointDef.versionGenerationUrl,
      contentRetrievalUrl = endpointDef.contentRetrievalUrl,
      collation = endpointDef.collation,
      categories = endpointDef.categories
    )

    val views = new util.HashSet[EndpointView]()

    endpointDef.views.foreach(v => {
      views.add(EndpointView(
        name = v.name,
        endpoint = ep,
        categories = v.categories
      ))
    })

    ep.setViews(views)

    ep
  }


  def getPairDef(domain:String, key: String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    cachedPairsByKey.readThrough(DomainPairKey(space.id, key), () => jooq.execute { t =>
      val pairs = JooqConfigStoreCompanion.listPairs(jooq, domain, space.id, key = Some(key))
      if (pairs.length == 1) {
        pairs(0)
      } else {
        //throw new MissingObjectException(domain + "/" + key)

        // TODO Ideally this code should throw something more descriptive like the above error
        // but for now, I'd like to keep this patch small

        throw new MissingObjectException("pair")
      }
    })
  }

  def getConfigVersion(domain:String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    cachedConfigVersions.readThrough(space.id, () => jooq.execute(t => {

      val result = t.select(SPACES.CONFIG_VERSION).
        from(SPACES).
        where(SPACES.ID.equal(space.id)).
        fetchOne()

      if (result == null) {
        throw new MissingObjectException("domain")
      }
      else {
        result.getValue(SPACES.CONFIG_VERSION)
      }

    }))
  }



  def allConfigOptions(domain:String) = {
    if(spacePathCache.doesDomainExist(domain)) {
      val space = spacePathCache.resolveSpacePathOrDie(domain)

      cachedDomainConfigOptionsMap.readThrough(space.id, () => jooq.execute( t => {
        val results = t.select(CONFIG_OPTIONS.OPT_KEY, CONFIG_OPTIONS.OPT_VAL).
          from(CONFIG_OPTIONS).
          where(CONFIG_OPTIONS.SPACE.equal(space.id)).fetch()

        val configs = new java.util.HashMap[String,String]()

        results.iterator().foreach(r => {
          configs.put(r.getValue(CONFIG_OPTIONS.OPT_KEY), r.getValue(CONFIG_OPTIONS.OPT_VAL))
        })

        configs
      })).toMap
    } else {
      Map()
    }
  }


  def maybeConfigOption(domain:String, key:String) = {

    if (spacePathCache.doesDomainExist(domain)) {

      val space = spacePathCache.resolveSpacePathOrDie(domain)

      val option = cachedDomainConfigOptions.readThrough(DomainConfigKey(space.id,key), () => jooq.execute( t => {

        val record = t.select(CONFIG_OPTIONS.OPT_VAL).
                       from(CONFIG_OPTIONS).
                       where(CONFIG_OPTIONS.SPACE.equal(space.id)).
                         and(CONFIG_OPTIONS.OPT_KEY.equal(key)).
                       fetchOne()

        if (record != null) {
          record.getValue(CONFIG_OPTIONS.OPT_VAL)
        }
        else {
          // Insert a null byte into as a value for this key in the cache to denote that this key does not
          // exist and should not get queried for against the the underlying database
          "\u0000"
        }

      }))

      option match {
        case "\u0000"     => None
        case value        => Some(value)
      }
    } else {
      None
    }
  }

  def configOptionOrDefault(domain:String, key: String, defaultVal: String) =
    maybeConfigOption(domain, key) match {
      case Some(str) => str
      case None      => defaultVal
    }

  def setConfigOption(domain:String, key:String, value:String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    jooq.execute(t => {
      t.insertInto(CONFIG_OPTIONS).
        set(CONFIG_OPTIONS.SPACE, space.id).
        set(CONFIG_OPTIONS.OPT_KEY, key).
        set(CONFIG_OPTIONS.OPT_VAL, value).
      onDuplicateKeyUpdate().
        set(CONFIG_OPTIONS.OPT_VAL, value).
      execute()
    })

    invalidateConfigCaches(space.id)
  }

  def clearConfigOption(domain:String, key:String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    jooq.execute(t => {
      t.delete(CONFIG_OPTIONS).
        where(CONFIG_OPTIONS.SPACE.equal(space.id)).
        and(CONFIG_OPTIONS.OPT_KEY.equal(key)).
      execute()
    })

    // TODO This is a very coarse grained invalidation
    invalidateConfigCaches(space.id)
  }

  /**
   * Force the DB to uprev the config version column for this particular domain
   */
  private def upgradeConfigVersion(t:Factory, space:Long) {

    cachedConfigVersions.evict(space)

    t.update(SPACES).
      set(SPACES.CONFIG_VERSION, SPACES.CONFIG_VERSION.add(1)).
      where(SPACES.ID.equal(space)).
      execute()
  }

  def makeDomainMember(domain:String, userName:String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    jooq.execute(t => {
      t.insertInto(MEMBERS).
        set(MEMBERS.SPACE, space.id).
        set(MEMBERS.USERNAME, userName).
        onDuplicateKeyIgnore().
        execute()
    })

    invalidateMembershipCache(space.id)

    val member = Member(userName, space.id, domain)
    membershipListener.onMembershipCreated(member)
    member
  }

  def removeDomainMembership(domain:String, userName:String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    jooq.execute(t => {
      t.delete(MEMBERS).
        where(MEMBERS.SPACE.equal(space.id)).
        and(MEMBERS.USERNAME.equal(userName)).
        execute()
    })

    invalidateMembershipCache(space.id)

    val member = Member(userName,space.id, domain)
    membershipListener.onMembershipRemoved(member)
  }

  def listDomainMembers(domain:String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    cachedMembers.readThrough(space.id, () => {

      jooq.execute(t => {

        val results = t.select(MEMBERS.USERNAME).
          from(MEMBERS).
          where(MEMBERS.SPACE.equal(space.id)).
          fetch()

        val members = new java.util.ArrayList[Member]()
        results.iterator().foreach(r => members.add(Member(
          user = r.getValue(MEMBERS.USERNAME),
          space = space.id,
          domain = domain
        ))
        )
        members

      })
    }).toSeq
  }

  def isBreakerTripped(domain: String, pair: String, name: String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    cachedBreakers.readThrough(BreakerKey(space.id, pair, name), () => jooq.execute(t => {
      val c = t.selectCount().
        from(BREAKERS).
        where(BREAKERS.SPACE.equal(space.id)).
          and(BREAKERS.PAIR.equal(pair)).
          and(BREAKERS.NAME.equal(name)).
        fetchOne().
        getValue(0).asInstanceOf[java.lang.Number]

      val breakerPresent = (c != null && c.intValue() > 0)

      // We consider a breaker to be tripped (ie, the feature should not be used) when there is a matching row in
      // the table.
      breakerPresent
    }))
  }

  def tripBreaker(domain: String, pair: String, name: String) {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    if (!isBreakerTripped(domain, pair, name)) {
      jooq.execute(t => {
        t.insertInto(BREAKERS).
            set(BREAKERS.SPACE, space.id).
            set(BREAKERS.PAIR, pair).
            set(BREAKERS.NAME, name).
          onDuplicateKeyIgnore().
          execute()
      })

      cachedBreakers.put(BreakerKey(space.id, pair, name), true)
    }
  }

  def clearBreaker(domain: String, pair: String, name: String) {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    if (isBreakerTripped(domain, pair, name)) {
      jooq.execute(t => {
        t.delete(BREAKERS).
          where(BREAKERS.SPACE.equal(space.id)).
            and(BREAKERS.PAIR.equal(pair)).
            and(BREAKERS.NAME.equal(name)).
          execute()
      })

      cachedBreakers.put(BreakerKey(space.id, pair, name), false)
    }
  }
}

// These key classes need to be serializable .......

case class DomainEndpointKey(
  @BeanProperty var space: Long,
  @BeanProperty var endpoint: String = null) {

  def this() = this(space = 0)

}

case class DomainPairKey(
  @BeanProperty var space: Long,
  @BeanProperty var pair: String = null) {

  def this() = this(space = 0)

}

case class DomainConfigKey(
  @BeanProperty var space: Long,
  @BeanProperty var configKey: String = null) {

  def this() = this(space = 0)

}

case class BreakerKey(
  @BeanProperty var space: Long,
  @BeanProperty var pair:String = null,
  @BeanProperty var name:String = null) {

  def this() = this(space = 0)
}

case class ConfigOptionByDomainPredicate(
  @BeanProperty var space:Long) extends KeyPredicate[DomainConfigKey] {
  def this() = this(space = 0)
  def constrain(key: DomainConfigKey) = key.space == space
}

case class PairByDomainAndEndpointPredicate(
  @BeanProperty var space:Long,
  @BeanProperty endpoint:String = null) extends KeyPredicate[DomainEndpointKey] {
  def this() = this(space = 0)
  def constrain(key: DomainEndpointKey) = key.space == space && key.endpoint == endpoint
}

case class EndpointByDomainPredicate(@BeanProperty var space:Long) extends KeyPredicate[DomainEndpointKey] {
  def this() = this(space = 0)
  def constrain(key: DomainEndpointKey) = key.space == space
}

case class PairByDomainPredicate(@BeanProperty var space:Long) extends KeyPredicate[DomainPairKey] {
  def this() = this(space = 0)
  def constrain(key: DomainPairKey) = key.space == space
}

case class BreakerByDomainPredicate(@BeanProperty var space:Long) extends KeyPredicate[BreakerKey] {
  def this() = this(space = 0)
  def constrain(key: BreakerKey) = key.space == space
}
case class BreakerByPairAndDomainPredicate(
    @BeanProperty var space:Long,
    @BeanProperty var pair:String = null) extends KeyPredicate[BreakerKey] {

  def this() = this(space = 0)
  def constrain(key: BreakerKey) = key.space == space && key.pair == pair
}
