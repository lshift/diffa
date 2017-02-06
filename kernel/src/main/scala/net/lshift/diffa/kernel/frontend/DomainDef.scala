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

package net.lshift.diffa.kernel.frontend

import scala.beans.BeanProperty
import net.lshift.diffa.kernel.config.ValidationUtil

/**
 * Serializable representation of a domain within the context of a domain.
 */
case class DomainDef(@BeanProperty var name: String = null) {

  def this() = this(name = null)

  def validate(path:String = null) {
    val domainPath = ValidationUtil.buildPath(path, "domain", Map("name" -> name))

    ValidationUtil.ensureLengthLimit(domainPath, "name", name, DefaultLimits.KEY_LENGTH_LIMIT)
  }

}