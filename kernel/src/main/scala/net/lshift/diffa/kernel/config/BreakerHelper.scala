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

/**
 * Helper class for creating and clearing breakers.
 */
class BreakerHelper(val config:DomainConfigStore) {
  def nameForEscalationBreaker(escalationName:String) = "escalation:" + escalationName
  val nameForAllEscalationsBreaker = nameForEscalationBreaker("*")

  def isEscalationEnabled(pair:DiffaPairRef, name:String) = {
    !config.isBreakerTripped(pair.domain, pair.key, nameForAllEscalationsBreaker) &&
      !config.isBreakerTripped(pair.domain, pair.key, nameForEscalationBreaker(name))
  }

  def tripAllEscalations(pair:DiffaPairRef) {
    config.tripBreaker(pair.domain, pair.key, nameForAllEscalationsBreaker)
  }
  def clearAllEscalations(pair:DiffaPairRef) {
    config.clearBreaker(pair.domain, pair.key, nameForAllEscalationsBreaker)
  }

  def tripEscalation(pair:DiffaPairRef, name:String) {
    config.tripBreaker(pair.domain, pair.key, nameForEscalationBreaker(name))
  }
  def clearEscalation(pair:DiffaPairRef,  name:String) {
    config.clearBreaker(pair.domain, pair.key, nameForEscalationBreaker(name))
  }
}