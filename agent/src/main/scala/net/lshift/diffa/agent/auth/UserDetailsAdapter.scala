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
import net.lshift.diffa.kernel.util.MissingObjectException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import net.lshift.diffa.kernel.config.User
import net.lshift.diffa.kernel.config.system.{PolicyStatement, PolicyKey, SystemConfigStore}

/**
 * Adapter for providing UserDetailsService on top of the underlying Diffa user store.
 */
class UserDetailsAdapter(val systemConfigStore:SystemConfigStore)
    extends UserDetailsService
    with PermissionEvaluator
    with TargetEnhancer {

  def loadUserByUsername(username: String) = {
    val user = try {
      systemConfigStore.getUser(username)
    } catch {
      case _:MissingObjectException => throw new UsernameNotFoundException(username)
    }

    extractDetails(user)
  }

  def loadUserByToken(token: String) = {
    val user = try {
      systemConfigStore.getUserByToken(token)
    } catch {
      case _:MissingObjectException => throw new UsernameNotFoundException(token)
    }

    extractDetails(user)
  }

  def hasPermission(auth: Authentication, targetDomainObject: AnyRef, permission: AnyRef) = {
    permission match {
        // Tests to see whether the requesting user is the owner of the requested object
      case UserPrivilege("user-preferences") =>
        /*
        os.ten.si.ble [o-sten-suh-buhl]
        adjective
        1. outwardly appearing as such; professed; pretended: an ostensible cheerfulness concealing sadness.
        2.apparent, evident, or conspicuous: the ostensible truth of their theories.
        */
        val ostensibleUsername = targetDomainObject.asInstanceOf[UserTarget].username
        isUserWhoTheyClaimToBe(auth, ostensibleUsername)

        // For any other permission, check to see if the user has the privilege, or if they are a root user.
      case privilege:Privilege =>
        val target = targetDomainObject.asInstanceOf[TargetObject]
        isRoot(auth) || hasTargetPrivilege(auth, target, privilege)


        // Unknown permission request type
      case _ =>
        false
    }
  }

  def hasPermission(auth: Authentication, targetId: Serializable, targetType: String, permission: AnyRef) = false

  def extractDetails(user:User) = {
    val isRoot = user.superuser
    val memberships = systemConfigStore.listDomainMemberships(user.name)
    val domainAuthorities = memberships.flatMap(m =>
      systemConfigStore.lookupPolicyStatements(PolicyKey(m.policySpace, m.policy)).map(p => SpaceAuthority(m.space, m.domain, p)))
    val authorities = domainAuthorities ++
                      Seq(new SimpleGrantedAuthority("user"), new UserAuthority(user.name)) ++
    (isRoot match {
      case true   => Seq(new SimpleGrantedAuthority("root"))
      case false  => Seq()
    })

    new UserDetails() {
      def getAuthorities = authorities.toList
      def getPassword = user.passwordEnc
      def getUsername = user.name
      def isAccountNonExpired = true
      def isAccountNonLocked = true
      def isCredentialsNonExpired = true
      def isEnabled = true
    }
  }
  def isRoot(auth: Authentication) = auth.getAuthorities.find(_.getAuthority == "root").isDefined
  def hasTargetPrivilege(auth: Authentication, target:TargetObject, privilege:Privilege) = {
    target.enhance(this)

    auth.getAuthorities.find {
      case SpaceAuthority(grantedSpace, grantedSpacePath, grantedPrivilegeStmt) =>
        if (grantedPrivilegeStmt.privilege == privilege.name) {
          privilege.isValidForTarget(grantedSpace, grantedPrivilegeStmt, target)
        } else {
          false
        }
      case _ =>
        false
    }.isDefined
  }

  def isUserWhoTheyClaimToBe(auth: Authentication, ostensibleUsername:String) = auth.getAuthorities.find {
    case UserAuthority(actualUsername) => actualUsername == ostensibleUsername
    case _                             => false
  }.isDefined

  def expandSpaceParents(space: Long) = systemConfigStore.listSuperspaceIds(space)
}

case class SpaceAuthority(space:Long, spacePath:String, statement:PolicyStatement) extends GrantedAuthority {
  def getAuthority = statement + "@" + spacePath
}

case class UserAuthority(username:String) extends GrantedAuthority {
  def getAuthority = username
}