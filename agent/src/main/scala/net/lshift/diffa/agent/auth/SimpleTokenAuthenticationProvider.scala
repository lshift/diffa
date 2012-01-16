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

import java.lang.Class
import org.springframework.util.Assert
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.core.Authentication
import org.slf4j.{LoggerFactory, Logger}
import org.springframework.security.authentication.{BadCredentialsException, AuthenticationServiceException, AuthenticationProvider}

/**
 * Filter allowing login to be performed using a token stored against a user account.
 */
class SimpleTokenAuthenticationProvider(val userDetails: UserDetailsAdapter) extends AuthenticationProvider {
  val log:Logger = LoggerFactory.getLogger(getClass)

  def authenticate(authentication: Authentication) = {
    Assert.isInstanceOf(classOf[SimpleTokenAuthenticationToken], authentication, "Only SimpleTokenAuthenticationToken is supported")

    val details = retrieveUser(authentication.getPrincipal.toString)

    val result = new SimpleTokenAuthenticationToken(authentication.getPrincipal.toString, details.getAuthorities)
    result.setDetails(authentication.getDetails)

    result
  }

  def supports(authentication: Class[_]) = classOf[SimpleTokenAuthenticationToken].isAssignableFrom(authentication)

  def retrieveUser(token:String) = {
    try {
      val loadedUser = userDetails.loadUserByToken(token)

      Assert.notNull(loadedUser, "UserDetailsAdapter should throw an exception, not return null")

      loadedUser
    } catch {
      case notFound: UsernameNotFoundException => {
        log.debug("User with token '" + notFound.getMessage + "' not found")
        throw new BadCredentialsException("Bad token")
      }
      case repositoryProblem => throw new AuthenticationServiceException(repositoryProblem.getMessage, repositoryProblem)
    }
  }

}

