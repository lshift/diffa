/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.agent.auth

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.springframework.security.core.{AuthenticationException, Authentication}
import javax.servlet.FilterChain

class FormAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
  override def successfulAuthentication(request: HttpServletRequest, response: HttpServletResponse,
                                        chain: FilterChain, authResult: Authentication) {
    super.successfulAuthentication(request, response, chain, authResult)

    request.getSession.setAttribute("username", request.getParameter("j_username"))
  }

  override def unsuccessfulAuthentication(request: HttpServletRequest, response: HttpServletResponse,
                                          failed: AuthenticationException) {
    super.unsuccessfulAuthentication(request, response, failed)

    request.getSession().removeAttribute("username")
  }
}
