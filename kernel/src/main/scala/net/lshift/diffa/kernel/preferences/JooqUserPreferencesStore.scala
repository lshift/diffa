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
package net.lshift.diffa.kernel.preferences

import scala.collection.JavaConversions._
import net.lshift.diffa.schema.jooq.DatabaseFacade
import net.lshift.diffa.kernel.config.{SpacePathCache, DiffaPairRef}
import net.lshift.diffa.schema.tables.UserItemVisibility.USER_ITEM_VISIBILITY
import net.lshift.diffa.kernel.lifecycle.{DomainLifecycleAware, PairLifecycleAware}
import net.lshift.diffa.kernel.util.cache.{KeyPredicate, CacheProvider}
import reflect.BeanProperty

class JooqUserPreferencesStore(db:DatabaseFacade, cacheProvider:CacheProvider, spacePathCache:SpacePathCache)
  extends UserPreferencesStore
  with PairLifecycleAware
  with DomainLifecycleAware {

  val cachedFilteredItems = cacheProvider.getCachedMap[DomainUserTypeKey, java.util.Set[String]]("user.preferences.filtered.items")

  def reset {
    cachedFilteredItems.evictAll()
  }

  def createFilteredItem(pair:DiffaPairRef, username: String, itemType: FilteredItemType) = {

    val space = spacePathCache.resolveSpacePathOrDie(pair.domain)

    db.execute(t => {
      t.insertInto(USER_ITEM_VISIBILITY).
          set(USER_ITEM_VISIBILITY.SPACE, space.id).
          set(USER_ITEM_VISIBILITY.PAIR, pair.key).
          set(USER_ITEM_VISIBILITY.USERNAME, username).
          set(USER_ITEM_VISIBILITY.ITEM_TYPE, itemType.toString).
        onDuplicateKeyIgnore().
        execute()
    })

    // Theoretically we could update the cache right now,
    // but we'd need to lock the the update, so let's just invalidate it for now and
    // let the reader pull the data through

    val key = DomainUserTypeKey(space.id, username, itemType.toString)
    cachedFilteredItems.evict(key)
  }

  def removeFilteredItem(pair:DiffaPairRef, username: String, itemType: FilteredItemType) = {

    val space = spacePathCache.resolveSpacePathOrDie(pair.domain)

    cachedFilteredItems.evict(DomainUserTypeKey(space.id, username, itemType.toString))
    db.execute(t => {
      t.delete(USER_ITEM_VISIBILITY).
        where(USER_ITEM_VISIBILITY.SPACE.equal(space.id)).
        and(USER_ITEM_VISIBILITY.PAIR.equal(pair.key)).
        and(USER_ITEM_VISIBILITY.USERNAME.equal(username)).
        and(USER_ITEM_VISIBILITY.ITEM_TYPE.equal(itemType.toString)).
        execute()
    })
  }

  def removeAllFilteredItemsForDomain(domain:String, username: String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    cachedFilteredItems.keySubset(FilterByDomainAndUserPredicate(space.id, username)).evictAll()
    db.execute(t => {
      t.delete(USER_ITEM_VISIBILITY).
        where(USER_ITEM_VISIBILITY.SPACE.equal(space.id)).
        and(USER_ITEM_VISIBILITY.USERNAME.equal(username)).
        execute()
    })
  }

  def removeAllFilteredItemsForUser(username: String) = {
    cachedFilteredItems.keySubset(FilterByUserPredicate(username)).evictAll()
    db.execute(t => {
      t.delete(USER_ITEM_VISIBILITY).
        where(USER_ITEM_VISIBILITY.USERNAME.equal(username)).
        execute()
    })
  }

  def listFilteredItems(domain: String, username: String, itemType: FilteredItemType) : Set[String] = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    cachedFilteredItems.readThrough(DomainUserTypeKey(space.id, username, itemType.toString), () => {
      db.execute(t => {
        val result =
          t.select().from(USER_ITEM_VISIBILITY).
            where(USER_ITEM_VISIBILITY.SPACE.equal(space.id)).
            and(USER_ITEM_VISIBILITY.USERNAME.equal(username)).
            and(USER_ITEM_VISIBILITY.ITEM_TYPE.equal(itemType.toString)).
          fetch()

        val items = new java.util.HashSet[String]()

        for (record <- result.iterator()) {
          items.add(record.getValue(USER_ITEM_VISIBILITY.PAIR))
        }

        items
      })
    })
  }.toSet

  def onPairUpdated(pair: DiffaPairRef) {
    // This is probably too coarse grained, i.e. it invalidates everything
    maybeInvalidateCacheForDomain(pair.domain)
  }
  def onPairDeleted(pair: DiffaPairRef) {
    // This is probably too coarse grained, i.e. it invalidates everything
    maybeInvalidateCacheForDomain(pair.domain)
  }

  private def maybeInvalidateCacheForDomain(domain:String) {
    val space = spacePathCache.resolveSpacePath(domain)
    if (space != spacePathCache.NON_EXISTENT_SPACE) {
      invalidCacheForDomain(space.id)
    }
  }

  def onDomainUpdated(space: Long) = invalidCacheForDomain(space)
  def onDomainRemoved(space: Long) = invalidCacheForDomain(space)

  private def invalidCacheForDomain(space:Long) = {
    cachedFilteredItems.keySubset(FilterByDomainPredicate(space)).evictAll()
  }
}

case class DomainUserTypeKey(
  @BeanProperty var space: Long,
  @BeanProperty var username: String = null,
  @BeanProperty var itemType: String = null) {

  def this() = this(space = -1)

}

case class FilterByUserPredicate(@BeanProperty var user:String = null) extends KeyPredicate[DomainUserTypeKey] {
  def this() = this(user = null)
  def constrain(key: DomainUserTypeKey) = key.username == user
}

case class FilterByDomainPredicate(@BeanProperty var space:Long) extends KeyPredicate[DomainUserTypeKey] {
  def this() = this(space = -1)
  def constrain(key: DomainUserTypeKey) = key.space == space
}

case class FilterByDomainAndUserPredicate(@BeanProperty var space:Long,
                                          @BeanProperty var user:String = null) extends KeyPredicate[DomainUserTypeKey] {
  def this() = this(space = -1)
  def constrain(key: DomainUserTypeKey) = key.space == space && key.username == user
}
