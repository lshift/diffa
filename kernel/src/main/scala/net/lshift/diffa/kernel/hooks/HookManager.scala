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

import net.lshift.hibernate.migrations.dialects.{OracleDialectExtension, DialectExtensionSelector}
import net.lshift.diffa.schema.jooq.DatabaseFacade

/**
 * Factory for constructing hooks based upon session configuration.
 */
class HookManager(val dialect:String) {

  val dialectExtension = DialectExtensionSelector.select(dialect)

  def createDifferencePartitioningHook(jooq:DatabaseFacade) = {
    // Ideally, this would go in something like the DialectExtension;
    // however, that would create a circular dependency between the two modules.
    if (dialectExtension.isInstanceOf[OracleDialectExtension]) {
      new OracleDifferencePartitioningHook(jooq)
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