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

package net.lshift.diffa.participants

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.eclipse.jetty.server.{Request, Server}
import net.lshift.diffa.adapter.content.{ContentParticipantHandler, ContentParticipantDelegator}
import net.lshift.diffa.adapter.scanning.ScanningParticipantRequestHandler
import net.lshift.diffa.adapter.correlation.{VersioningParticipantHandler, VersioningParticipantDelegator}
import net.lshift.diffa.adapter.common.ServletHelper
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.security.authentication.BasicAuthenticator
import org.eclipse.jetty.security.{HashLoginService, ConstraintMapping, ConstraintSecurityHandler}
import org.eclipse.jetty.util.security.{Credential, Constraint}
import org.slf4j.LoggerFactory

/**
 * Indicates what sort of authentication strategy the server should implement
 */
trait AuthenticationMechanism

/**
 * This signals to the server that it doesn't need to perform any authentication at all.
 */
object NoAuthentication extends AuthenticationMechanism

/**
 * The server will perform HTTP Basic Auth allowing the specified username/password combinations only access to the
 * underlying resource.
 */
case class BasicAuthenticationMechanism(users:Map[String,String]) extends AuthenticationMechanism

/**
 * This instructs the server to verify the value of a particular query parameter when authenticating requests.
 */
case class QueryParameterAuthenticationMechanism(name:String, value:String) extends AuthenticationMechanism


class ParticipantRpcServer(val port: Int,
                           scanning:ScanningParticipantRequestHandler,
                           content:ContentParticipantHandler,
                           versioning:VersioningParticipantHandler,
                           authenticationMechanism:AuthenticationMechanism) {

  val handler = authenticationMechanism match {
    case NoAuthentication =>
      new ParticipantHandler(scanning, content, versioning, NoopRequestAuthenticator)
    case b:BasicAuthenticationMechanism =>
      val authHandler = basicAuthHandler(b.users)
      authHandler.setHandler(new ParticipantHandler(scanning,content,versioning, NoopRequestAuthenticator))
      authHandler
    case q:QueryParameterAuthenticationMechanism =>
      val authenticator = new QueryParameterAuthenticator(q.name, q.value)
      new ParticipantHandler(scanning,content,versioning, authenticator)
  }



  private val server = new Server(port)
  server.setStopAtShutdown(true)
  server.setHandler(handler)
  
  def start: Unit = {
    server.start
  }

  def stop: Unit = {
    server.stop
  }

  private def basicAuthHandler(users:Map[String,String]) = {

    val loginService = new HashLoginService();
    users.foreach{ case (user, pass) => loginService.putUser(user, Credential.getCredential(pass), Array("user"))}

    val constraint = new Constraint();
    constraint.setName(Constraint.__BASIC_AUTH)
    constraint.setRoles(Array("user"))
    constraint.setAuthenticate(true)

    val cm = new ConstraintMapping();
    cm.setConstraint(constraint);
    cm.setPathSpec("/*");

    val csh = new ConstraintSecurityHandler();
    csh.setAuthenticator(new BasicAuthenticator());
    csh.addConstraintMapping(cm);
    csh.setLoginService(loginService);

    csh

  }
}


trait CustomRequestAuthenticator {
  def allowRequest(request: HttpServletRequest) : Boolean
}

object NoopRequestAuthenticator extends CustomRequestAuthenticator {
  def allowRequest(request: HttpServletRequest) = true
}

class QueryParameterAuthenticator(name:String,value:String) extends CustomRequestAuthenticator {

  val log = LoggerFactory.getLogger(getClass)

  def allowRequest(request: HttpServletRequest) = {
    val parameter = request.getParameter(name)
    val shouldPass = (parameter != null && parameter == value)

    if (!shouldPass) {
      val authHeader = request.getHeader("Authorization")
      if (authHeader != null) {
        log.warn("Received Authorization Header (%s) when using a query parameter to authenticate, is this potentially a bug?".format(authHeader))
      }
    }

    shouldPass
  }
}

class ParticipantHandler(scanning:ScanningParticipantRequestHandler,
                         content:ContentParticipantHandler,
                         versioning:VersioningParticipantHandler,
                         authenticator:CustomRequestAuthenticator) extends AbstractHandler {

  val log = LoggerFactory.getLogger(getClass)

  private val contentAdapter = new ContentParticipantDelegator(content)
  private val versioningAdapter = new VersioningParticipantDelegator(versioning)

  override def handle(target: String, jettyReq: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {

    if (!authenticator.allowRequest(request)) {
      log.info("Request forbidden [target=%s]".format(target))
      response.setStatus(HttpServletResponse.SC_FORBIDDEN)
      ServletHelper.writeResponse(response, "Forbidden")
    }
    else {
      if (target.startsWith("/scan")) {

        log.info("Processing scan request [target=%s]".format(target))
        scanning.handleRequest(request, response)

      } else if (target.startsWith("/content")) {

        log.info("Processing content request [target=%s]".format(target))
        contentAdapter.handleRequest(request, response)

      } else if (versioning != null && target.startsWith("/corr-version")) {

        log.info("Processing versioning request [target=%s]".format(target))
        versioningAdapter.handleRequest(request, response)

      } else {

        log.info("Invalid target: %s".format(target))
        response.setStatus(HttpServletResponse.SC_NOT_FOUND)
        ServletHelper.writeResponse(response, "Unknown path " + target)

      }
    }

    jettyReq.setHandled(true)
  }
}