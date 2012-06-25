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
import net.lshift.diffa.kernel.config.DiffaPairRef
import net.lshift.diffa.schema.tables.UserItemVisibility.USER_ITEM_VISIBILITY
import net.lshift.diffa.kernel.lifecycle.{DomainLifecycleAware, PairLifecycleAware}
import net.lshift.diffa.kernel.util.cache.{KeyPredicate, CacheProvider}
import reflect.BeanProperty

class JooqUserPreferencesStore(db:DatabaseFacade, cacheProvider:CacheProvider)
  extends UserPreferencesStore
  with PairLifecycleAware
  with DomainLifecycleAware {

  val cachedFilteredItems = cacheProvider.getCachedMap[DomainUserTypeKey, java.util.Set[String]]("user.preferences.filtered.items")

  def reset {
    cachedFilteredItems.evictAll()
  }

  def createFilteredItem(pair:DiffaPairRef, username: String, itemType: FilteredItemType) = {
    db.execute(t => {
      t.insertInto(USER_ITEM_VISIBILITY).
        set(USER_ITEM_VISIBILITY.DOMAIN, pair.domain).
        set(USER_ITEM_VISIBILITY.PAIR, pair.key).
        set(USER_ITEM_VISIBILITY.USERNAME, username).
        set(USER_ITEM_VISIBILITY.ITEM_TYPE, itemType.toString).
        execute()
    })

    // Theoretically we could update the cache right now,
    // but we'd need to lock the the update, so let's just invalidate it for now and
    // let the reader pull the data through

    val key = DomainUserTypeKey(pair.domain, username, itemType.toString)
    cachedFilteredItems.evict(key)
  }

  def removeFilteredItem(pair:DiffaPairRef, username: String, itemType: FilteredItemType) = {
    cachedFilteredItems.evict(DomainUserTypeKey(pair.domain, username, itemType.toString))
    db.execute(t => {
      t.delete(USER_ITEM_VISIBILITY).
        where(USER_ITEM_VISIBILITY.DOMAIN.equal(pair.domain)).
        and(USER_ITEM_VISIBILITY.PAIR.equal(pair.key)).
        and(USER_ITEM_VISIBILITY.USERNAME.equal(username)).
        and(USER_ITEM_VISIBILITY.ITEM_TYPE.equal(itemType.toString)).
        execute()
    })
  }

  def removeAllFilteredItemsForDomain(domain:String, username: String) = {
    cachedFilteredItems.keySubset(FilterByDomainAndUserPredicate(domain, username)).evictAll()
    db.execute(t => {
      t.delete(USER_ITEM_VISIBILITY).
        where(USER_ITEM_VISIBILITY.DOMAIN.equal(domain)).
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
    cachedFilteredItems.readThrough(DomainUserTypeKey(domain,username,itemType.toString), () => {
      db.execute(t => {
        val result =
          t.select().from(USER_ITEM_VISIBILITY).
            where(USER_ITEM_VISIBILITY.DOMAIN.equal(domain)).
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

  def onPairDeleted(pair: DiffaPairRef) {
    // This is probably too coarse grained, i.e. it invalidates everything
    invalidCacheForDomain(pair.domain)
  }
  def onDomainUpdated(domain: String) = invalidCacheForDomain(domain)
  def onDomainRemoved(domain: String) = invalidCacheForDomain(domain)

  private def invalidCacheForDomain(domain:String) = {
    cachedFilteredItems.keySubset(FilterByUserPredicate(domain)).evictAll()
  }
}

case class DomainUserTypeKey(
  @BeanProperty var domain: String = null,
  @BeanProperty var username: String = null,
  @BeanProperty var itemType: String = null) {

  def this() = this(domain = null)

}

case class FilterByUserPredicate(@BeanProperty user:String = null) extends KeyPredicate[DomainUserTypeKey] {
  def this() = this(user = null)
  def constrain(key: DomainUserTypeKey) = key.username == user
}

case class FilterByDomainPredicate(@BeanProperty domain:String = null) extends KeyPredicate[DomainUserTypeKey] {
  def this() = this(domain = null)
  def constrain(key: DomainUserTypeKey) = key.domain == domain
}

case class FilterByDomainAndUserPredicate(@BeanProperty domain:String = null,
                                          @BeanProperty user:String = null) extends KeyPredicate[DomainUserTypeKey] {
  def this() = this(domain = null)
  def constrain(key: DomainUserTypeKey) = key.domain == domain && key.username == user
}
