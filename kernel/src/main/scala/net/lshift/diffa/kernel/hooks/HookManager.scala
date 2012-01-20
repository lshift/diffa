/**
 * Copyright (C) 2012 LShift Ltd.
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
package net.lshift.diffa.kernel.hooks

import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import org.hibernate.dialect.{Oracle8iDialect, Dialect}

/**
 * Factory for constructing hooks based upon session configuration.
 */
class HookManager {
  private var dialect:Dialect = null

  def this(config:Configuration) = {
    this()

    applyConfiguration(config)
  }

  def applyConfiguration(config:Configuration) {
    dialect = Dialect.getDialect(config.getProperties)
  }

  def createDifferencePartitioningHook(sessionFactory:SessionFactory) = {
    if (dialect.isInstanceOf[Oracle8iDialect]) {
      new OracleDifferencePartitioningHook(sessionFactory)
    } else {
      new EmptyDifferencePartitioningHook
    }
  }
}

class EmptyDifferencePartitioningHook extends DifferencePartitioningHook {
  def pairCreated(domain: String, key: String) {}
  def pairRemoved(domain: String, key: String) {}
  def removeAllPairDifferences(domain: String, key: String) = false
  def isDifferencePartitioningEnabled = false
}