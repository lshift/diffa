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

package net.lshift.diffa.agent.util

import org.springframework.beans.factory.FactoryBean
import java.util.Properties

/**
 * Factory for creating the configuration to run Hibernate. Abstracted since we'd like
 * to be able to configure Hibernate via the context.xml, and this just doesn't seem
 * to be possible via the provided mechanisms.
 */
class HibernatePropertiesFactory(dialect:String)
    extends FactoryBean[Properties] {
  private val props = new Properties
  props.setProperty("hibernate.dialect", dialect)
  def isSingleton = true
  def getObjectType = classOf[Properties]
  def getObject = props
}