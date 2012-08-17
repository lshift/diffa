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

import net.lshift.diffa.schema.servicelimits._
import net.lshift.diffa.schema.jooq.{DatabaseFacade => JooqDatabaseFacade}
import net.lshift.diffa.schema.tables.PairLimits.PAIR_LIMITS
import net.lshift.diffa.schema.tables.SpaceLimits.SPACE_LIMITS
import net.lshift.diffa.schema.tables.SystemLimits.SYSTEM_LIMITS
import net.lshift.diffa.schema.tables.LimitDefinitions.LIMIT_DEFINITIONS
import org.jooq.impl.{TableImpl, Factory}
import org.jooq.{Condition, TableField}
import scala.collection.JavaConversions._
import net.lshift.diffa.schema.tables.records.{SpaceLimitsRecord, SystemLimitsRecord}
import java.lang.{Long => LONG}

class JooqServiceLimitsStore(jooq:JooqDatabaseFacade, spacePathCache:SpacePathCache) extends ServiceLimitsStore {

  private def validate(limitValue: Int) {
    if (limitValue < 0 && limitValue != Unlimited.hardLimit)
      throw new Exception("Invalid limit value")
  }

  def defineLimit(limit: ServiceLimit) = jooq.execute(t => {
    t.insertInto(LIMIT_DEFINITIONS).
      set(LIMIT_DEFINITIONS.NAME, limit.key).
      set(LIMIT_DEFINITIONS.DESCRIPTION, limit.description).
    onDuplicateKeyUpdate().
      set(LIMIT_DEFINITIONS.DESCRIPTION, limit.description).
    execute()
  })

  def deleteDomainLimits(domain: String) = jooq.execute(t => {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    deletePairLimitsByDomainInternal(t, space.id)

    t.delete(SPACE_LIMITS).
      where(SPACE_LIMITS.SPACE.equal(space.id)).
      execute()
  })

  def deletePairLimitsByDomain(domain: String) = {
    val space = spacePathCache.resolveSpacePathOrDie(domain)
    jooq.execute(deletePairLimitsByDomainInternal(_, space.id))
  }

  def setSystemHardLimit(limit: ServiceLimit, limitValue: Int) = jooq.execute(t => {
    validate(limitValue)

    setSystemLimit(t, limit, limitValue, SYSTEM_LIMITS.HARD_LIMIT)
    cascadeToSpaceLimits(t, limit, limitValue, SPACE_LIMITS.HARD_LIMIT)
  })

  def setDomainHardLimit(domainName: String, limit: ServiceLimit, limitValue: Int) = {
    val space = spacePathCache.resolveSpacePathOrDie(domainName)
    jooq.execute(t => {
      validate(limitValue)
      setDomainLimit(t, space.id, limit, limitValue, SPACE_LIMITS.HARD_LIMIT)
    })
  }

  def setSystemDefaultLimit(limit: ServiceLimit, limitValue: Int) = jooq.execute(t => {
    setSystemLimit(t, limit, limitValue, SYSTEM_LIMITS.DEFAULT_LIMIT)
  })

  def setDomainDefaultLimit(domainName: String, limit: ServiceLimit, limitValue: Int) = {
    val space = spacePathCache.resolveSpacePathOrDie(domainName)
    jooq.execute(t => {
      setDomainLimit(t, space.id, limit, limitValue, SPACE_LIMITS.DEFAULT_LIMIT)
    })
  }

  def setPairLimit(domainName: String, pairKey: String, limit: ServiceLimit, limitValue: Int) = {
    val space = spacePathCache.resolveSpacePathOrDie(domainName)
    jooq.execute(setPairLimit(_, space.id, pairKey, limit, limitValue))
  }


  def getSystemHardLimitForName(limit: ServiceLimit) =
    getLimit(SYSTEM_LIMITS.HARD_LIMIT, SYSTEM_LIMITS, SYSTEM_LIMITS.NAME.equal(limit.key))

  def getSystemDefaultLimitForName(limit: ServiceLimit) =
    getLimit(SYSTEM_LIMITS.DEFAULT_LIMIT, SYSTEM_LIMITS, SYSTEM_LIMITS.NAME.equal(limit.key))

  def getDomainHardLimitForDomainAndName(domainName: String, limit: ServiceLimit) = {
    val space = spacePathCache.resolveSpacePathOrDie(domainName)
    getLimit(SPACE_LIMITS.HARD_LIMIT, SPACE_LIMITS, SPACE_LIMITS.NAME.equal(limit.key), SPACE_LIMITS.SPACE.equal(space.id))
  }

  def getDomainDefaultLimitForDomainAndName(domainName: String, limit: ServiceLimit) = {
    val space = spacePathCache.resolveSpacePathOrDie(domainName)
    getLimit(SPACE_LIMITS.DEFAULT_LIMIT, SPACE_LIMITS, SPACE_LIMITS.NAME.equal(limit.key), SPACE_LIMITS.SPACE.equal(space.id))
  }

  def getPairLimitForPairAndName(domainName: String, pairKey: String, limit: ServiceLimit) = {
    val space = spacePathCache.resolveSpacePathOrDie(domainName)
    getLimit(PAIR_LIMITS.LIMIT_VALUE, PAIR_LIMITS,
             PAIR_LIMITS.NAME.equal(limit.key), PAIR_LIMITS.SPACE.equal(space.id), PAIR_LIMITS.PAIR.equal(pairKey))
  }

  private def getLimit(limitValue:TableField[_,_], table:TableImpl[_], predicate:Condition*) : Option[Int] = jooq.execute(t => {

    val record = t.select(limitValue).from(table).where(predicate).fetchOne()

    if (record != null) {
      Some(record.getValueAsInteger(limitValue))
    }
    else {
      None
    }
  })

  private def setPairLimit(t: Factory, space: Long, pairKey: String, limit: ServiceLimit, limitValue: Int) = {
    t.insertInto(PAIR_LIMITS).
        set(PAIR_LIMITS.NAME, limit.key).
        set(PAIR_LIMITS.LIMIT_VALUE, int2Integer(limitValue)).
        set(PAIR_LIMITS.SPACE, space:LONG).
        set(PAIR_LIMITS.PAIR, pairKey).
      onDuplicateKeyUpdate().
        set(PAIR_LIMITS.LIMIT_VALUE, int2Integer(limitValue)).
      execute()
  }

  /**
   * If there isn't a row for the the particular limit, then irrespective of whether setting the hard or default limit,
   * set both columns to the same value. If the row already exists, then just update the requested column (i.e. either
   * the hard or the default limit). After having performed the UPSERT, make sure that the default limit is never greater
   * than the hard limit.
   */

  private def setSystemLimit(t:Factory, limit: ServiceLimit, limitValue: java.lang.Integer, fieldToLimit:TableField[SystemLimitsRecord,java.lang.Integer]) = {
    t.insertInto(SYSTEM_LIMITS).
        set(SYSTEM_LIMITS.NAME, limit.key).
        set(SYSTEM_LIMITS.HARD_LIMIT, limitValue).
        set(SYSTEM_LIMITS.DEFAULT_LIMIT, limitValue).
      onDuplicateKeyUpdate().
        set(fieldToLimit, limitValue).
      execute()

    verifySystemDefaultLimit(t)
  }

  private def setDomainLimit(t:Factory, space:Long, limit: ServiceLimit, limitValue: java.lang.Integer, fieldToLimit:TableField[SpaceLimitsRecord,java.lang.Integer]) = {
    t.insertInto(SPACE_LIMITS).
        set(SPACE_LIMITS.SPACE, space:LONG).
        set(SPACE_LIMITS.NAME, limit.key).
        set(SPACE_LIMITS.HARD_LIMIT, limitValue).
        set(SPACE_LIMITS.DEFAULT_LIMIT, limitValue).
      onDuplicateKeyUpdate().
        set(fieldToLimit, limitValue).
      execute()

    verifyDomainDefaultLimit(t)
    cascadeToPairLimits(t, space, limit, limitValue)

  }

  /**
   * This will only get called within the scope of setting a system hard limit, so be careful when trying to make it more optimal :-)
   */
  private def cascadeToSpaceLimits(t:Factory, limit: ServiceLimit, limitValue: java.lang.Integer, fieldToLimit:TableField[SpaceLimitsRecord,java.lang.Integer]) = {
    t.update(SPACE_LIMITS).
        set(fieldToLimit, limitValue).
      where(SPACE_LIMITS.NAME.equal(limit.key)).
        and(SPACE_LIMITS.HARD_LIMIT.greaterThan(limitValue)).
      execute()

    verifyDomainDefaultLimit(t)
    verifyPairLimit(t, limit, limitValue)
  }

  private def cascadeToPairLimits(t:Factory, space:Long, limit: ServiceLimit, limitValue: java.lang.Integer) = {
    t.update(PAIR_LIMITS).
        set(PAIR_LIMITS.LIMIT_VALUE, limitValue).
      where(PAIR_LIMITS.NAME.equal(limit.key)).
        and(PAIR_LIMITS.SPACE.equal(space)).
        and(PAIR_LIMITS.LIMIT_VALUE.greaterThan(limitValue)).
      execute()
  }

  private def verifySystemDefaultLimit(t:Factory) = {
    t.update(SYSTEM_LIMITS).
        set(SYSTEM_LIMITS.DEFAULT_LIMIT, SYSTEM_LIMITS.HARD_LIMIT).
      where(SYSTEM_LIMITS.DEFAULT_LIMIT.greaterThan(SYSTEM_LIMITS.HARD_LIMIT)).
      execute()
  }

  private def verifyDomainDefaultLimit(t:Factory) = {
    t.update(SPACE_LIMITS).
        set(SPACE_LIMITS.DEFAULT_LIMIT, SPACE_LIMITS.HARD_LIMIT).
      where(SPACE_LIMITS.DEFAULT_LIMIT.greaterThan(SPACE_LIMITS.HARD_LIMIT)).
      execute()
  }

  private def verifyPairLimit(t:Factory, limit: ServiceLimit, limitValue: java.lang.Integer) = {
    t.update(PAIR_LIMITS).
        set(PAIR_LIMITS.LIMIT_VALUE, limitValue).
      where(PAIR_LIMITS.LIMIT_VALUE.greaterThan(limitValue)).
        and(PAIR_LIMITS.NAME.equal(limit.key)).
      execute()
  }

  private def deletePairLimitsByDomainInternal(t:Factory, space: Long) = {
    t.delete(PAIR_LIMITS).
      where(PAIR_LIMITS.SPACE.equal(space)).
      execute()
  }
}
