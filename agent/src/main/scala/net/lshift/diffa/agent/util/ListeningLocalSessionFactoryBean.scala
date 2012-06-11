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

import org.springframework.orm.hibernate3.LocalSessionFactoryBean
import org.hibernate.SessionFactoryObserver
import org.hibernate.cfg.Configuration
import reflect.BeanProperty
import net.lshift.diffa.schema.migrations.HibernatePreparationStep
import net.lshift.diffa.kernel.hooks.HookManager

/**
 * This wires in a callback that will be invoked when the underlying session factory has been
 * created. This is a subclass of the Spring template because that template doesn't expose
 * the Hibernate Configuration object in a suitable way. 
 */
class ListeningLocalSessionFactoryBean(observer:SessionFactoryObserver) extends LocalSessionFactoryBean {
  @BeanProperty var preparationSteps:Array[HibernatePreparationStep] = Array[HibernatePreparationStep]()
  @BeanProperty var hookManager:HookManager = null

  override def newConfiguration() = {
    val config = super.newConfiguration()
    config.setSessionFactoryObserver(observer)
    config
	}

  /**
   * Override the final step in the session factory creation to let us prepare the database.
   */
  override def newSessionFactory(config:Configuration) = {
    val sf = super.newSessionFactory(config)

    hookManager.applyConfiguration(config)
    preparationSteps.foreach(step => step.prepare(sf, config))

    sf
  }
}