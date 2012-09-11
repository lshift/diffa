/**
 * Copyright (C) 2012 LShift Ltd.
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

import org.springframework.security.core.Authentication
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider
import org.slf4j.{LoggerFactory, Logger}
import scala.collection.JavaConversions._
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.util.StringUtils
import org.springframework.security.authentication.{BadCredentialsException, AuthenticationProvider}
import net.lshift.diffa.kernel.lifecycle.{NotificationCentre, AgentLifecycleAware}
import net.lshift.diffa.kernel.frontend.SystemConfigListener
import org.springframework.security.ldap.DefaultSpringSecurityContextSource
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch
import org.springframework.security.ldap.authentication.{NullLdapAuthoritiesPopulator, LdapAuthenticationProvider, BindAuthenticator}
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator
import net.lshift.diffa.kernel.config.system.PolicyKey._
import net.lshift.diffa.kernel.config.system.{PolicyKey, SystemConfigStore}

/**
 * Runtime controllable authentication provider implementation, that will proxy to an internally configured LDAP
 * implementation if configuration is available.
 */
class ExternalAuthenticationProviderSwitch(val configStore:SystemConfigStore)
    extends AuthenticationProvider
    with AgentLifecycleAware
    with SystemConfigListener {

  val AD_DOMAIN_PROPERTY = "activedirectory.domain"
  val AD_SERVER_PROPERTY = "activedirectory.server"

  val LDAP_URL_PROPERTY = "ldap.url"
  val LDAP_DN_PATTERN_PROPERTY = "ldap.userdn.pattern"
  val LDAP_USER_SEARCH_BASE_PROPERTY = "ldap.user.search.base"
  val LDAP_USER_SEARCH_FILTER_PROPERTY = "ldap.user.search.filter"
  val LDAP_GROUP_SEARCH_BASE_PROPERTY = "ldap.group.search.base"
  val LDAP_GROUP_SEARCH_FILTER_PROPERTY = "ldap.group.search.filter"
  val LDAP_GROUP_ROLE_ATTRIBUTE_PROPERTY = "ldap.group.role.attribute"

  val RECONFIGURE_PROPERTIES = Seq(
    AD_DOMAIN_PROPERTY, AD_SERVER_PROPERTY,
    LDAP_URL_PROPERTY, LDAP_DN_PATTERN_PROPERTY, LDAP_USER_SEARCH_BASE_PROPERTY, LDAP_USER_SEARCH_FILTER_PROPERTY,
    LDAP_GROUP_SEARCH_BASE_PROPERTY, LDAP_GROUP_SEARCH_FILTER_PROPERTY, LDAP_GROUP_ROLE_ATTRIBUTE_PROPERTY
  )

  val log:Logger = LoggerFactory.getLogger(getClass)

  override def onAgentInstantiationCompleted(nc: NotificationCentre) {
    nc.registerForSystemConfigEvents(this)
  }

  // Generate a backing provider based upon the settings that are available

  var backingProvider:Option[AuthenticationProvider] = loadBackingProvider

  def authenticate(authentication: Authentication) = backingProvider match {
    case Some(ap) => {
      // None of the providers prevent an empty password being used, but the ActiveDirectory provider actually throws
      // a nasty exception if one is used.
      if (!StringUtils.hasLength(authentication.getCredentials.toString)) {
        throw new BadCredentialsException("Empty Password")
      }

      enhanceResult(ap.authenticate(authentication))
    }
    case None     => null
  }

  def supports(authentication: Class[_]):Boolean = backingProvider match {
    case Some(ap) => ap.supports(authentication)
    case None     => false
  }

  private def loadBackingProvider = {
    // Look for configuration for various authentication mechanisms
    val activeDirectoryDomain = configStore.maybeSystemConfigOption(AD_DOMAIN_PROPERTY)
    val ldapServerUrl = configStore.maybeSystemConfigOption(LDAP_URL_PROPERTY)

    if (activeDirectoryDomain.isDefined) {
      val domain = activeDirectoryDomain.get
      val activeDirectoryServer = configStore.systemConfigOptionOrDefault(AD_SERVER_PROPERTY, "ldap://" + domain + "/")

      log.info("Using ActiveDirectory authentication for domain %s with server %s".format(domain, activeDirectoryServer))

      Some(new ActiveDirectoryLdapAuthenticationProvider(domain, activeDirectoryServer))
    } else if (ldapServerUrl.isDefined) {
      val serverUrl = ldapServerUrl.get
      val configDetailBuilder = new StringBuilder

      val contextSource = new DefaultSpringSecurityContextSource(serverUrl);
      contextSource.afterPropertiesSet()
      val bindAuthenticator = new BindAuthenticator(contextSource)

      // Possibly configure DN patterns
      configStore.maybeSystemConfigOption(LDAP_DN_PATTERN_PROPERTY) match {
        case Some(dnPattern) =>
          bindAuthenticator.setUserDnPatterns(Array(dnPattern))
          configDetailBuilder.append(", with User DN pattern '%s'".format(dnPattern))
        case None            => // Leave the user dn patterns blank
      }

      // Possibly configure search mechanisms
      configStore.maybeSystemConfigOption(LDAP_USER_SEARCH_FILTER_PROPERTY) match {
        case Some(filter) =>
          val userSearchBase = configStore.systemConfigOptionOrDefault(LDAP_USER_SEARCH_BASE_PROPERTY, "")
          configDetailBuilder.append(", with User Search Filter '%s' and search base '%s'".format(filter, userSearchBase))

          bindAuthenticator.setUserSearch(new FilterBasedLdapUserSearch(userSearchBase, filter, contextSource))
        case None =>
          // No search mechanism
      }

      val authoritiesPopulator = configStore.maybeSystemConfigOption(LDAP_GROUP_SEARCH_BASE_PROPERTY) match {
        case Some(groupSearchBase) =>
          val populator = new DefaultLdapAuthoritiesPopulator(contextSource, groupSearchBase)
          populator.setRolePrefix("")   // We need to call these deprecated methods to prevent "ROLE_" being applied
          populator.setConvertToUpperCase(false)
          configDetailBuilder.append(", with Group Search Base '%s'".format(groupSearchBase))

          configStore.maybeSystemConfigOption(LDAP_GROUP_SEARCH_FILTER_PROPERTY) match {
            case Some(filter) =>
              configDetailBuilder.append(" and filter '%s'".format(filter))
              populator.setGroupSearchFilter(filter)
            case None         => // Leave as the default '(member={0})' where {0} is the user's full DN
          }

          configStore.maybeSystemConfigOption(LDAP_GROUP_ROLE_ATTRIBUTE_PROPERTY) match {
            case Some(attribute) =>
              configDetailBuilder.append(" using role attribute '%s'".format(attribute))
              populator.setGroupRoleAttribute(attribute)
            case None            => // Leave as the default 'cn'
          }
          populator
        case None =>
          new NullLdapAuthoritiesPopulator
      }

      log.info("Using LDAP authentication with server %s%s".format(serverUrl, configDetailBuilder))

      Some(new LdapAuthenticationProvider(bindAuthenticator, authoritiesPopulator))
    } else {
      log.info("No external authentication providers configured")

      None
    }
  }

  private def enhanceResult(res:Authentication):Authentication = res match {
    case null => null
    case _    => {
      val username = res.getPrincipal.asInstanceOf[UserDetails].getUsername

      // Process the user and their authorities (groups) to generate a list of domains that the user can access. Also,
      // if a builtin account (for either the user or the group) has root privileges, then this user will be given a
      // root authority.
      val identities = Seq(username) ++ res.getAuthorities.map(a => a.getAuthority)
      val memberships = identities.flatMap(i => configStore.listDomainMemberships(i))
      val domainAuthorities = memberships.flatMap(m =>
        configStore.lookupPolicyStatements(PolicyKey(m.policySpace, m.policy)).map(p => SpaceAuthority(m.space, m.domain, p)))
      val isRoot = configStore.containsRootUser(identities)
      val authorities = domainAuthorities ++ Seq(new SimpleGrantedAuthority("user")) ++ (isRoot match {
        case true   => Seq(new SimpleGrantedAuthority("root"))
        case false  => Seq()
      })

      new Authentication {
        def getDetails = res.getDetails
        def getCredentials = res.getCredentials
        def setAuthenticated(isAuthenticated: Boolean) { res.setAuthenticated(isAuthenticated) }
        def getPrincipal = res.getPrincipal
        def getAuthorities = authorities
        def getName = res.getName
        def isAuthenticated = res.isAuthenticated
      }
    }
  }

  def configPropertiesUpdated(properties: Seq[String]) {
    // Reload the backing provider if any relevant configuration properties are changed
    val relevantUpdate = properties.find(prop => RECONFIGURE_PROPERTIES.contains(prop)).isDefined
    if (relevantUpdate) {
      backingProvider = loadBackingProvider
    }
  }
}