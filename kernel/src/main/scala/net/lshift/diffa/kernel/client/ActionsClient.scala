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

package net.lshift.diffa.kernel.client

import scala.beans.BeanProperty
import net.lshift.diffa.kernel.frontend.wire.InvocationResult
import net.lshift.diffa.kernel.config.RepairAction._
import net.lshift.diffa.kernel.frontend.RepairActionDef
import net.lshift.diffa.kernel.config.DiffaPairRef

/**
 * Interface supported by clients capable of listing and invoking actions for pairs.
 */
trait ActionsClient {

  /**
   * Lists all actions that a pairing offers
   */
  def listActions(pair:DiffaPairRef): Seq[Actionable]

  /**
   * Lists the entity-scoped actions that a pairing offers
   */
  def listEntityScopedActions(pair:DiffaPairRef): Seq[Actionable]

  /**
   * Lists the pair-scoped actions that a pairing offers
   */
  def listPairScopedActions(pair:DiffaPairRef): Seq[Actionable]

  /**
   * Invokes an action against a pairing
   */
  def invoke(request:ActionableRequest): InvocationResult
  
}

case class Actionable (
  @BeanProperty var name:String,
  @BeanProperty var scope:String,
  @BeanProperty var path:String,
  @BeanProperty var pair:String) {

 def this() = this(null, null, null, null)
}

object Actionable {
  def fromRepairAction(domain:String, pair:String, a: RepairActionDef): Actionable = {
    val path = "/domains/" + domain + "/actions/" + pair + "/" + a.name + (a.scope match {
      case ENTITY_SCOPE =>  "/${id}"
      case PAIR_SCOPE => ""
    })
    new Actionable(a.name, a.scope, path, pair)
  }
}

case class ActionableRequest (
  @BeanProperty var pairKey:String = null,
  @BeanProperty var domain:String = null,
  @BeanProperty var actionId:String = null,
  @BeanProperty var entityId:String = null) {

 def this() = this(pairKey = null)

}
