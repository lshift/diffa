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

package net.lshift.diffa.kernel.config.system

import net.lshift.diffa.kernel.config._
import net.lshift.diffa.kernel.frontend.DomainEndpointDef
import net.lshift.diffa.kernel.frontend.DomainPairDef
import net.lshift.diffa.kernel.config.User
import net.lshift.diffa.kernel.config.Member
import net.lshift.diffa.kernel.util.MissingObjectException


/**
 * This provides configuration options for the entire system and hence should only be
 * accessible to internally trusted components or external users with elevated privileges
 */
trait SystemConfigStore {

  /**
   * This creates a new space with the given path, delimited by forward slashes.
   *
   * If the path foo/bar/baz is requested, it will be assumed that the parent path foo/bar already exists.
   *
   * Note also that each path segment will be validated to make sure that it doesn't contain any illegal characters.
   *
   * @see ValidationUtil#ensurePathSegmentFormat
   * @throws MissingObjectException If the parent space does not exist.
   * @throws ConfigValidationException If any path segment contains invalid characters
   */
  def createOrUpdateSpace(path: String) : Space

  /**
   * Returns the complete subspace closure for a given parent space, including the parent. This returns a flat
   * list of a subspace hierarchy that where the elements are ordered by descending tree depth.
   *
   * This allows callers to apply functions to an entire subspace tree recursively.
   */
  def listSubspaces(parent:Long) : Seq[Space]

  @Deprecated def createOrUpdateDomain(domain: String)

  //def deleteDomain(name: String)
  def deleteSpace(id: Long)
  def doesDomainExist(name: String): Boolean

  def doesSpaceExist(space: Long): Boolean

  def lookupSpaceByPath(path: String) : Space

  def lookupSpacePathById(space: Long) : String

  @Deprecated  def listDomains : Seq[String]
  def listSpaces : Seq[Space]

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

  def getUserToken(username: String): String
  def clearUserToken(username: String)
  def deleteUser(name: String): Unit
  def listUsers : Seq[User]
  def listDomainMemberships(username: String) : Seq[Member]
  def getUser(name: String) : User
  def getUserByToken(token: String) : User
  def containsRootUser(names:Seq[String]):Boolean

}
