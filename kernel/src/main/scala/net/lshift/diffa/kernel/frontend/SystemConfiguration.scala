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

import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.frontend.FrontendConversions._


/**
 * Frontend component that wraps all of the events that surround system configuration changes.
 */
class SystemConfiguration(val systemConfigStore: SystemConfigStore) {

  val log = LoggerFactory.getLogger(getClass)

  def createOrUpdateDomain(domain: DomainDef) = {
    log.debug("Processing domain declare/update request: %s".format(domain))
    domain.validate()
    systemConfigStore.createOrUpdateDomain(fromDomainDef(domain))
  }

  def deleteDomain(domain: String) = {
    log.debug("Processing endpoint delete request: %s".format(domain))
    systemConfigStore.deleteDomain(domain)
  }

  def getUser(username: String) : UserDef = toUserDef(systemConfigStore.getUser(username))
  def createOrUpdateUser(user:UserDef) : Unit = systemConfigStore.createOrUpdateUser(fromUserDef(user))
  def deleteUser(username: String) = systemConfigStore.deleteUser(username)
  def listUsers : Seq[UserDef] = systemConfigStore.listUsers.map(toUserDef(_))
}