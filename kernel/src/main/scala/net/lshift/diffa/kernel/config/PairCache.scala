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

import net.sf.ehcache.CacheManager
import net.lshift.diffa.kernel.util.CacheWrapper
import net.lshift.diffa.kernel.frontend.PairDef

/**
 * This is a temporary workaround for a Hibernate 2L issue - see #367. This class should be deleted when #366 lands.
 */
@Deprecated
class PairCache(val cacheManager:CacheManager) {

  /**
   * An explicitly managed cache of pairs to avoid a problem with Hibernate 2L - see #....
   */
  private val cachedPairs = new CacheWrapper[String,Seq[PairDef]]("listPairs", cacheManager)

  @Deprecated
  def invalidate(domain:String) = cachedPairs.remove(domain)

  @Deprecated
  def readThrough(domain:String, f:() => Seq[PairDef]) = cachedPairs.readThrough(domain,f)
}