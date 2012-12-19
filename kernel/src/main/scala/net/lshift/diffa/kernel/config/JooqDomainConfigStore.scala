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
import net.lshift.diffa.schema.jooq.{DatabaseFacade => JooqDatabaseFacade}
import net.lshift.diffa.schema.tables.Members.MEMBERS
import net.lshift.diffa.schema.tables.Policies.POLICIES
import net.lshift.diffa.schema.tables.ConfigOptions.CONFIG_OPTIONS
import net.lshift.diffa.schema.tables.RepairActions.REPAIR_ACTIONS
import net.lshift.diffa.schema.tables.Escalations.ESCALATIONS
import net.lshift.diffa.schema.tables.EscalationRules.ESCALATION_RULES
import net.lshift.diffa.schema.tables.PairReports.PAIR_REPORTS
import net.lshift.diffa.schema.tables.PairViews.PAIR_VIEWS
import net.lshift.diffa.schema.tables.Endpoints.ENDPOINTS
import net.lshift.diffa.schema.tables.Pairs.PAIRS
import net.lshift.diffa.schema.tables.Spaces.SPACES
import net.lshift.diffa.schema.tables.EndpointViews.ENDPOINT_VIEWS
import net.lshift.diffa.schema.tables.Breakers.BREAKERS
import net.lshift.diffa.schema.tables.Extents.EXTENTS
import JooqConfigStoreCompanion._
import net.lshift.diffa.kernel.naming.CacheName._
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.kernel.lifecycle.{PairLifecycleAware, DomainLifecycleAware}
import net.lshift.diffa.kernel.util.cache.{KeyPredicate, CacheProvider}
import reflect.BeanProperty
import java.util
import collection.mutable.ListBuffer
import org.jooq.impl.Factory
import org.jooq._
import java.lang.{Long => LONG}
import system.{ConflictingConcurrentModificationException, PolicyKey}
import java.lang.{Integer => INT}
import java.sql.SQLIntegrityConstraintViolationException
import org.jooq.exception.DataAccessException
import net.lshift.diffa.snowflake.IdProvider
import net.lshift.diffa.kernel.frontend.DomainPairDef
import net.lshift.diffa.kernel.frontend.EndpointDef
import net.lshift.diffa.kernel.frontend.PairDef
import scala.Some
import net.lshift.diffa.kernel.frontend.DomainEndpointDef

class JooqDomainConfigStore(jooq:JooqDatabaseFacade,
                            cacheProvider:CacheProvider,
                            idProvider:IdProvider,
                            membershipListener:DomainMembershipAware)
    extends DomainConfigStore
    with DomainLifecycleAware {

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
  private val cachedPolicies = cacheProvider.getCachedMap[SpacePolicyKey, PolicyKey](SPACE_POLICIES)

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
    cachedPolicies.evictAll()
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

  def createOrUpdateEndpoint(space: Long, endpointDef: EndpointDef) : DomainEndpointDef = {
    val id:LONG = idProvider.getId

    jooq.execute(t => {

      /* mergeInto should be used when there is merge support in MySQL. */
      if (jooq.resolvedDialect.equals(SQLDialect.MYSQL)) {
        val insert = t.insertInto(ENDPOINTS).
          set(ENDPOINTS.SPACE, space:LONG).
          set(ENDPOINTS.NAME, endpointDef.name).
          set(ENDPOINTS.ID, id).
          set(ENDPOINTS.COLLATION_TYPE, endpointDef.collation).
          set(ENDPOINTS.CONTENT_RETRIEVAL_URL, endpointDef.contentRetrievalUrl).
          set(ENDPOINTS.SCAN_URL, endpointDef.scanUrl).
          set(ENDPOINTS.VERSION_GENERATION_URL, endpointDef.versionGenerationUrl).
          set(ENDPOINTS.INBOUND_URL, endpointDef.inboundUrl)
        val update = t.update(ENDPOINTS).
          set(ENDPOINTS.COLLATION_TYPE, endpointDef.collation).
          set(ENDPOINTS.CONTENT_RETRIEVAL_URL, endpointDef.contentRetrievalUrl).
          set(ENDPOINTS.SCAN_URL, endpointDef.scanUrl).
          set(ENDPOINTS.VERSION_GENERATION_URL, endpointDef.versionGenerationUrl).
          set(ENDPOINTS.INBOUND_URL, endpointDef.inboundUrl).
          where(ENDPOINTS.SPACE.equal(space:LONG).and(ENDPOINTS.NAME.equal(endpointDef.name)))

        // This is not a fail-safe way to create or update.  It is subject to a race condition:
        // if the same endpoint is created between when the update fails and the insert is attempted,
        // then the unique key constraint violation will trigger a DataAccessException.  In this case,
        // we just let the user know it failed.
        val rowsUpdated = update.execute()
        if (rowsUpdated > 1) {
          throw new RuntimeException("Duplicate endpoint definition found for endpoint %s".format(endpointDef.name))
        } else if (rowsUpdated < 1) {
          try {
            insert.execute()
          } catch {
            case ex: DataAccessException =>
              log.warn("Potential concurrent modification inserting " + endpointDef.name, ex)
              throw new ConflictingConcurrentModificationException(endpointDef.name)
          }
        }
      } else {
        t.mergeInto(ENDPOINTS).
          usingDual().
          on(ENDPOINTS.SPACE.equal(space), ENDPOINTS.NAME.equal(endpointDef.name)).
          whenMatchedThenUpdate().
            set(ENDPOINTS.COLLATION_TYPE, endpointDef.collation).
            set(ENDPOINTS.CONTENT_RETRIEVAL_URL, endpointDef.contentRetrievalUrl).
            set(ENDPOINTS.SCAN_URL, endpointDef.scanUrl).
            set(ENDPOINTS.VERSION_GENERATION_URL, endpointDef.versionGenerationUrl).
            set(ENDPOINTS.INBOUND_URL, endpointDef.inboundUrl).
          whenNotMatchedThenInsert().
            set(ENDPOINTS.SPACE, space:LONG).
            set(ENDPOINTS.NAME, endpointDef.name).
            set(ENDPOINTS.ID, id).
            set(ENDPOINTS.COLLATION_TYPE, endpointDef.collation).
            set(ENDPOINTS.CONTENT_RETRIEVAL_URL, endpointDef.contentRetrievalUrl).
            set(ENDPOINTS.SCAN_URL, endpointDef.scanUrl).
            set(ENDPOINTS.VERSION_GENERATION_URL, endpointDef.versionGenerationUrl).
            set(ENDPOINTS.INBOUND_URL, endpointDef.inboundUrl).execute()
      }

      val endpointId = endpointIdByName(t, endpointDef.name, space)

      // Don't attempt to update to update any rows per se, just delete every associated
      // category and re-insert the new definitions, irrespective of
      // whether they are identical to the previous definitions

      deleteCategories(t, space, endpointDef.name)

      // Insert categories for the endpoint proper
      insertCategories(t, space, endpointId, endpointDef)

      // Update the view definitions

      t.delete(ENDPOINT_VIEWS).
          where(ENDPOINT_VIEWS.ENDPOINT.equal(endpointId)).
        execute()

      endpointDef.views.foreach(v => {
        t.insertInto(ENDPOINT_VIEWS).
            set(ENDPOINT_VIEWS.ENDPOINT, endpointId).
            set(ENDPOINT_VIEWS.NAME, v.name).
          execute()

          // Insert categories for the endpoint view
        insertCategoriesForView(t, space, endpointDef.name, endpointId, v)
      })

      upgradeConfigVersion(t, space)

    })

    invalidateEndpointCachesOnly(space, endpointDef.name)

    DomainEndpointDef(
      space = space,
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

  def deleteEndpoint(space:Long, endpoint: String) = {
    val upstream = ENDPOINTS.as("upstream")
    val downstream = ENDPOINTS.as("downstream")

    jooq.execute(t => {

      // Remove all pairs that reference the endpoint

      val results = t.select(PAIRS.SPACE, PAIRS.NAME).
                      from(PAIRS).
                      leftOuterJoin(upstream).on(upstream.ID.equal(PAIRS.UPSTREAM)).
                      leftOuterJoin(downstream).on(downstream.ID.equal(PAIRS.DOWNSTREAM)).
                      where(PAIRS.SPACE.equal(space)).
                        and(upstream.NAME.equal(endpoint).
                            or(downstream.NAME.equal(endpoint))).fetch()

      results.iterator().foreach(r => {
        val ref = PairRef(r.getValue(PAIRS.NAME), r.getValue(PAIRS.SPACE))
        deletePairWithDependencies(t, ref)
      })

      deleteCategories(t, space, endpoint)

      t.delete(ENDPOINT_VIEWS).
          where(ENDPOINT_VIEWS.ENDPOINT.equal(endpointIdByNameAsField(t, endpoint, space))).
        execute()

      val deleted = t.delete(ENDPOINTS).
                      where(ENDPOINTS.SPACE.equal(space)).
                        and(ENDPOINTS.NAME.equal(endpoint)).
                      execute()

      if (deleted == 0) {
        throw new MissingObjectException("endpoint")
      }

      upgradeConfigVersion(t, space)

    })

    invalidatePairCachesOnly(space)
    invalidateEndpointCachesOnly(space, endpoint)

  }

  def getEndpointDef(space:Long, endpoint: String) = {
    getEndpointDefWithDomainName(space, endpoint).withoutDomain()
  }

  @Deprecated private def getEndpointDefWithDomainName(space:Long, endpoint: String) = {

    cachedEndpointsByKey.readThrough(DomainEndpointKey(space, endpoint), () => {

      val endpoints = JooqConfigStoreCompanion.listEndpoints(jooq, Some(space), Some(endpoint))

      if (endpoints.isEmpty) {
        throw new MissingObjectException("endpoint")
      } else {
        endpoints.head
      }
    })

  }

  def listEndpoints(space:Long): Seq[EndpointDef] =
    cachedEndpoints.readThrough(space, () => JooqConfigStoreCompanion.listEndpoints(jooq, Some(space))).
      map(_.withoutDomain())

  def createOrUpdatePair(space:Long, pair: PairDef) = {

    pair.validate()

    jooq.execute(t => {
      val upstream = endpointIdByNameAsField(t, pair.upstreamName, space)
      val downstream = endpointIdByNameAsField(t, pair.downstreamName, space)

      // Attempt to prevent unnecessary sequence churn when updating pairs
      // TODO We should consider splitting out create and update APIs for records that use sequences
      val rows = t.update(PAIRS).
          set(PAIRS.UPSTREAM, upstream).
          set(PAIRS.DOWNSTREAM, downstream).
          set(PAIRS.ALLOW_MANUAL_SCANS, pair.allowManualScans).
          set(PAIRS.MATCHING_TIMEOUT, pair.matchingTimeout.asInstanceOf[Integer]).
          set(PAIRS.SCAN_CRON_SPEC, pair.scanCronSpec).
          set(PAIRS.SCAN_CRON_ENABLED, boolean2Boolean(pair.scanCronEnabled)).
          set(PAIRS.VERSION_POLICY_NAME, pair.versionPolicyName).
        where(PAIRS.SPACE.eq(space)).
          and(PAIRS.NAME.eq(pair.key)).
        execute()

      if (rows == 0) {

        val extent = upgradeExtent(t)

        t.insertInto(PAIRS).
            set(PAIRS.SPACE, space:LONG).
            set(PAIRS.NAME, pair.key).
            set(PAIRS.EXTENT, extent:LONG).
            set(PAIRS.UPSTREAM, upstream).
            set(PAIRS.DOWNSTREAM, downstream).
            set(PAIRS.ALLOW_MANUAL_SCANS, pair.allowManualScans).
            set(PAIRS.MATCHING_TIMEOUT, pair.matchingTimeout.asInstanceOf[Integer]).
            set(PAIRS.SCAN_CRON_SPEC, pair.scanCronSpec).
            set(PAIRS.SCAN_CRON_ENABLED, boolean2Boolean(pair.scanCronEnabled)).
            set(PAIRS.VERSION_POLICY_NAME, pair.versionPolicyName).
          onDuplicateKeyUpdate().
            set(PAIRS.UPSTREAM, upstream).
            set(PAIRS.DOWNSTREAM, downstream).
            set(PAIRS.ALLOW_MANUAL_SCANS, pair.allowManualScans).
            set(PAIRS.MATCHING_TIMEOUT, pair.matchingTimeout.asInstanceOf[Integer]).
            set(PAIRS.SCAN_CRON_SPEC, pair.scanCronSpec).
            set(PAIRS.SCAN_CRON_ENABLED, boolean2Boolean(pair.scanCronEnabled)).
            set(PAIRS.VERSION_POLICY_NAME, pair.versionPolicyName).
          execute()

      }

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
        PAIR_VIEWS.SPACE.equal(space).and(PAIR_VIEWS.PAIR.equal(pair.key)),
        PAIR_VIEWS.NAME)
      clearUnused(t, PAIR_REPORTS, pair.reports,
        PAIR_REPORTS.SPACE.equal(space).and(PAIR_REPORTS.PAIR.equal(pair.key)),
        PAIR_REPORTS.NAME)
      clearUnused(t, REPAIR_ACTIONS, pair.repairActions,
        REPAIR_ACTIONS.SPACE.equal(space).and(REPAIR_ACTIONS.PAIR.equal(pair.key)),
        REPAIR_ACTIONS.NAME)

      pair.views.foreach(v => {
        insertOrUpdate(t, PAIR_VIEWS,
          Map(PAIR_VIEWS.SPACE -> space, PAIR_VIEWS.PAIR -> pair.key, PAIR_VIEWS.NAME -> v.name),
          Map(PAIR_VIEWS.SCAN_CRON_SPEC -> v.scanCronSpec, PAIR_VIEWS.SCAN_CRON_ENABLED -> boolean2Boolean(v.scanCronEnabled)))
      })
      pair.repairActions.foreach(a => {
        insertOrUpdate(t, REPAIR_ACTIONS,
          Map(REPAIR_ACTIONS.SPACE -> space, REPAIR_ACTIONS.PAIR -> pair.key, REPAIR_ACTIONS.NAME -> a.name),
          Map(REPAIR_ACTIONS.URL -> a.url, REPAIR_ACTIONS.SCOPE -> a.scope))
      })
      pair.reports.foreach(r => {
        insertOrUpdate(t, PAIR_REPORTS,
          Map(PAIR_REPORTS.SPACE -> space, PAIR_REPORTS.PAIR -> pair.key, PAIR_REPORTS.NAME -> r.name),
          Map(PAIR_REPORTS.REPORT_TYPE -> r.reportType, PAIR_REPORTS.TARGET -> r.target))
      })

      // Clear all previous escalations and then re-add them

      t.update(ESCALATION_RULES).
          set(ESCALATION_RULES.EXTENT, null:LONG).
          set(ESCALATION_RULES.ESCALATION, null:String).
        where(ESCALATION_RULES.EXTENT.eq(
          t.select(PAIRS.EXTENT).
            from(PAIRS).
            where(PAIRS.SPACE.eq(space).
              and(PAIRS.NAME.eq(pair.key))).
            asField().
            asInstanceOf[Field[LONG]]
        )).execute()

      t.delete(ESCALATIONS).
        where(ESCALATIONS.EXTENT.eq(
        t.select(PAIRS.EXTENT).
          from(PAIRS).
          where(PAIRS.SPACE.eq(space).
            and(PAIRS.NAME.eq(pair.key))).
          asField().
          asInstanceOf[Field[LONG]]
      )).execute()

      pair.escalations.foreach(e => {

        // TODO somehow factor out the sub-select to avoid repetitions

        t.insertInto(ESCALATIONS).
            set(ESCALATIONS.NAME, e.name).
            set(ESCALATIONS.EXTENT,
              t.select(PAIRS.EXTENT).
                from(PAIRS).
                where(PAIRS.SPACE.eq(space).
                  and(PAIRS.NAME.eq(pair.key))).
                asField().
                asInstanceOf[Field[LONG]]).
            set(ESCALATIONS.ACTION, e.action).
            set(ESCALATIONS.ACTION_TYPE, e.actionType).
            set(ESCALATIONS.DELAY, e.delay:INT).
          onDuplicateKeyUpdate().
            set(ESCALATIONS.ACTION, e.action).
            set(ESCALATIONS.ACTION_TYPE, e.actionType).
            set(ESCALATIONS.DELAY, e.delay:INT).
          execute()

        val rule = if (e.rule == null) "*" else e.rule

        // Attempt an update to the escalation rules table first to avoid sequence churn
        // this means we'll have to potentially attempt the update twice, depending on concurrency

        val updateExistingRules
          = t.update(ESCALATION_RULES).
                set(ESCALATION_RULES.ESCALATION, ESCALATION_RULES.PREVIOUS_ESCALATION).
                set(ESCALATION_RULES.EXTENT, ESCALATION_RULES.PREVIOUS_EXTENT).
              where(ESCALATION_RULES.RULE.eq(rule)).
                and(ESCALATION_RULES.PREVIOUS_ESCALATION.eq(e.name)).
                and(ESCALATION_RULES.PREVIOUS_EXTENT.eq(
                  t.select(PAIRS.EXTENT).
                    from(PAIRS).
                    where(PAIRS.SPACE.eq(space).
                      and(PAIRS.NAME.eq(pair.key))).
                    asField().
                    asInstanceOf[Field[LONG]]
              ))

        val rows = updateExistingRules.execute()

        if (rows == 0) {

          // Has just the escalation name changed?

          val updatePreviousEscalationName
            = t.update(ESCALATION_RULES).
                  set(ESCALATION_RULES.ESCALATION, e.name).
                  set(ESCALATION_RULES.PREVIOUS_ESCALATION, e.name).
                  set(ESCALATION_RULES.EXTENT,
                    t.select(PAIRS.EXTENT).
                      from(PAIRS).
                      where(PAIRS.SPACE.eq(space).
                        and(PAIRS.NAME.eq(pair.key))).
                      asField().
                      asInstanceOf[Field[LONG]]
                  ).
                  where(ESCALATION_RULES.RULE.eq(rule)).
                    and(ESCALATION_RULES.PREVIOUS_EXTENT.eq(
                  t.select(PAIRS.EXTENT).
                    from(PAIRS).
                    where(PAIRS.SPACE.eq(space).
                      and(PAIRS.NAME.eq(pair.key))).
                    asField().
                    asInstanceOf[Field[LONG]]
                )).execute()

          if (updatePreviousEscalationName == 0) {

            val ruleId = idProvider.getId()

            try {
              t.insertInto(ESCALATION_RULES).
                set(ESCALATION_RULES.ID, ruleId:LONG).
                set(ESCALATION_RULES.EXTENT,
                  t.select(PAIRS.EXTENT).
                  from(PAIRS).
                  where(PAIRS.SPACE.eq(space).
                    and(PAIRS.NAME.eq(pair.key))).
                  asField().
                  asInstanceOf[Field[LONG]]).
                set(ESCALATION_RULES.PREVIOUS_EXTENT,
                  t.select(PAIRS.EXTENT).
                    from(PAIRS).
                    where(PAIRS.SPACE.eq(space).
                    and(PAIRS.NAME.eq(pair.key))).
                    asField().
                    asInstanceOf[Field[LONG]]).
                set(ESCALATION_RULES.ESCALATION, e.name).
                set(ESCALATION_RULES.PREVIOUS_ESCALATION, e.name).
                set(ESCALATION_RULES.RULE, rule).
              execute()
            }
            catch {
              case x:Exception if x.getCause.isInstanceOf[SQLIntegrityConstraintViolationException] => {
                // This should happen as a result of a race condition between the first attempt to update
                // and the attempt to insert a new row. If there is a genuine reason for the non-PK unique
                // constraint to have caused the constraint violation, then this second attempt to
                // update the rule will correctly result in the constraint beingu
                updateExistingRules.execute()
              }
            }
          }

        } else {

        }

      })

      upgradeConfigVersion(t, space)

    })

    invalidatePairCachesOnly(space)

    val ref = PairRef(space = space, name = pair.key)

    pairEventSubscribers.foreach(_.onPairUpdated(ref))
  }

  def deletePair(space:Long, key: String) = {
    jooq.execute(t => {
      val ref = PairRef(space = space, name = key)
      invalidatePairCachesOnly(space)
      deletePairWithDependencies(t, ref)
      upgradeConfigVersion(t, space)
      pairEventSubscribers.foreach(_.onPairDeleted(ref))
    })
  }

  def listPairs(space:Long) = {
    cachedPairs.readThrough(space, () => JooqConfigStoreCompanion.listPairs(jooq, space))
  }

  def listPairsForEndpoint(space:Long, endpoint:String) = {
    cachedPairsByEndpoint.readThrough(
      DomainEndpointKey(space, endpoint),
      () => JooqConfigStoreCompanion.listPairs(jooq, space, endpoint = Some(endpoint))
    )
  }


  @Deprecated def getEndpoint(space:Long, endpoint: String) = {

    val endpointDef = getEndpointDefWithDomainName(space, endpoint)

    val ep = Endpoint(
      name = endpointDef.name,
      domain = Domain(name = endpointDef.domain),
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


  def getPairDef(space:Long, key: String) = {

    cachedPairsByKey.readThrough(DomainPairKey(space, key), () => jooq.execute { t =>
      val pairs = JooqConfigStoreCompanion.listPairs(jooq, space, key = Some(key))
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

  def getConfigVersion(space:Long) = {

    cachedConfigVersions.readThrough(space, () => jooq.execute(t => {

      val result = t.select(SPACES.CONFIG_VERSION).
        from(SPACES).
        where(SPACES.ID.equal(space)).
        fetchOne()

      if (result == null) {
        throw new MissingObjectException("domain")
      }
      else {
        result.getValue(SPACES.CONFIG_VERSION)
      }

    }))
  }

  def allConfigOptions(space:Long) = {

    cachedDomainConfigOptionsMap.readThrough(space, () => jooq.execute( t => {
      val results = t.select(CONFIG_OPTIONS.OPT_KEY, CONFIG_OPTIONS.OPT_VAL).
        from(CONFIG_OPTIONS).
        where(CONFIG_OPTIONS.SPACE.equal(space)).fetch()

      val configs = new java.util.HashMap[String,String]()

      results.iterator().foreach(r => {
        configs.put(r.getValue(CONFIG_OPTIONS.OPT_KEY), r.getValue(CONFIG_OPTIONS.OPT_VAL))
      })

      configs
    })).toMap

  }


  def maybeConfigOption(space:Long, key:String) = {

    val option = cachedDomainConfigOptions.readThrough(DomainConfigKey(space,key), () => jooq.execute( t => {

      val record = t.select(CONFIG_OPTIONS.OPT_VAL).
                     from(CONFIG_OPTIONS).
                     where(CONFIG_OPTIONS.SPACE.equal(space)).
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

  def configOptionOrDefault(space:Long, key: String, defaultVal: String) =
    maybeConfigOption(space, key) match {
      case Some(str) => str
      case None      => defaultVal
    }

  def setConfigOption(space:Long, key:String, value:String) = {

    jooq.execute(t => {
      t.insertInto(CONFIG_OPTIONS).
        set(CONFIG_OPTIONS.SPACE, space:LONG).
        set(CONFIG_OPTIONS.OPT_KEY, key).
        set(CONFIG_OPTIONS.OPT_VAL, value).
      onDuplicateKeyUpdate().
        set(CONFIG_OPTIONS.OPT_VAL, value).
      execute()
    })

    invalidateConfigCaches(space)
  }

  def clearConfigOption(space:Long, key:String) = {

    jooq.execute(t => {
      t.delete(CONFIG_OPTIONS).
        where(CONFIG_OPTIONS.SPACE.equal(space)).
        and(CONFIG_OPTIONS.OPT_KEY.equal(key)).
      execute()
    })

    // TODO This is a very coarse grained invalidation
    invalidateConfigCaches(space)
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

  def lookupPolicy(space:Long, policy:String) = {
    cachedPolicies.readThrough(SpacePolicyKey(space, policy), () => {
      jooq.execute(t => {
        def searchSpace(spaceId:Long) = {
          val result = t.select().
                       from(POLICIES).
                       where(POLICIES.SPACE.equal(spaceId).and(POLICIES.NAME.equal(policy))).
                       fetchOne()
          if (result == null) {
            None
          } else {
            Some(PolicyKey(spaceId, policy))
          }
        }
        def searchSpaceOrParent(spaceId:Long, tree:Seq[Long]):PolicyKey = {
          searchSpace(spaceId) match {
            case Some(k) => k
            case None =>
              if (tree.length == 0) {
                throw new MissingObjectException("policy " + policy)
              } else {
                searchSpaceOrParent(tree.head, tree.tail)
              }
          }
        }

        val tree = ancestorIdTree(t, space)
        searchSpaceOrParent(space, tree)
      })
    })
  }

  def makeDomainMember(space:Long, userName:String, policy:PolicyKey) = {

    jooq.execute(t => {
      t.insertInto(MEMBERS).
        set(MEMBERS.SPACE, space:LONG).
        set(MEMBERS.USERNAME, userName).
        set(MEMBERS.POLICY_SPACE, policy.space).
        set(MEMBERS.POLICY, policy.name).
        onDuplicateKeyIgnore().
        execute()
    })

    invalidateMembershipCache(space)

    val member = Member(userName, space, policy.space, policy.name, resolveSpaceName(space))
    membershipListener.onMembershipCreated(member)
    member
  }

  def removeDomainMembership(space:Long, userName:String, policy:String) {

    jooq.execute(t => {
      t.delete(MEMBERS).
        where(MEMBERS.SPACE.equal(space)).
          and(MEMBERS.USERNAME.equal(userName)).
          and(MEMBERS.POLICY.equal(policy)).
        execute()
    })

    invalidateMembershipCache(space)

      // TODO: This should include the right space id
    val member = Member(userName, space, space, policy, resolveSpaceName(space))
    membershipListener.onMembershipRemoved(member)
  }

  /**
   * TODO This should be cached and centralized
   */
  @Deprecated private def resolveSpaceName(space:Long) = {
    jooq.execute(t => {
      val record =  t.select(SPACES.NAME).
                      from(SPACES).
                      where(SPACES.ID.equal(space)).
                      fetchOne()

      if (record == null) {
        throw new MissingObjectException(space.toString)
      }
      else {
        record.getValue(SPACES.NAME)
      }
    })
  }

  def listDomainMembers(space:Long) = {

    cachedMembers.readThrough(space, () => {

      jooq.execute(t => {

        val results = t.select(MEMBERS.USERNAME, MEMBERS.POLICY_SPACE, MEMBERS.POLICY).
                        select(SPACES.NAME).
                        from(MEMBERS).
                        join(SPACES).
                          on(SPACES.ID.equal(MEMBERS.SPACE)).
                        where(MEMBERS.SPACE.equal(space)).
                        fetch()

        val members = new java.util.ArrayList[Member]()
        results.iterator().foreach(r => members.add(Member(
          user = r.getValue(MEMBERS.USERNAME),
          space = space,
          domain = r.getValue(SPACES.NAME),
          policySpace = r.getValue(MEMBERS.POLICY_SPACE),
          policy = r.getValue(MEMBERS.POLICY)
        ))
        )
        members

      })
    }).toSeq
  }

  def isBreakerTripped(space:Long, pair: String, name: String) = {

    cachedBreakers.readThrough(BreakerKey(space, pair, name), () => jooq.execute(t => {
      val c = t.selectCount().
        from(BREAKERS).
        where(BREAKERS.SPACE.equal(space)).
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

  def tripBreaker(space:Long, pair: String, name: String) {

    if (!isBreakerTripped(space, pair, name)) {
      jooq.execute(t => {
        t.insertInto(BREAKERS).
            set(BREAKERS.SPACE, space:LONG).
            set(BREAKERS.PAIR, pair).
            set(BREAKERS.NAME, name).
          onDuplicateKeyIgnore().
          execute()
      })

      cachedBreakers.put(BreakerKey(space, pair, name), true)
    }
  }

  def clearBreaker(space:Long, pair: String, name: String) {

    if (isBreakerTripped(space, pair, name)) {
      jooq.execute(t => {
        t.delete(BREAKERS).
          where(BREAKERS.SPACE.equal(space)).
            and(BREAKERS.PAIR.equal(pair)).
            and(BREAKERS.NAME.equal(name)).
          execute()
      })

      cachedBreakers.put(BreakerKey(space, pair, name), false)
    }
  }

  private def upgradeExtent(t:Factory) : Long = {

    val extent = idProvider.getId()

    t.insertInto(EXTENTS).
        set(EXTENTS.ID, extent:LONG).
      onDuplicateKeyIgnore().
      execute()

    extent
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

case class SpacePolicyKey(
  @BeanProperty var space: Long,
  @BeanProperty var policy: String = null) {

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
