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
import net.lshift.diffa.kernel.differencing.DifferencesManager
import net.lshift.diffa.kernel.config.{User, ServiceLimitsStore}
import net.lshift.diffa.kernel.config.limits.ValidServiceLimits


/**
 * Frontend component that wraps all of the events that surround system configuration changes.
 */
class SystemConfiguration(val systemConfigStore: SystemConfigStore,
                          serviceLimitsStore: ServiceLimitsStore,
                          differencesManager:DifferencesManager,
                          listener:SystemConfigListener, configuration:Configuration) {

  val log = LoggerFactory.getLogger(getClass)

  def listDomains = systemConfigStore.listDomains

  def createOrUpdateDomain(domain: DomainDef) = {
    log.info("Processing domain declare/update request: %s".format(domain))
    domain.validate()
    systemConfigStore.createOrUpdateDomain(domain.name)
    differencesManager.onUpdateDomain(domain.name)
  }

  def deleteDomain(domain: String) = {
    log.info("Processing domain delete request: %s".format(domain))
    configuration.clearDomain(domain)
    differencesManager.onDeleteDomain(domain)
    serviceLimitsStore.deleteDomainLimits(domain)
    systemConfigStore.deleteDomain(domain)
  }

  def getUser(username: String) : UserDef = toUserDef(systemConfigStore.getUser(username))
  def createOrUpdateUser(user:UserDef) {
    user.validate()
    systemConfigStore.createOrUpdateUser(fromUserDef(user))
  }
  def deleteUser(username: String) = systemConfigStore.deleteUser(username)
  def listUsers : Seq[UserDef] = systemConfigStore.listUsers.map(toUserDef(_))
  def listFullUsers : Seq[User] = systemConfigStore.listUsers
  def getUserToken(username:String) = systemConfigStore.getUserToken(username)
  def clearUserToken(username:String) {
    systemConfigStore.clearUserToken(username)
  }

  def setSystemConfigOption(key:String, value:String) {
    systemConfigStore.setSystemConfigOption(key, value)
    listener.configPropertiesUpdated(Seq(key))
  }
  def setSystemConfigOptions(options:Map[String,String]) {
    options.foreach { case (k, v) =>
      systemConfigStore.setSystemConfigOption(k, v)
    }
    listener.configPropertiesUpdated(options.keys.toSeq)
  }
  def clearSystemConfigOption(key:String) {
    systemConfigStore.clearSystemConfigOption(key)
    listener.configPropertiesUpdated(Seq(key))
  }
  def getSystemConfigOption(key:String) = systemConfigStore.maybeSystemConfigOption(key)

  def listDomainMemberships(username: String) = systemConfigStore.listDomainMemberships(username)

  def getEffectiveSystemLimit(limitName:String) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.getEffectiveLimitByName(limit)
  }

  def setHardSystemLimit(limitName:String, value:Int) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.setSystemHardLimit(limit, value)
  }

  def setDefaultSystemLimit(limitName:String, value:Int) = {
    val limit = ValidServiceLimits.lookupLimit(limitName)
    serviceLimitsStore.setSystemDefaultLimit(limit, value)
  }

}