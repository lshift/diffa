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

package net.lshift.diffa.kernel.activation

import org.hibernate.{SessionFactory, SessionFactoryObserver}
import org.slf4j.LoggerFactory
import net.sf.ehcache.CacheManager
import net.lshift.diffa.kernel.config.{PairCache, HibernateDomainConfigStore}
import net.lshift.diffa.kernel.hooks.HookManager
import net.lshift.diffa.kernel.util.db.HibernateDatabaseFacade

/**
 * This creates a baseline data set in the DB once the Hibernate session factory
 * becomes available for the first time.
 */
class BaselineConfiguration extends SessionFactoryObserver {

  private val log = LoggerFactory.getLogger(getClass)

  def sessionFactoryCreated(factory:SessionFactory) = {
    // The config store will not have been constructed at this point in Spring
    // So just create a throw away instance in order to produce this baseline
    val cacheManager = new CacheManager()
    val config = new HibernateDomainConfigStore(factory,
                                                new HibernateDatabaseFacade(factory),
                                                new PairCache(cacheManager),
                                                new HookManager,
                                                cacheManager)
    log.debug("Diffa baseline configuration created")
  }

  def sessionFactoryClosed(factory:SessionFactory) = {}
}