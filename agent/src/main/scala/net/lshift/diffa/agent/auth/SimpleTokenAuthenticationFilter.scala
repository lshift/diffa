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

import org.springframework.web.filter.GenericFilterBean
import javax.servlet.{FilterChain, ServletResponse, ServletRequest}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.AuthenticationException
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.NameValuePair
import java.util.{ArrayList, Scanner, Collections}

/**
 * Filter allowing login to be performed using a token stored against a user account.
 */
class SimpleTokenAuthenticationFilter(authenticationManager:AuthenticationManager) extends GenericFilterBean {
  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
    val request = req.asInstanceOf[HttpServletRequest]
    val response = res.asInstanceOf[HttpServletResponse]

    if (SecurityContextHolder.getContext.getAuthentication == null) {
      // Look for an authentication token
      val authToken = getAuthTokenParameter(request)

      if (authToken != null) {
        try {
          val tokenAuth = authenticationManager.authenticate(new SimpleTokenAuthenticationToken(authToken))

          SecurityContextHolder.getContext.setAuthentication(tokenAuth)
        } catch {
          case failed:AuthenticationException => {
            SecurityContextHolder.clearContext()

            logger.debug("Authentication request for failed: " + failed)
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, failed.getMessage)
            
            return
          }
        }
      }
    }

    chain.doFilter(request, response)
  }

  /**
   * Extracts the token parameter from the request. Note that if we were to just use request.getParameter,
   * then the request body would be parsed for POST requests, which breaks Spring's ability to use the request body
   * in some circumstances (we specifically see this on the multiple-config method, where we ask spring to give us
   * a Form object containing all the properties).
   */
  def getAuthTokenParameter(request:HttpServletRequest):String = {
    val params = new ArrayList[NameValuePair]

    val query = request.getQueryString
    if (query != null && query.length > 0) {
      URLEncodedUtils.parse(params, new Scanner(query), "UTF-8")
    }

    for (i <- 0 until params.size()) {
      if (params.get(i).getName == "authToken") return params.get(i).getValue
    }

    null
  }
}

