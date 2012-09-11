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

import net.lshift.diffa.kernel.util.cache.CacheProvider
import net.lshift.diffa.kernel.config._
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.naming.CacheName

class CachedSystemConfigStore(underlying:SystemConfigStore, cacheProvider:CacheProvider)
  extends SystemConfigStore
  with DomainMembershipAware{

  val userTokenCache = cacheProvider.getCachedMap[String, User]("user.tokens")
  val usersCache = cacheProvider.getCachedMap[String, User]("users")
  val membershipCache = cacheProvider.getCachedMap[String, java.util.List[Member]]("user.domain.memberships")
  val policyCache = cacheProvider.getCachedMap[PolicyKey, java.util.List[PolicyStatement]](CacheName.SPACE_POLICY_STATEMENTS)
  val superspaceCache = cacheProvider.getCachedMap[Long, java.util.List[Long]](CacheName.SPACE_SUPERSPACES)

  def reset {
    usersCache.evictAll()
    userTokenCache.evictAll()
    membershipCache.evictAll()
    policyCache.evictAll()
    superspaceCache.evictAll()
  }

  def onMembershipCreated(member: Member) = {
    // For simplicity's sake, just evict this user from the cache and let it
    // get read through the next time it is required
    // A future implementation could actually manage the memberships in the map.
    evictMember(member)
  }
  def onMembershipRemoved(member: Member) = {
    // See note about onMembershipCreated/1
    evictMember(member)
  }

  // TODO Currently the only operations that are cached are the frequently invoked
  // lookups of users by username, token and their respective memberships for authentication purposes
  // Ultimately, all operations on this store should get cached

  def createOrUpdateSpace(path: String)  = underlying.createOrUpdateSpace(path)
  def listSpaces : Seq[Space] = underlying.listSpaces

  def listSubspaces(parent:Long) = underlying.listSubspaces(parent)
  def listSuperspaceIds(child:Long) = superspaceCache.readThrough(child, () => underlying.listSuperspaceIds(child).toList)

  def lookupSpaceByPath(path: String) = underlying.lookupSpaceByPath(path)
  def lookupSpacePathById(space: Long) = underlying.lookupSpacePathById(space)

  def createOrUpdateDomain(domain:String) = underlying.createOrUpdateDomain(domain)
  //def deleteDomain(domain:String) = underlying.deleteDomain(domain)
  def deleteSpace(id:Long) = underlying.deleteSpace(id)
  def doesDomainExist(name: String) = underlying.doesDomainExist(name)
  def doesSpaceExist(id:Long) = underlying.doesSpaceExist(id)
  def listDomains = underlying.listDomains
  def setSystemConfigOption(key: String, value: String) = underlying.setSystemConfigOption(key, value)
  def clearSystemConfigOption(key: String) = underlying.clearSystemConfigOption(key)
  def maybeSystemConfigOption(key: String) = underlying.maybeSystemConfigOption(key)
  def systemConfigOptionOrDefault(key: String, defaultVal: String) = underlying.systemConfigOptionOrDefault(key, defaultVal)
  def listPairs = underlying.listPairs
  def listEndpoints = underlying.listEndpoints
  def createOrUpdateUser(user: User) = underlying.createOrUpdateUser(user)
  def getUserToken(username: String) = underlying.getUserToken(username)

  def clearUserToken(username: String) = {
    evictFromCachesByUsername(username)
    underlying.clearUserToken(username)
  }

  def deleteUser(username: String) = {
    evictFromTokenCacheByUsername(username)
    usersCache.evict(username)
    underlying.deleteUser(username)
  }

  def listUsers = underlying.listUsers

  def listDomainMemberships(username: String) = {
    membershipCache.readThrough(username,
      () => underlying.listDomainMemberships(username).toList)
  }

  def lookupPolicyStatements(policy:PolicyKey) = {
    policyCache.readThrough(policy,
      () => underlying.lookupPolicyStatements(policy).toList)
  }

  def storePolicy(policy: PolicyKey, stmts: Seq[PolicyStatement]) {
    underlying.storePolicy(policy, stmts)
    policyCache.evict(policy)
  }

  def removePolicy(policy: PolicyKey) {
    underlying.removePolicy(policy)
    policyCache.evict(policy)
  }

  def getUser(username: String) = {
    usersCache.readThrough(username, () => underlying.getUser(username))
  }

  def getUserByToken(token: String) = {
    userTokenCache.readThrough(token, () => underlying.getUserByToken(token))
  }

  def containsRootUser(names: Seq[String]) = underlying.containsRootUser(names)

  private def evictFromCachesByUsername(username:String) = {
    // First update the users cache
    evictFromUserCacheByUsername(username)
    // Then invalidate the user token cache
    evictFromTokenCacheByUsername(username)
  }

  private def evictFromTokenCacheByUsername(username:String) = {
    val users = userTokenCache.valueSubset("name", username)
    // In this scenario, this should only be a list with 1 element
    users.foreach(u => userTokenCache.evict(u.token))
  }

  private def evictFromUserCacheByUsername(username:String) = {
    val user = usersCache.get(username)
    if (user != null) {
      user.token = null
      usersCache.put(username, user)
    }
  }

  private def evictMember(member: Member) = {
    val username = member.user
    membershipCache.evict(username)
  }
}
