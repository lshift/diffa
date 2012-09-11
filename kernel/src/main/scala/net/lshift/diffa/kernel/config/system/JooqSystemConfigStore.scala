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

import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.util.AlertCodes._
import net.lshift.diffa.kernel.util.MissingObjectException
import org.apache.commons.lang.RandomStringUtils
import net.lshift.diffa.kernel.config._

import net.lshift.diffa.schema.jooq.{DatabaseFacade => JooqDatabaseFacade}
import net.lshift.diffa.schema.tables.UserItemVisibility.USER_ITEM_VISIBILITY
import net.lshift.diffa.schema.tables.Breakers.BREAKERS
import net.lshift.diffa.schema.tables.ExternalHttpCredentials.EXTERNAL_HTTP_CREDENTIALS
import net.lshift.diffa.schema.tables.PairReports.PAIR_REPORTS
import net.lshift.diffa.schema.tables.Escalations.ESCALATIONS
import net.lshift.diffa.schema.tables.EscalationRules.ESCALATION_RULES
import net.lshift.diffa.schema.tables.RepairActions.REPAIR_ACTIONS
import net.lshift.diffa.schema.tables.PairViews.PAIR_VIEWS
import net.lshift.diffa.schema.tables.Pairs.PAIRS
import net.lshift.diffa.schema.tables.PrefixCategories.PREFIX_CATEGORIES
import net.lshift.diffa.schema.tables.PrefixCategoryViews.PREFIX_CATEGORY_VIEWS
import net.lshift.diffa.schema.tables.SetCategories.SET_CATEGORIES
import net.lshift.diffa.schema.tables.SetCategoryViews.SET_CATEGORY_VIEWS
import net.lshift.diffa.schema.tables.RangeCategories.RANGE_CATEGORIES
import net.lshift.diffa.schema.tables.RangeCategoryViews.RANGE_CATEGORY_VIEWS
import net.lshift.diffa.schema.tables.UniqueCategoryNames.UNIQUE_CATEGORY_NAMES
import net.lshift.diffa.schema.tables.UniqueCategoryViewNames.UNIQUE_CATEGORY_VIEW_NAMES
import net.lshift.diffa.schema.tables.EndpointViews.ENDPOINT_VIEWS
import net.lshift.diffa.schema.tables.Endpoints.ENDPOINTS
import net.lshift.diffa.schema.tables.ConfigOptions.CONFIG_OPTIONS
import net.lshift.diffa.schema.tables.Members.MEMBERS
import net.lshift.diffa.schema.tables.Policies.POLICIES
import net.lshift.diffa.schema.tables.PolicyStatements.POLICY_STATEMENTS
import net.lshift.diffa.schema.tables.StoreCheckpoints.STORE_CHECKPOINTS
import net.lshift.diffa.schema.tables.PendingDiffs.PENDING_DIFFS
import net.lshift.diffa.schema.tables.Spaces.SPACES
import net.lshift.diffa.schema.tables.SpacePaths.SPACE_PATHS
import net.lshift.diffa.schema.tables.SystemConfigOptions.SYSTEM_CONFIG_OPTIONS
import net.lshift.diffa.schema.tables.Users.USERS
import net.lshift.diffa.kernel.lifecycle.DomainLifecycleAware
import collection.mutable.ListBuffer
import net.lshift.diffa.kernel.util.cache.CacheProvider
import net.lshift.diffa.schema.tables.records.UsersRecord
import net.lshift.diffa.kernel.util.sequence.SequenceProvider
import java.lang.{Long => LONG, Integer => INT}
import org.jooq.exception.DataAccessException
import java.sql.SQLIntegrityConstraintViolationException
import org.jooq.impl.Factory._
import scala.Some
import net.lshift.diffa.kernel.config.Member
import net.lshift.diffa.kernel.config.User
import net.lshift.diffa.kernel.frontend.DomainEndpointDef
import net.lshift.diffa.kernel.naming.{CacheName, SequenceName}
import org.jooq.impl.Factory
import org.jooq._
import collection.JavaConversions._
import net.lshift.diffa.kernel.config.JooqConfigStoreCompanion.{ancestorIdTree}

class JooqSystemConfigStore(jooq:JooqDatabaseFacade,
                            cacheProvider:CacheProvider,
                            sequenceProvider:SequenceProvider)
    extends SystemConfigStore {

  val logger = LoggerFactory.getLogger(getClass)

  val ROOT_SPACE = Space(id = 0L, parent = 0L, name = "diffa")
  val NON_EXISTENT_SPACE = Space(id = -1L, parent = -1L, name = "does_not_exist")

  private val spacePathCache = cacheProvider.getCachedMap[String,Space](CacheName.SPACE_PATHS)
  private val spaceIdCache = cacheProvider.getCachedMap[java.lang.Long,Space](CacheName.SPACE_IDS)

  initializeExistingSequences()

  private val domainEventSubscribers = new ListBuffer[DomainLifecycleAware]

  def registerDomainEventListener(d:DomainLifecycleAware) = domainEventSubscribers += d

  private def invalidateSpaceByIdCache(id:Long) = {
    spaceIdCache.evict(id)
  }

  private def invalidateSpaceByPathCache(path:String) = {
    spacePathCache.evict(path)
  }

  private def invalidateSpaceByPathCache(id:Long) = {
    // TODO For now, we'll ignore the id, but this might have an effect on performance
    spacePathCache.evictAll()
  }

  private def invalidateCaches(id:Long) = {
    invalidateSpaceByIdCache(id)
    invalidateSpaceByPathCache(id)
  }

  def reset {
    spaceIdCache.evictAll()
    spacePathCache.evictAll()
  }

  def createOrUpdateDomain(path:String) = createOrUpdateSpace(path)

  def createOrUpdateSpace(path:String) = {

    val lastSlash = path.lastIndexOf("/")

    if (lastSlash > 0) {

      val parentPath = path.substring(0, lastSlash)
      val childPath = path.substring(lastSlash + 1)

      ValidationUtil.ensurePathSegmentFormat("spaces", childPath)

      jooq.execute(t => {
        val parent = lookupSpaceId(t, parentPath + "%")
        createSpace(t, childPath, parent.id, path)
      })
    }

    else {

      ValidationUtil.ensurePathSegmentFormat("spaces", path)
      jooq.execute(createSpace(_, path, ROOT_SPACE.id, path))

    }

  }

  def deleteSpace(id: Long) = {
    jooq.execute(t => {
      descendancyTree(t, id).foreach(s => {
        invalidateCaches(s.id)
        deleteSingleSpace(t, s.id)
        domainEventSubscribers.foreach(_.onDomainRemoved(s.id))
      })
    })
  }

  def listSubspaces(parent:Long) = jooq.execute(descendancyTree(_, parent)).toSeq
  def listSuperspaceIds(parent:Long) = jooq.execute(ancestorIdTree(_, parent)).toSeq

  def lookupSpaceByPath(path: String) = {
    jooq.execute(lookupSpaceId(_, path))
  }

  def lookupSpacePathById(space: Long) = {
    jooq.execute( t => {

      val spacePath = Factory.groupConcat(SPACES.NAME).orderBy(SPACES.ID.asc()).separator("/")

      val path =
        t.select(spacePath.as("path")).
          from(SPACES).
          join(SPACE_PATHS).
            on(SPACE_PATHS.ANCESTOR.eq(SPACES.ID)).
          where(SPACE_PATHS.DESCENDANT.eq(space)).
            and(SPACE_PATHS.ANCESTOR.ne(0)).
          groupBy(SPACE_PATHS.DESCENDANT).
          fetchOne()

      if (path == null) {
        throw new MissingObjectException(space.toString)
      }
      else {
        path.getValueAsString("path")
      }

    })
  }

  def doesDomainExist(path: String) = resolveSpaceByPath(path) match {
    case NON_EXISTENT_SPACE => false
    case _                  => true
  }

  def doesSpaceExist(id: Long) = resolveSpaceById(id) match {
    case NON_EXISTENT_SPACE => false
    case _                  => true
  }

  def listDomains = listSpaces.map(_.name)

  def listSpaces = jooq.execute( t => {
    t.select().
      from(SPACES).
      orderBy(SPACES.NAME).
      fetch().
      iterator().map(s => Space(
        id = s.getValue(SPACES.ID),
        parent = s.getValue(SPACES.PARENT),
        name = s.getValue(SPACES.NAME),
        configVersion = s.getValue(SPACES.CONFIG_VERSION)
      )).toSeq
  })

  def listPairs = jooq.execute { t =>
    t.select(PAIRS.getFields).select(SPACES.NAME).
      from(PAIRS).
      join(SPACES).on(SPACES.ID.equal(PAIRS.SPACE)).
      fetch().
      map(ResultMappingUtil.recordToDomainPairDef)
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
      val results = t.select(MEMBERS.SPACE, MEMBERS.POLICY, MEMBERS.POLICY_SPACE, SPACES.NAME).
                      from(MEMBERS).
                      join(SPACES).on(SPACES.ID.equal(MEMBERS.SPACE)).
                      where(MEMBERS.USERNAME.equal(username)).
                      fetch()
      results.iterator().map(r => Member(
        user = username,
        space = r.getValue(MEMBERS.SPACE),
        policySpace = r.getValue(MEMBERS.POLICY_SPACE),
        policy = r.getValue(MEMBERS.POLICY),

        // TODO Ideally we shouldn't need to do this join, since the domain field is deprecated
        // and consumers of this call should be able to deal with the surrogate space id, but
        // for now that creates further churn in the patch to land space ids, so we'll just backplane the
        // the existing behavior
        domain = r.getValue(SPACES.NAME)
      ))
    }).toSeq
  }

  def lookupPolicyStatements(policy:PolicyKey) = {
    jooq.execute(t => lookupPolicyStatements(policy, t))
  }

  def lookupPolicyStatements(policy:PolicyKey, t:Factory) = {
    val results = t.select().
                      from(POLICY_STATEMENTS).
                      where(POLICY_STATEMENTS.SPACE.equal(policy.space).and(POLICY_STATEMENTS.POLICY.equal(policy.name))).
                      fetch()

    results.iterator().map(recordToPolicyStatement(_)).toSeq
  }

  def storePolicy(policy:PolicyKey, stmts:Seq[PolicyStatement]) {
    jooq.execute(t => {
      // Remove all missing policy statements
      val existing = lookupPolicyStatements(policy, t)
      val toRemove:Set[PolicyStatement] = existing.toSet -- stmts
      val toAdd:Set[PolicyStatement] = stmts.toSet -- existing

      toRemove.foreach(r => {
        t.delete(POLICY_STATEMENTS).
          where(POLICY_STATEMENTS.SPACE.equal(policy.space).and(POLICY_STATEMENTS.POLICY.equal(policy.name))).
            and(POLICY_STATEMENTS.PRIVILEGE.equal(r.privilege)).
            and(POLICY_STATEMENTS.TARGET.equal(r.target)).
          execute()
      })

      t.insertInto(POLICIES).
        set(Map(POLICIES.SPACE -> policy.space, POLICIES.NAME -> policy.name)).
        onDuplicateKeyIgnore().
        execute()

      toAdd.foreach(a => {
        t.insertInto(POLICY_STATEMENTS).
          set(Map(
            POLICY_STATEMENTS.SPACE -> policy.space,
            POLICY_STATEMENTS.POLICY -> policy.name,
            POLICY_STATEMENTS.PRIVILEGE -> a.privilege,
            POLICY_STATEMENTS.TARGET -> a.target
          )).
          execute()
      })
    })
  }

  def removePolicy(policy:PolicyKey) {
    jooq.execute(t => {
      t.delete(POLICY_STATEMENTS).
        where(POLICY_STATEMENTS.SPACE.equal(policy.space).and(POLICY_STATEMENTS.POLICY.equal(policy.name))).
        execute()
      t.delete(POLICIES).
        where(POLICIES.SPACE.equal(policy.space).and(POLICIES.NAME.equal(policy.name))).
        execute()
    })

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

  private def deleteSingleSpace(t:Factory, id:Long) = {

    t.update(ESCALATION_RULES).
      set(ESCALATION_RULES.EXTENT, null:LONG).
      set(ESCALATION_RULES.ESCALATION, null:String).
      whereExists(
      t.select(field("1")).
        from(PAIRS).
        where(ESCALATION_RULES.EXTENT.eq(PAIRS.EXTENT))
        and(PAIRS.SPACE.eq(id)
      )).
      execute()

    t.delete(ESCALATIONS).
      whereExists(
      t.select(field("1")).
        from(PAIRS).
        where(ESCALATIONS.EXTENT.eq(PAIRS.EXTENT))
          and(PAIRS.SPACE.eq(id)
        )).
      execute()

    t.delete(EXTERNAL_HTTP_CREDENTIALS).where(EXTERNAL_HTTP_CREDENTIALS.SPACE.equal(id)).execute()
    t.delete(USER_ITEM_VISIBILITY).where(USER_ITEM_VISIBILITY.SPACE.equal(id)).execute()
    t.delete(PREFIX_CATEGORIES).where(PREFIX_CATEGORIES.SPACE.equal(id)).execute()
    t.delete(PREFIX_CATEGORY_VIEWS).where(PREFIX_CATEGORY_VIEWS.SPACE.equal(id)).execute()
    t.delete(SET_CATEGORIES).where(SET_CATEGORIES.SPACE.equal(id)).execute()
    t.delete(SET_CATEGORY_VIEWS).where(SET_CATEGORY_VIEWS.SPACE.equal(id)).execute()
    t.delete(RANGE_CATEGORIES).where(RANGE_CATEGORIES.SPACE.equal(id)).execute()
    t.delete(RANGE_CATEGORY_VIEWS).where(RANGE_CATEGORY_VIEWS.SPACE.equal(id)).execute()
    t.delete(UNIQUE_CATEGORY_NAMES).where(UNIQUE_CATEGORY_NAMES.SPACE.equal(id)).execute()
    t.delete(UNIQUE_CATEGORY_VIEW_NAMES).where(UNIQUE_CATEGORY_VIEW_NAMES.SPACE.equal(id)).execute()
    t.delete(ENDPOINT_VIEWS).where(ENDPOINT_VIEWS.SPACE.equal(id)).execute()
    t.delete(PAIR_REPORTS).where(PAIR_REPORTS.SPACE.equal(id)).execute()
    t.delete(REPAIR_ACTIONS).where(REPAIR_ACTIONS.SPACE.equal(id)).execute()
    t.delete(PAIR_VIEWS).where(PAIR_VIEWS.SPACE.equal(id)).execute()
    t.delete(BREAKERS).where(BREAKERS.SPACE.equal(id)).execute()
    t.delete(PAIRS).where(PAIRS.SPACE.equal(id)).execute()
    t.delete(ENDPOINTS).where(ENDPOINTS.SPACE.equal(id)).execute()
    t.delete(CONFIG_OPTIONS).where(CONFIG_OPTIONS.SPACE.equal(id)).execute()
    t.delete(MEMBERS).where(MEMBERS.SPACE.equal(id)).execute()
    t.delete(STORE_CHECKPOINTS).where(STORE_CHECKPOINTS.SPACE.equal(id)).execute()
    t.delete(PENDING_DIFFS).where(PENDING_DIFFS.SPACE.equal(id)).execute()
    t.delete(POLICY_STATEMENTS).where(POLICY_STATEMENTS.SPACE.equal(id)).execute()
    t.delete(POLICIES).where(POLICIES.SPACE.equal(id)).execute()

    t.delete(SPACE_PATHS).
      where(SPACE_PATHS.ANCESTOR.equal(id)).
      or(SPACE_PATHS.DESCENDANT.equal(id)).
      execute()

    val deleted = t.delete(SPACES).where(SPACES.ID.equal(id)).execute()

    if (deleted == 0) {
      logger.error("%s: Attempt to delete non-existent space: %s".format(INVALID_DOMAIN, id))
      throw new MissingObjectException(id + "")
    }
  }

  private def lookupSpaceId(t:Factory, path:String) : Space = {

    val space = resolveSpaceByPath(path, Some(t))

    if (NON_EXISTENT_SPACE == space) {
      throw new MissingObjectException(path)
    }
    else {
      space
    }
  }

  private def resolveSpaceByPath(path:String, factory:Option[Factory] = None) : Space = {
    spacePathCache.readThrough(path,() => factory match {
      case Some(t) => resolveSpaceByPath(t, path)
      case None    => jooq.execute(resolveSpaceByPath(_,path))
    }
    )
  }

  private def resolveSpaceById(id:Long) = {
    spaceIdCache.readThrough(id, () => jooq.execute(t => {
      t.select().
        from(SPACES).
        where(SPACES.ID.equal(id)).
        fetchOne() match {
        case null   => NON_EXISTENT_SPACE
        case record => recordToSpace(record)
      }
    }))
  }

  private def resolveSpaceByPath(t:Factory, path:String) : Space = {

    if (path == ROOT_SPACE.name) {
      ROOT_SPACE
    }
    else {

      val spacePath = Factory.groupConcat(SPACES.NAME).orderBy(SPACES.ID).separator("/")

      val space = t.select(SPACE_PATHS.as("d").DESCENDANT).
                    select(spacePath.as("path")).
                    from(SPACE_PATHS.as("d")).
                    join(SPACE_PATHS.as("a")).
                      on(SPACE_PATHS.as("a").DESCENDANT.equal(SPACE_PATHS.as("d").DESCENDANT)).
                    join(SPACES).
                      on(SPACES.ID.equal(SPACE_PATHS.as("a").ANCESTOR)).
                    where(SPACE_PATHS.as("d").ANCESTOR.equal(0)).
                      and(SPACE_PATHS.as("d").ANCESTOR.notEqual(SPACE_PATHS.as("d").DESCENDANT)).
                    groupBy(SPACE_PATHS.as("d").DESCENDANT).
                    having(spacePath.like(ROOT_SPACE.name + "/" + path)).
                    fetchOne()

      if (null == space) {
        NON_EXISTENT_SPACE
      }
      else {
        Space(
          // TODO This is quite ropey really, because I can't figure out how to formulate the query
          // to return all attributes of the SPACES table and still keep the GROUP BY sweet
          // probably some DB guy will tell me it is pretty easy really
          id = space.getValue(SPACE_PATHS.as("d").DESCENDANT)
        )
      }
    }
  }

  private def createSpace(t:Factory, name:String, parent:Long, fullPath:String) : Space =  {

    val count = t.selectCount().
      from(SPACES).
      where(SPACES.NAME.equal(name)).
        and(SPACES.PARENT.equal(parent)).
      fetchOne().
      getValueAsBigInteger(0).longValue()

    // There is small margin for error between the read and the write, but basically we want to prevent unnecessary
    // sequence churn

    if (count == 0) {

      val sequence = sequenceProvider.nextSequenceValue(SequenceName.SPACES)

      try {

        val space = insertSpacePath(t, sequence, name, parent, fullPath)
        domainEventSubscribers.foreach(_.onDomainUpdated(sequence))
        space
      }
      catch {
        case u:DataAccessException if u.getCause.isInstanceOf[SQLIntegrityConstraintViolationException] =>
          logger.warn("Integrity constraint when trying to create space for path " + name)
          throw u
        case x => throw x
      }

    }

    else {

      // This is the U in CRUD, which currently is a NOOP

      val record =  t.select().
        from(SPACES).
        where(SPACES.NAME.equal(name)).
        and(SPACES.PARENT.equal(parent)).
        fetchOne()

      recordToSpace(record)
    }

  }

  private def insertSpacePath(t:Factory, space:Long, name:String, parent:Long, fullPath:String) : Space = {

    t.insertInto(SPACES).
      set(SPACES.ID, space:LONG).
      set(SPACES.PARENT, parent:LONG).
      set(SPACES.NAME, name).
      set(SPACES.CONFIG_VERSION, 0:INT).
      execute()

    val leaf = Factory.field(space.toString)
    val zero = Factory.field("0")

    t.insertInto(SPACE_PATHS, SPACE_PATHS.ANCESTOR, SPACE_PATHS.DESCENDANT, SPACE_PATHS.DEPTH).
      select(
      t.select(SPACE_PATHS.ANCESTOR).
        select(leaf).
        select(SPACE_PATHS.DEPTH.add(1)).
        from(SPACE_PATHS).
        where(SPACE_PATHS.DESCENDANT.equal(parent)).
        unionAll(t.select(leaf, leaf, zero))
    ).execute()

    invalidateSpaceByPathCache(fullPath)
    invalidateSpaceByIdCache(space)

    Space(
      id = space,
      parent = parent,
      name = name,
      configVersion = 0
    )
  }


  private def descendancyTree(t:Factory, parent:Long) = {
    val hierarchy =
      t.select().
        from(SPACES).
        join(SPACE_PATHS).
          on(SPACE_PATHS.DESCENDANT.equal(SPACES.ID)).
        where(SPACE_PATHS.ANCESTOR.equal(parent)).
        orderBy(SPACE_PATHS.DEPTH.desc()).
        fetch()

    if (hierarchy == null) {
      throw new MissingObjectException(parent.toString)
    }
    else {
      hierarchy.iterator().map(recordToSpace(_))
    }
  }

  private def recordToSpace(record:Record) = {
    Space(
      id = record.getValue(SPACES.ID),
      parent = record.getValue(SPACES.PARENT),
      name = record.getValue(SPACES.NAME),
      configVersion = record.getValue(SPACES.CONFIG_VERSION)
    )
  }

  private def initializeExistingSequences() = {
    val persistentValue = jooq.execute { t =>

      t.select(max(SPACES.ID).as("max_space_id")).
        from(SPACES).
        fetchOne().
        getValueAsBigInteger("max_space_id").longValue()

    }

    val currentValue = sequenceProvider.currentSequenceValue(SequenceName.SPACES)

    if (persistentValue > currentValue) {
      sequenceProvider.upgradeSequenceValue(SequenceName.SPACES, currentValue, persistentValue)
    }
  }


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

  private def recordToPolicyStatement(record:Record) = {
    PolicyStatement(
      privilege = record.getValue(POLICY_STATEMENTS.PRIVILEGE),
      target = record.getValue(POLICY_STATEMENTS.TARGET)
    )
  }
}

/**
 * Indicates that the system not configured correctly
 */
class InvalidSystemConfigurationException(msg:String) extends RuntimeException(msg)
