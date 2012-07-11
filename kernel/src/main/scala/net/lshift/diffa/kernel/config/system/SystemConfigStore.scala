package net.lshift.diffa.kernel.config.system

import reflect.BeanProperty
import net.lshift.diffa.kernel.config.{Member, DiffaPairRef, User, Endpoint, Domain, DiffaPair}
import net.lshift.diffa.kernel.frontend.{DomainEndpointDef, DomainPairDef, PairDef}

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

/**
 * This provides configuration options for the entire system and hence should only be
 * accessible to internally trusted components or external users with elevated privileges
 */
trait SystemConfigStore {

  def createOrUpdateDomain(domain: Domain)
  def deleteDomain(name: String)
  def doesDomainExist(name: String): Boolean
  def listDomains : Seq[Domain]
  
  /**
   * Sets the given configuration option to the given value.
   * This option is marked as internal will not be returned by the allConfigOptions method. This allows
   * properties to be prevented from being shown in the user-visible system configuration views.
   */
  def setSystemConfigOption(key:String, value:String)
  def clearSystemConfigOption(key:String)
  def maybeSystemConfigOption(key:String) : Option[String]

  // TODO This requires a unit test
  def systemConfigOptionOrDefault(key:String, defaultVal:String) : String

  /**
   * Enumerate all pairs of all domains
   */
  def listPairs : Seq[DomainPairDef]

  /**
   * Enumerate all pairs of all domains
   */
  def listEndpoints : Seq[DomainEndpointDef]

  def createOrUpdateUser(user: User)
  def createUser(user: User)
  def updateUser(user: User) : Int

  def getUserToken(username: String): String
  def clearUserToken(username: String)
  def deleteUser(name: String): Unit
  def listUsers : Seq[User]
  def listDomainMemberships(username: String) : Seq[Member]
  def getUser(name: String) : User
  def getUserByToken(token: String) : User
  def containsRootUser(names:Seq[String]):Boolean

}
