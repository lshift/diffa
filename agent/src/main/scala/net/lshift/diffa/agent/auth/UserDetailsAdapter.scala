/**
 * Copyright (C) 2011 LShift Ltd.
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
package net.lshift.diffa.agent.auth

import org.springframework.security.core.userdetails.{UsernameNotFoundException, UserDetails, UserDetailsService}
import scala.collection.JavaConversions._
import org.springframework.security.access.PermissionEvaluator
import java.io.Serializable
import org.springframework.security.core.{GrantedAuthority, Authentication}
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.util.MissingObjectException
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * Adapter for providing UserDetailsService on top of the underlying Diffa user store.
 */
class UserDetailsAdapter(val systemConfigStore:SystemConfigStore)
    extends UserDetailsService
    with PermissionEvaluator {
  def loadUserByUsername(username: String) = {
    val user = try {
      systemConfigStore.getUser(username)
    } catch {
      case _:MissingObjectException => throw new UsernameNotFoundException(username)
    }

    val isRoot = user.superuser
    val memberships = systemConfigStore.listDomainMemberships(username)
    val domainAuthorities = memberships.map(m => DomainAuthority(m.domain.name, "user"))
    val authorities = domainAuthorities ++ Seq(new SimpleGrantedAuthority("user")) ++ (isRoot match {
      case true   => Seq(new SimpleGrantedAuthority("root"))
      case false  => Seq()
    })

    new UserDetails() {
      def getAuthorities = authorities.toList
      def getPassword = user.passwordEnc
      def getUsername = username
      def isAccountNonExpired = true
      def isAccountNonLocked = true
      def isCredentialsNonExpired = true
      def isEnabled = true
    }
  }

  def hasPermission(auth: Authentication, targetDomainObject: AnyRef, permission: AnyRef) = {
    permission match {
        // If we're asking for a domain-user, then return true if the provided authentication
        // has a DomainAuthority for the given domain (or is a root user)
      case "domain-user" =>
        val domain = targetDomainObject.asInstanceOf[String]
        isRoot(auth) || hasDomainRole(auth, domain, "user")

        // Unknown permission request type
      case _ =>
        false
    }
  }

  def hasPermission(auth: Authentication, targetId: Serializable, targetType: String, permission: AnyRef) = false

  def isRoot(auth: Authentication) = auth.getAuthorities.find(_.getAuthority == "root").isDefined
  def hasDomainRole(auth: Authentication, domain:String, role:String) = auth.getAuthorities.find {
      case DomainAuthority(grantedDomain, grantedRole) =>
        domain == grantedDomain && role == grantedRole
      case _ =>
        false
    }.isDefined
}

case class DomainAuthority(domain:String, domainRole:String) extends GrantedAuthority {
  def getAuthority = domainRole + "@" + domain
}