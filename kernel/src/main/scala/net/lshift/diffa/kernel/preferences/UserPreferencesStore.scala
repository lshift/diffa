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

import net.lshift.diffa.kernel.config.DiffaPairRef

/**
 * Provides a mechanism for users to persist their display preferences.
 */
trait UserPreferencesStore {

  /**
   * Creates a new item that the user has chosen to filter out.
   */
  def createFilteredItem(pair:DiffaPairRef, username: String, itemType: FilteredItemType)

  /**
   * Deletes item that the user has chosen to filter out.
   */
  def removeFilteredItem(pair:DiffaPairRef, username: String, itemType: FilteredItemType)

  /**
   * Deletes all items that the given user has configured for the current domain.
   */
  def removeAllFilteredItemsForDomain(domain:String, username: String)

  /**
   * Deletes all items that the given user has configured for themselves.
   */
  def removeAllFilteredItemsForUser(username: String)

  /**
   * Returns a list of all of the items that the user has elected to filter out.
   */
  def listFilteredItems(domain:String, username:String, itemType:FilteredItemType) : Set[String]


}
