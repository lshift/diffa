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

  def isEscalationEnabled(pair:PairRef, name:String) = {
    !config.isBreakerTripped(pair.space, pair.name, nameForAllEscalationsBreaker) &&
      !config.isBreakerTripped(pair.space, pair.name, nameForEscalationBreaker(name))
  }

  def tripAllEscalations(pair:PairRef) {
    config.tripBreaker(pair.space, pair.name, nameForAllEscalationsBreaker)
  }
  def clearAllEscalations(pair:PairRef) {
    config.clearBreaker(pair.space, pair.name, nameForAllEscalationsBreaker)
  }

  def tripEscalation(pair:PairRef, name:String) {
    config.tripBreaker(pair.space, pair.name, nameForEscalationBreaker(name))
  }
  def clearEscalation(pair:PairRef,  name:String) {
    config.clearBreaker(pair.space, pair.name, nameForEscalationBreaker(name))
  }
}