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
import net.lshift.diffa.schema.tables.Domains.DOMAINS
import net.lshift.diffa.schema.tables.Members.MEMBERS
import net.lshift.diffa.schema.tables.ConfigOptions.CONFIG_OPTIONS
import net.lshift.diffa.schema.tables.RepairActions.REPAIR_ACTIONS
import net.lshift.diffa.schema.tables.Escalations.ESCALATIONS
import net.lshift.diffa.schema.tables.PairReports.PAIR_REPORTS
import net.lshift.diffa.schema.tables.Pair.PAIR
import net.lshift.diffa.schema.tables.PairViews.PAIR_VIEWS
import net.lshift.diffa.schema.tables.Endpoint.ENDPOINT
import net.lshift.diffa.schema.tables.EndpointViews.ENDPOINT_VIEWS
import net.lshift.diffa.schema.tables.Breakers.BREAKERS
import JooqConfigStoreCompanion._
import net.lshift.diffa.kernel.naming.CacheName._
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.lifecycle.{PairLifecycleAware, DomainLifecycleAware}
import net.lshift.diffa.kernel.util.cache.{KeyPredicate, CacheProvider}
import scala.beans.BeanProperty
import java.util
import collection.mutable.ListBuffer
import org.jooq.impl.Factory
import org.jooq.impl.Factory._
import net.lshift.diffa.kernel.frontend.DomainEndpointDef
import net.lshift.diffa.kernel.frontend.DomainPairDef
import net.lshift.diffa.kernel.frontend.RepairActionDef
import net.lshift.diffa.kernel.frontend.PairDef
import net.lshift.diffa.kernel.frontend.PairViewDef
import net.lshift.diffa.kernel.frontend.EndpointDef
import net.lshift.diffa.kernel.frontend.EscalationDef
import net.lshift.diffa.kernel.frontend.PairReportDef
import org.jooq.{Record, Field, Condition, Table}

class JooqDomainConfigStore(jooq:JooqDatabaseFacade,
                            hookManager:HookManager,
                            cacheProvider:CacheProvider,
                            membershipListener:DomainMembershipAware)
    extends DomainConfigStore
    with DomainLifecycleAware {

  val hook = hookManager.createDifferencePartitioningHook(jooq)

  private val pairEventSubscribers = new ListBuffer[PairLifecycleAware]
  def registerPairEventListener(p:PairLifecycleAware) = pairEventSubscribers += p

  private val cachedConfigVersions = cacheProvider.getCachedMap[String,Int]("domain.config.versions")
  private val cachedPairs = cacheProvider.getCachedMap[String, java.util.List[DomainPairDef]]("domain.pairs")
  private val cachedPairsByKey = cacheProvider.getCachedMap[DomainPairKey, DomainPairDef]("domain.pairs.by.key")
  private val cachedEndpoints = cacheProvider.getCachedMap[String, java.util.List[DomainEndpointDef]]("domain.endpoints")
  private val cachedEndpointsByKey = cacheProvider.getCachedMap[DomainEndpointKey, DomainEndpointDef]("domain.endpoints.by.key")
  private val cachedPairsByEndpoint = cacheProvider.getCachedMap[DomainEndpointKey, java.util.List[DomainPairDef]]("domain.pairs.by.endpoint")
  private val cachedBreakers = cacheProvider.getCachedMap[BreakerKey, Boolean](DOMAIN_PAIR_BREAKERS)

  // Config options
  private val cachedDomainConfigOptionsMap = cacheProvider.getCachedMap[String, java.util.Map[String,String]](DOMAIN_CONFIG_OPTIONS_MAP)
  private val cachedDomainConfigOptions = cacheProvider.getCachedMap[DomainConfigKey, String](DOMAIN_CONFIG_OPTIONS)

  // Members
  private val cachedMembers = cacheProvider.getCachedMap[String, java.util.List[Member]](USER_DOMAIN_MEMBERS)

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

  private def invalidateMembershipCache(domain:String) = {
    cachedMembers.evict(domain)
  }

  private def invalidateConfigCaches(domain:String) = {
    cachedDomainConfigOptionsMap.evict(domain)
    cachedDomainConfigOptions.keySubset(ConfigOptionByDomainPredicate(domain)).evictAll()
  }

  private def invalidateAllCaches(domain:String) = {
    cachedConfigVersions.evict(domain)
    cachedEndpoints.evict(domain)
    cachedPairs.evict(domain)
    cachedPairsByEndpoint.keySubset(EndpointByDomainPredicate(domain)).evictAll()
    cachedPairsByKey.keySubset(PairByDomainPredicate(domain)).evictAll()
    cachedEndpointsByKey.keySubset(EndpointByDomainPredicate(domain)).evictAll()
    cachedBreakers.keySubset(BreakerByDomainPredicate(domain)).evictAll()

    invalidateConfigCaches(domain)

    invalidateMembershipCache(domain)
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

  def createOrUpdateEndpoint(domain:String, endpointDef: EndpointDef) : DomainEndpointDef = {
    val ordering = if (endpointDef.validateEntityOrder.equals(EntityOrdering.ENFORCED)) {
      endpointDef.collation
    } else {
      EntityOrdering.UNENFORCED
    }

    jooq.execute(t => {

      t.insertInto(ENDPOINT).
          set(ENDPOINT.DOMAIN, domain).
          set(ENDPOINT.NAME, endpointDef.name).
          set(ENDPOINT.COLLATION_TYPE, ordering).
          set(ENDPOINT.CONTENT_RETRIEVAL_URL, endpointDef.contentRetrievalUrl).
          set(ENDPOINT.SCAN_URL, endpointDef.scanUrl).
          set(ENDPOINT.VERSION_GENERATION_URL, endpointDef.versionGenerationUrl).
          set(ENDPOINT.INBOUND_URL, endpointDef.inboundUrl).
        onDuplicateKeyUpdate().
          set(ENDPOINT.COLLATION_TYPE, ordering).
          set(ENDPOINT.CONTENT_RETRIEVAL_URL, endpointDef.contentRetrievalUrl).
          set(ENDPOINT.SCAN_URL, endpointDef.scanUrl).
          set(ENDPOINT.VERSION_GENERATION_URL, endpointDef.versionGenerationUrl).
          set(ENDPOINT.INBOUND_URL, endpointDef.inboundUrl).
        execute()

      // Don't attempt to update to update any rows per se, just delete every associated
      // category and re-insert the new definitions, irrespective of
      // whether they are identical to the previous definitions

      deleteCategories(t, domain, endpointDef.name)

      // Insert categories for the endpoint proper
      insertCategories(t, domain, endpointDef)

      // Update the view definitions

      if (endpointDef.views.isEmpty) {

        t.delete(ENDPOINT_VIEWS).
          where(ENDPOINT_VIEWS.DOMAIN.equal(domain)).
            and(ENDPOINT_VIEWS.ENDPOINT.equal(endpointDef.name)).
          execute()

      } else {

        t.delete(ENDPOINT_VIEWS).
          where(ENDPOINT_VIEWS.NAME.notIn(endpointDef.views.map(v => v.name))).
            and(ENDPOINT_VIEWS.DOMAIN.equal(domain)).
            and(ENDPOINT_VIEWS.ENDPOINT.equal(endpointDef.name)).
          execute()

      }

      endpointDef.views.foreach(v => {
        t.insertInto(ENDPOINT_VIEWS).
            set(ENDPOINT_VIEWS.DOMAIN, domain).
            set(ENDPOINT_VIEWS.ENDPOINT, endpointDef.name).
            set(ENDPOINT_VIEWS.NAME, v.name).
          onDuplicateKeyIgnore().
          execute()

          // Insert categories for the endpoint view
        insertCategoriesForView(t, domain, endpointDef.name, v)
      })

      upgradeConfigVersion(t, domain)

    })

    invalidateEndpointCachesOnly(domain, endpointDef.name)

    DomainEndpointDef(
      domain = domain,
      name = endpointDef.name,
      validateEntityOrder = endpointDef.validateEntityOrder,
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

    jooq.execute(t => {

      // Remove all pairs that reference the endpoint

      val results = t.select(PAIR.DOMAIN, PAIR.PAIR_KEY).
                      from(PAIR).
                      where(PAIR.DOMAIN.equal(domain)).
                        and(PAIR.UPSTREAM.equal(endpoint).
                            or(PAIR.DOWNSTREAM.equal(endpoint))).fetch()

      results.iterator().foreach(r => {
        val ref = DiffaPairRef(r.getValue(PAIR.PAIR_KEY), r.getValue(PAIR.DOMAIN))
        deletePairWithDependencies(t, ref)
      })

      deleteCategories(t, domain, endpoint)

      t.delete(ENDPOINT_VIEWS).
        where(ENDPOINT_VIEWS.DOMAIN.equal(domain)).
          and(ENDPOINT_VIEWS.ENDPOINT.equal(endpoint)).
        execute()

      var deleted = t.delete(ENDPOINT).
                      where(ENDPOINT.DOMAIN.equal(domain)).
                        and(ENDPOINT.NAME.equal(endpoint)).
                      execute()

      if (deleted == 0) {
        throw new MissingObjectException("endpoint")
      }

      upgradeConfigVersion(t, domain)

    })

    invalidatePairCachesOnly(domain)
    invalidateEndpointCachesOnly(domain, endpoint)

  }

  def getEndpointDef(domain:String, endpoint: String) = {
    cachedEndpointsByKey.readThrough(DomainEndpointKey(domain, endpoint), () => {

      val endpoints = JooqConfigStoreCompanion.listEndpoints(jooq, Some(domain), Some(endpoint))

      if (endpoints.isEmpty) {
        throw new MissingObjectException("endpoint")
      } else {
        endpoints.head
      }
    })
  }.withoutDomain()

  def listEndpoints(domain:String): Seq[EndpointDef] = {
    cachedEndpoints.readThrough(domain, () => JooqConfigStoreCompanion.listEndpoints(jooq, Some(domain)))
  }.map(_.withoutDomain())

  def createOrUpdatePair(domain:String, pair: PairDef) = {

    pair.validate()

    jooq.execute(t => {
      t.insertInto(PAIR).
          set(PAIR.DOMAIN, domain).
          set(PAIR.PAIR_KEY, pair.key).
          set(PAIR.UPSTREAM, pair.upstreamName).
          set(PAIR.DOWNSTREAM, pair.downstreamName).
          set(PAIR.ALLOW_MANUAL_SCANS, pair.allowManualScans).
          set(PAIR.MATCHING_TIMEOUT, pair.matchingTimeout.asInstanceOf[Integer]).
          set(PAIR.SCAN_CRON_SPEC, pair.scanCronSpec).
          set(PAIR.SCAN_CRON_ENABLED, boolean2Boolean(pair.scanCronEnabled)).
          set(PAIR.VERSION_POLICY_NAME, pair.versionPolicyName).
        onDuplicateKeyUpdate().
          set(PAIR.UPSTREAM, pair.upstreamName).
          set(PAIR.DOWNSTREAM, pair.downstreamName).
          set(PAIR.ALLOW_MANUAL_SCANS, pair.allowManualScans).
          set(PAIR.MATCHING_TIMEOUT, pair.matchingTimeout.asInstanceOf[Integer]).
          set(PAIR.SCAN_CRON_SPEC, pair.scanCronSpec).
          set(PAIR.SCAN_CRON_ENABLED, boolean2Boolean(pair.scanCronEnabled)).
          set(PAIR.VERSION_POLICY_NAME, pair.versionPolicyName).
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
        PAIR_VIEWS.DOMAIN.equal(domain).and(PAIR_VIEWS.PAIR.equal(pair.key)),
        PAIR_VIEWS.NAME)
      clearUnused(t, ESCALATIONS, pair.escalations,
        ESCALATIONS.DOMAIN.equal(domain).and(ESCALATIONS.PAIR_KEY.equal(pair.key)),
        ESCALATIONS.NAME)
      clearUnused(t, PAIR_REPORTS, pair.reports,
        PAIR_REPORTS.DOMAIN.equal(domain).and(PAIR_REPORTS.PAIR_KEY.equal(pair.key)),
        PAIR_REPORTS.NAME)
      clearUnused(t, REPAIR_ACTIONS, pair.repairActions,
        REPAIR_ACTIONS.DOMAIN.equal(domain).and(REPAIR_ACTIONS.PAIR_KEY.equal(pair.key)),
        REPAIR_ACTIONS.NAME)

      pair.views.foreach(v => {
        insertOrUpdate(t, PAIR_VIEWS,
          Map(PAIR_VIEWS.DOMAIN -> domain, PAIR_VIEWS.PAIR -> pair.key, PAIR_VIEWS.NAME -> v.name),
          Map(PAIR_VIEWS.SCAN_CRON_SPEC -> v.scanCronSpec, PAIR_VIEWS.SCAN_CRON_ENABLED -> boolean2Boolean(v.scanCronEnabled)))
      })
      pair.repairActions.foreach(a => {
        insertOrUpdate(t, REPAIR_ACTIONS,
          Map(REPAIR_ACTIONS.DOMAIN -> domain, REPAIR_ACTIONS.PAIR_KEY -> pair.key, REPAIR_ACTIONS.NAME -> a.name),
          Map(REPAIR_ACTIONS.URL -> a.url, REPAIR_ACTIONS.SCOPE -> a.scope))
      })
      pair.reports.foreach(r => {
        insertOrUpdate(t, PAIR_REPORTS,
          Map(PAIR_REPORTS.DOMAIN -> domain, PAIR_REPORTS.PAIR_KEY -> pair.key, PAIR_REPORTS.NAME -> r.name),
          Map(PAIR_REPORTS.REPORT_TYPE -> r.reportType, PAIR_REPORTS.TARGET -> r.target))
      })
      pair.escalations.foreach(e => {
        insertOrUpdate(t, ESCALATIONS,
          Map(ESCALATIONS.DOMAIN -> domain, ESCALATIONS.PAIR_KEY -> pair.key, ESCALATIONS.NAME -> e.name),
          Map(ESCALATIONS.ACTION -> e.action, ESCALATIONS.ACTION_TYPE -> e.actionType,
            ESCALATIONS.RULE -> e.rule, ESCALATIONS.DELAY -> e.delay))
      })

      upgradeConfigVersion(t, domain)

    })

    invalidatePairCachesOnly(domain)

    hook.pairCreated(domain, pair.key)
    pairEventSubscribers.foreach(_.onPairUpdated(pair.asRef(domain)))
  }

  def deletePair(domain:String, key: String) = {
    jooq.execute(t => {
      val ref = DiffaPairRef(key,domain)
      invalidatePairCachesOnly(domain)
      deletePairWithDependencies(t, ref)
      upgradeConfigVersion(t, domain)
      pairEventSubscribers.foreach(_.onPairDeleted(ref))
      hook.pairRemoved(domain, key)
    })
  }

  def listPairs(domain:String) = cachedPairs.readThrough(domain, () => JooqConfigStoreCompanion.listPairs(jooq,domain))

  def listPairsForEndpoint(domain:String, endpoint:String) =
    cachedPairsByEndpoint.readThrough(DomainEndpointKey(domain, endpoint), () => JooqConfigStoreCompanion.listPairs(jooq, domain, endpoint = Some(endpoint)))

  @Deprecated def getEndpoint(domain:String, endpoint: String) = {

    val endpointDef = getEndpointDef(domain, endpoint)

    val ep = Endpoint(
      name = endpointDef.name,
      domain = Domain(name = domain),
      scanUrl = endpointDef.scanUrl,
      versionGenerationUrl = endpointDef.versionGenerationUrl,
      contentRetrievalUrl = endpointDef.contentRetrievalUrl,
      validateEntityOrder = endpointDef.validateEntityOrder,
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


  def getPairDef(domain:String, key: String) = cachedPairsByKey.readThrough(DomainPairKey(domain,key), () => jooq.execute { t =>
    val pairs = JooqConfigStoreCompanion.listPairs(jooq, domain, key = Some(key))
    if (pairs.length == 1) {
      pairs(0)
    } else {
      //throw new MissingObjectException(domain + "/" + key)

      // TODO Ideally this code should throw something more descriptive like the above error
      // but for now, I'd like to keep this patch small

      throw new MissingObjectException("pair")
    }
  })

  def getConfigVersion(domain:String) = cachedConfigVersions.readThrough(domain, () => jooq.execute(t => {

    val result = t.select(DOMAINS.CONFIG_VERSION).
                   from(DOMAINS).
                   where(DOMAINS.NAME.equal(domain)).
                   fetchOne()

    if (result == null) {
      throw new MissingObjectException("domain")
    }
    else {
      result.getValue(DOMAINS.CONFIG_VERSION)
    }

  }))



  def allConfigOptions(domain:String) = cachedDomainConfigOptionsMap.readThrough(domain, () => jooq.execute( t => {
    val results = t.select(CONFIG_OPTIONS.OPT_KEY, CONFIG_OPTIONS.OPT_VAL).
      from(CONFIG_OPTIONS).
      where(CONFIG_OPTIONS.DOMAIN.equal(domain)).fetch()

    val configs = new java.util.HashMap[String,String]()

    results.iterator().foreach(r => {
      configs.put(r.getValue(CONFIG_OPTIONS.OPT_KEY), r.getValue(CONFIG_OPTIONS.OPT_VAL))
    })

    configs
  })).toMap


  def maybeConfigOption(domain:String, key:String) = {

    val option = cachedDomainConfigOptions.readThrough(DomainConfigKey(domain,key), () => jooq.execute( t => {

      val record = t.select(CONFIG_OPTIONS.OPT_VAL).
                     from(CONFIG_OPTIONS).
                     where(CONFIG_OPTIONS.DOMAIN.equal(domain)).
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
  }

  def configOptionOrDefault(domain:String, key: String, defaultVal: String) =
    maybeConfigOption(domain, key) match {
      case Some(str) => str
      case None      => defaultVal
    }

  def setConfigOption(domain:String, key:String, value:String) = {
    jooq.execute(t => {
      t.insertInto(CONFIG_OPTIONS).
        set(CONFIG_OPTIONS.DOMAIN, domain).
        set(CONFIG_OPTIONS.OPT_KEY, key).
        set(CONFIG_OPTIONS.OPT_VAL, value).
      onDuplicateKeyUpdate().
        set(CONFIG_OPTIONS.OPT_VAL, value).
      execute()
    })

    invalidateConfigCaches(domain)
  }

  def clearConfigOption(domain:String, key:String) = {
    jooq.execute(t => {
      t.delete(CONFIG_OPTIONS).
        where(CONFIG_OPTIONS.DOMAIN.equal(domain)).
        and(CONFIG_OPTIONS.OPT_KEY.equal(key)).
      execute()
    })

    // TODO This is a very coarse grained invalidation
    invalidateConfigCaches(domain)
  }

  /**
   * Force the DB to uprev the config version column for this particular domain
   */
  private def upgradeConfigVersion(t:Factory, domain:String) {

    cachedConfigVersions.evict(domain)

    t.update(DOMAINS).
      set(DOMAINS.CONFIG_VERSION, DOMAINS.CONFIG_VERSION.add(1)).
      where(DOMAINS.NAME.equal(domain)).
      execute()
  }

  def makeDomainMember(domain:String, userName:String) = {

    jooq.execute(t => {
      t.insertInto(MEMBERS).
        set(MEMBERS.DOMAIN_NAME, domain).
        set(MEMBERS.USER_NAME, userName).
        onDuplicateKeyIgnore().
        execute()
    })

    invalidateMembershipCache(domain)

    val member = Member(userName,domain)
    membershipListener.onMembershipCreated(member)
    member
  }

  def removeDomainMembership(domain:String, userName:String) = {

    jooq.execute(t => {
      t.delete(MEMBERS).
        where(MEMBERS.DOMAIN_NAME.equal(domain)).
        and(MEMBERS.USER_NAME.equal(userName)).
        execute()
    })

    invalidateMembershipCache(domain)

    val member = Member(userName,domain)
    membershipListener.onMembershipRemoved(member)
  }

  def listDomainMembers(domain:String) = cachedMembers.readThrough(domain, () => {
    jooq.execute(t => {

      val results = t.select(MEMBERS.USER_NAME).
                     from(MEMBERS).
                     where(MEMBERS.DOMAIN_NAME.equal(domain)).
                     fetch()

      val members = new java.util.ArrayList[Member]()
      results.iterator().foreach(r => members.add(Member(r.getValue(MEMBERS.USER_NAME), domain)))
      members

    })
  }).toSeq

  def isBreakerTripped(domain: String, pair: String, name: String) = {
    cachedBreakers.readThrough(BreakerKey(domain, pair, name), () => jooq.execute(t => {
      val c = t.selectCount().
        from(BREAKERS).
        where(BREAKERS.DOMAIN.equal(domain)).
          and(BREAKERS.PAIR_KEY.equal(pair)).
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
    if (!isBreakerTripped(domain, pair, name)) {
      jooq.execute(t => {
        t.insertInto(BREAKERS).
          set(Map(BREAKERS.DOMAIN -> domain, BREAKERS.PAIR_KEY -> pair, BREAKERS.NAME -> name)).
          onDuplicateKeyIgnore().
          execute()
      })

      cachedBreakers.put(BreakerKey(domain, pair, name), true)
    }
  }

  def clearBreaker(domain: String, pair: String, name: String) {
    if (isBreakerTripped(domain, pair, name)) {
      jooq.execute(t => {
        t.delete(BREAKERS).
          where(BREAKERS.DOMAIN.equal(domain)).
            and(BREAKERS.PAIR_KEY.equal(pair)).
            and(BREAKERS.NAME.equal(name)).
          execute()
      })

      cachedBreakers.put(BreakerKey(domain, pair, name), false)
    }
  }
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

case class DomainConfigKey(
  @BeanProperty var domain: String = null,
  @BeanProperty var configKey: String = null) {

  def this() = this(domain = null)

}

case class BreakerKey(
  @BeanProperty var domain:String = null,
  @BeanProperty var pair:String = null,
  @BeanProperty var name:String = null) {

  def this() = this(domain = null)
}

case class ConfigOptionByDomainPredicate(
  @BeanProperty domain:String = null) extends KeyPredicate[DomainConfigKey] {
  def this() = this(domain = null)
  def constrain(key: DomainConfigKey) = key.domain == domain
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

case class BreakerByDomainPredicate(@BeanProperty domain:String = null) extends KeyPredicate[BreakerKey] {
  def this() = this(domain = null)
  def constrain(key: BreakerKey) = key.domain == domain
}
case class BreakerByPairAndDomainPredicate(
    @BeanProperty domain:String = null,
    @BeanProperty pair:String = null) extends KeyPredicate[BreakerKey] {

  def this() = this(domain = null)
  def constrain(key: BreakerKey) = key.domain == domain && key.pair == pair
}
