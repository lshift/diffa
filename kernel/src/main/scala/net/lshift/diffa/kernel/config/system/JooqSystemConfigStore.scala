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

package net.lshift.diffa.kernel.config.system

import scala.collection.JavaConversions._
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.util.{AlertCodes, MissingObjectException}
import org.apache.commons.lang.RandomStringUtils
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.schema.jooq.{DatabaseFacade => JooqDatabaseFacade}
import net.lshift.diffa.schema.tables.UserItemVisibility.USER_ITEM_VISIBILITY
import net.lshift.diffa.schema.tables.Breakers.BREAKERS
import net.lshift.diffa.schema.tables.ExternalHttpCredentials.EXTERNAL_HTTP_CREDENTIALS
import net.lshift.diffa.schema.tables.PairReports.PAIR_REPORTS
import net.lshift.diffa.schema.tables.Escalations.ESCALATIONS
import net.lshift.diffa.schema.tables.RepairActions.REPAIR_ACTIONS
import net.lshift.diffa.schema.tables.PairViews.PAIR_VIEWS
import net.lshift.diffa.schema.tables.Pair.PAIR
import net.lshift.diffa.schema.tables.PrefixCategories.PREFIX_CATEGORIES
import net.lshift.diffa.schema.tables.PrefixCategoryViews.PREFIX_CATEGORY_VIEWS
import net.lshift.diffa.schema.tables.SetCategories.SET_CATEGORIES
import net.lshift.diffa.schema.tables.SetCategoryViews.SET_CATEGORY_VIEWS
import net.lshift.diffa.schema.tables.RangeCategories.RANGE_CATEGORIES
import net.lshift.diffa.schema.tables.RangeCategoryViews.RANGE_CATEGORY_VIEWS
import net.lshift.diffa.schema.tables.EndpointViewRollingWindows.ENDPOINT_VIEW_ROLLING_WINDOWS
import net.lshift.diffa.schema.tables.UniqueCategoryNames.UNIQUE_CATEGORY_NAMES
import net.lshift.diffa.schema.tables.UniqueCategoryViewNames.UNIQUE_CATEGORY_VIEW_NAMES
import net.lshift.diffa.schema.tables.EndpointViews.ENDPOINT_VIEWS
import net.lshift.diffa.schema.tables.Endpoint.ENDPOINT
import net.lshift.diffa.schema.tables.ConfigOptions.CONFIG_OPTIONS
import net.lshift.diffa.schema.tables.Members.MEMBERS
import net.lshift.diffa.schema.tables.StoreCheckpoints.STORE_CHECKPOINTS
import net.lshift.diffa.schema.tables.PendingDiffs.PENDING_DIFFS
import net.lshift.diffa.schema.tables.Diffs.DIFFS
import net.lshift.diffa.schema.tables.Domains.DOMAINS
import net.lshift.diffa.schema.tables.SystemConfigOptions.SYSTEM_CONFIG_OPTIONS
import net.lshift.diffa.schema.tables.Users.USERS
import net.lshift.diffa.kernel.lifecycle.DomainLifecycleAware
import collection.mutable.ListBuffer
import net.lshift.diffa.kernel.util.cache.CacheProvider
import net.lshift.diffa.kernel.naming.CacheName._
import net.lshift.diffa.kernel.frontend.DomainEndpointDef
import net.lshift.diffa.kernel.config.Member
import net.lshift.diffa.kernel.config.User
import org.jooq.{TableField, Record}
import net.lshift.diffa.schema.tables.records.UsersRecord

class JooqSystemConfigStore(jooq:JooqDatabaseFacade,
                            cacheProvider:CacheProvider)
    extends SystemConfigStore {

  val logger = LoggerFactory.getLogger(getClass)

  private val cachedDomainNames = cacheProvider.getCachedMap[String, java.lang.Long](EXISTING_DOMAIN_NAMES)

  private val domainEventSubscribers = new ListBuffer[DomainLifecycleAware]

  def registerDomainEventListener(d:DomainLifecycleAware) = domainEventSubscribers += d

  def createOrUpdateDomain(domain:String) = {

    jooq.execute(t => {

      t.insertInto(DOMAINS).
        set(DOMAINS.NAME, domain).
        onDuplicateKeyIgnore().
        execute()
    })

    cachedDomainNames.evict(domain)
    domainEventSubscribers.foreach(_.onDomainUpdated(domain))
  }

  def deleteDomain(domain:String) = {

    jooq.execute(t => {
      t.delete(EXTERNAL_HTTP_CREDENTIALS).where(EXTERNAL_HTTP_CREDENTIALS.DOMAIN.equal(domain)).execute()
      t.delete(USER_ITEM_VISIBILITY).where(USER_ITEM_VISIBILITY.DOMAIN.equal(domain)).execute()
      t.delete(PREFIX_CATEGORY_VIEWS).where(PREFIX_CATEGORY_VIEWS.DOMAIN.equal(domain)).execute()
      t.delete(PREFIX_CATEGORIES).where(PREFIX_CATEGORIES.DOMAIN.equal(domain)).execute()
      t.delete(SET_CATEGORY_VIEWS).where(SET_CATEGORY_VIEWS.DOMAIN.equal(domain)).execute()
      t.delete(SET_CATEGORIES).where(SET_CATEGORIES.DOMAIN.equal(domain)).execute()
      t.delete(RANGE_CATEGORY_VIEWS).where(RANGE_CATEGORY_VIEWS.DOMAIN.equal(domain)).execute()
      t.delete(RANGE_CATEGORIES).where(RANGE_CATEGORIES.DOMAIN.equal(domain)).execute()
      t.delete(ENDPOINT_VIEW_ROLLING_WINDOWS).where(ENDPOINT_VIEW_ROLLING_WINDOWS.DOMAIN.equal(domain)).execute()
      t.delete(UNIQUE_CATEGORY_VIEW_NAMES).where(UNIQUE_CATEGORY_VIEW_NAMES.DOMAIN.equal(domain)).execute()
      t.delete(UNIQUE_CATEGORY_NAMES).where(UNIQUE_CATEGORY_NAMES.DOMAIN.equal(domain)).execute()
      t.delete(ENDPOINT_VIEWS).where(ENDPOINT_VIEWS.DOMAIN.equal(domain)).execute()
      t.delete(PAIR_REPORTS).where(PAIR_REPORTS.DOMAIN.equal(domain)).execute()
      t.delete(ESCALATIONS).where(ESCALATIONS.DOMAIN.equal(domain)).execute()
      t.delete(REPAIR_ACTIONS).where(REPAIR_ACTIONS.DOMAIN.equal(domain)).execute()
      t.delete(PAIR_VIEWS).where(PAIR_VIEWS.DOMAIN.equal(domain)).execute()
      t.delete(BREAKERS).where(BREAKERS.DOMAIN.equal(domain)).execute()
      t.delete(PAIR).where(PAIR.DOMAIN.equal(domain)).execute()
      t.delete(ENDPOINT).where(ENDPOINT.DOMAIN.equal(domain)).execute()
      t.delete(CONFIG_OPTIONS).where(CONFIG_OPTIONS.DOMAIN.equal(domain)).execute()
      t.delete(MEMBERS).where(MEMBERS.DOMAIN_NAME.equal(domain)).execute()
      t.delete(STORE_CHECKPOINTS).where(STORE_CHECKPOINTS.DOMAIN.equal(domain)).execute()
      t.delete(PENDING_DIFFS).where(PENDING_DIFFS.DOMAIN.equal(domain)).execute()
      t.delete(DIFFS).where(DIFFS.DOMAIN.equal(domain)).execute()

      val deleted = t.delete(DOMAINS).where(DOMAINS.NAME.equal(domain)).execute()

      if (deleted == 0) {
        logger.error("%s: Attempt to delete non-existent domain: %s".format(AlertCodes.INVALID_DOMAIN, domain))
        throw new MissingObjectException(domain)
      }
    })

    domainEventSubscribers.foreach(_.onDomainRemoved(domain))

    cachedDomainNames.evict(domain)
  }

  def doesDomainExist(domain: String) = {
    val count = cachedDomainNames.readThrough(domain, () => jooq.execute(t => {
      t.selectCount().
        from(DOMAINS).
        where(DOMAINS.NAME.equal(domain)).
        fetchOne().
        getValueAsBigInteger(0).
        longValue()
    }))

    count > 0
  }

  def listDomains = jooq.execute( t => {
    t.select(DOMAINS.NAME).
      from(DOMAINS).
      orderBy(DOMAINS.NAME).
      fetch().
      iterator().map(_.getValue(DOMAINS.NAME)).toSeq
  })

  def listPairs = jooq.execute { t =>
    t.select().from(PAIR).fetch().map(ResultMappingUtil.recordToDomainPairDef)
  }

  def listEndpoints : Seq[DomainEndpointDef] = JooqConfigStoreCompanion.listEndpoints(jooq)

  def createOrUpdateUser(user: User) = jooq.execute(t => {
    t.insertInto(USERS).
        set(USERS.EMAIL, user.email).
        set(USERS.NAME, user.name).
        set(USERS.PASSWORD_ENC, user.passwordEnc).
        set(USERS.SUPERUSER, boolean2Boolean(user.superuser)).
      onDuplicateKeyUpdate().
        set(USERS.EMAIL, user.email).
        set(USERS.PASSWORD_ENC, user.passwordEnc).
        set(USERS.SUPERUSER, boolean2Boolean(user.superuser)).
      execute()
  })

  def getUserToken(username: String) = jooq.execute(t => {
    val token = t.select(USERS.TOKEN).
                  from(USERS).
                  where(USERS.NAME.equal(username)).
                  fetchOne().
                  getValue(USERS.TOKEN)

    if (token == null) {
      // Generate token on demand
      val newToken = RandomStringUtils.randomAlphanumeric(40)

      t.update(USERS).
        set(USERS.TOKEN, newToken).
        where(USERS.NAME.equal(username)).
        execute()

      newToken
    }
    else {
      token
    }
  })

  def clearUserToken(username: String) = jooq.execute(t => {
    val nullString:String = null
    t.update(USERS).
        set(USERS.TOKEN, nullString).
      where(USERS.NAME.equal(username)).
      execute()
  })

  def deleteUser(username: String) = jooq.execute(t => {
    t.delete(USERS).
      where(USERS.NAME.equal(username)).
      execute()
  })

  def getUser(name: String) : User = getUserByPredicate(name, USERS.NAME)
  def getUserByToken(token: String) : User = getUserByPredicate(token, USERS.TOKEN)

  def listUsers : Seq[User] = jooq.execute(t => {
    val results = t.select().
                    from(USERS).
                    fetch()
    results.iterator().map(recordToUser(_)).toSeq
  })

  def listDomainMemberships(username: String) : Seq[Member] = {
    jooq.execute(t => {
      val results = t.select(MEMBERS.DOMAIN_NAME).
                      from(MEMBERS).
                      where(MEMBERS.USER_NAME.equal(username)).
                      fetch()
      results.iterator().map(r => Member(username, r.getValue(MEMBERS.DOMAIN_NAME)))
    }).toSeq
  }

  def containsRootUser(usernames: Seq[String]) : Boolean = jooq.execute(t => {
    val count = t.selectCount().
                  from(USERS).
                  where(USERS.NAME.in(usernames)).
                    and(USERS.SUPERUSER.isTrue).
                  fetchOne().getValueAsBigInteger(0).longValue()
    count > 0
  })

  def maybeSystemConfigOption(key: String) = jooq.execute( t => {
    val record = t.select(SYSTEM_CONFIG_OPTIONS.OPT_VAL).
      from(SYSTEM_CONFIG_OPTIONS).
      where(SYSTEM_CONFIG_OPTIONS.OPT_KEY.equal(key)).
      fetchOne()

    if (record != null) {
      Some(record.getValue(SYSTEM_CONFIG_OPTIONS.OPT_VAL))
    }
    else {
      None
    }
  })

  def systemConfigOptionOrDefault(key:String, defaultVal:String) = {
    maybeSystemConfigOption(key) match {
      case Some(str) => str
      case None      => defaultVal
    }
  }

  def setSystemConfigOption(key:String, value:String) = jooq.execute(t => {
    t.insertInto(SYSTEM_CONFIG_OPTIONS).
        set(SYSTEM_CONFIG_OPTIONS.OPT_KEY, key).
        set(SYSTEM_CONFIG_OPTIONS.OPT_VAL, value).
      onDuplicateKeyUpdate().
        set(SYSTEM_CONFIG_OPTIONS.OPT_VAL, value).
      execute()
  })

  def clearSystemConfigOption(key:String) = jooq.execute(t => {
    t.delete(SYSTEM_CONFIG_OPTIONS).
      where(SYSTEM_CONFIG_OPTIONS.OPT_KEY.equal(key)).
      execute()
  })

  private def getUserByPredicate(predicate: String, fieldToMatch:TableField[UsersRecord, String]) : User = jooq.execute(t => {
    val record =  t.select().
                    from(USERS).
                    where(fieldToMatch.equal(predicate)).
                    fetchOne()

    if (record == null) {
      throw new MissingObjectException("user")
    }
    else {
      recordToUser(record)
    }
  })

  private def recordToUser(record:Record) = {
    User(
      name = record.getValue(USERS.NAME),
      email = record.getValue(USERS.EMAIL),
      token = record.getValue(USERS.TOKEN),
      superuser = record.getValue(USERS.SUPERUSER),
      passwordEnc = record.getValue(USERS.PASSWORD_ENC)
    )
  }
}

/**
 * Indicates that the system not configured correctly
 */
class InvalidSystemConfigurationException(msg:String) extends RuntimeException(msg)
