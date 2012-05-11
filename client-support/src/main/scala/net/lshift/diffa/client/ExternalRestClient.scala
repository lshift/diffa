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

package net.lshift.diffa.client

import com.sun.jersey.api.client.config.{ClientConfig, DefaultClientConfig}
import org.codehaus.jackson.jaxrs.JacksonJsonProvider
import javax.ws.rs.core.MediaType
import org.slf4j.{LoggerFactory, Logger}
import org.apache.commons.io.IOUtils
import java.io.Closeable
import java.lang.RuntimeException
import com.sun.jersey.api.client.{WebResource, UniformInterfaceException, ClientResponse, Client}
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter

/**
 * Abstract super class to create RESTful clients for usage outside the agent.
 */
abstract class ExternalRestClient(val serverRootUrl:String, val restResourceSubUrl:String, params: RestClientParams = RestClientParams.default)
  extends Closeable {

  val log:Logger = LoggerFactory.getLogger(classOf[ExternalRestClient])

  log.debug("Configured to initialize using the server URL (" + serverRootUrl + ") with a sub URL (" + restResourceSubUrl + ")")

  def resourcePath = restResourceSubUrl

  private var isClosing = false

  val config = new DefaultClientConfig()
  config.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, true.asInstanceOf[AnyRef])
  config.getProperties().put(ClientConfig.PROPERTY_CONNECT_TIMEOUT, params.connectTimeout.getOrElse(null))
  config.getProperties().put(ClientConfig.PROPERTY_READ_TIMEOUT, params.readTimeout.getOrElse(null))
  config.getClasses().add(classOf[JacksonJsonProvider]);
  val client = Client.create(config)
  if (params.hasCredentials) {
    client.addFilter(new HTTPBasicAuthFilter(params.username.get, params.password.get))
  }

  val serverRootResource = client.resource(serverRootUrl)

  def resource = params.token match {
    case None         => serverRootResource.path(resourcePath)
    case Some(token)  => serverRootResource.path(resourcePath).queryParam("authToken", token)
  }

  override def close() = {
    // TODO race condition: two callers can both call client.destroy
    if (!isClosing) {
      isClosing = true
      client.destroy
    }    
  }

  def handleHTTPError(x:Int, path:WebResource, status:ClientResponse.Status) =
    throw new RuntimeException("HTTP %s for resource %s ; Reason: %s".format(x, path ,status.getReasonPhrase))

  // TODO This is quite sketchy at the moment
  def rpc(path:String) = {
    val response = resource.path(path).get(classOf[String])
    response
  }

  def executeRpc(path:String, body:String) : Option[String] = {
    val submitEndpoint = resource.path(path).`type`(MediaType.APPLICATION_JSON_TYPE)
    val response = submitEndpoint.post(classOf[ClientResponse], body)
    response.getStatus match {
      case 200 => Some(IOUtils.toString(response.getEntityInputStream, "UTF-8"))
      case _   => {
        log.error(response.getStatus + "")
        None
      }
    }

  }

  def submit(path:String, body:String) = {
    val submitEndpoint = resource.path(path).`type`(MediaType.APPLICATION_JSON_TYPE)
    val response = submitEndpoint.post(classOf[ClientResponse], body)
    response
  }

  def rpc[T](path:String, t:Class[T], queryParams: (String, String)*) = {
    try {
      queryParams.foldLeft(resource.path(path))((webResource, pair) => webResource.queryParam(pair._1, pair._2)).get(t)
    }
    catch {
      case x:UniformInterfaceException => {
        x.getResponse.getStatus match {
          case 403 => throw new AccessDeniedException(path)
          case 404 => throw new NotFoundException(path)
          case _   => throw x
        }
      }
    }
  }

  def create (where:String, what:Any) {
    val endpoint = resource.path(where)

    def logError(status:Int) = {
      log.error("HTTP %s: Could not create resource %s at %s".format(status, what, endpoint.getURI))
    }

    val media = endpoint.`type`(MediaType.APPLICATION_JSON_TYPE)
    val response = media.post(classOf[ClientResponse], what)
    response.getStatus match {
      case 201 => ()
      case 400 => {
        logError(response.getStatus)
        throw new BadRequestException(response.getStatus + "")
      }
      case _   => {
        logError(response.getStatus)
        throw new RuntimeException(response.getStatus + "")
      }
    }
  }

  def delete(where: String) = {
    val endpoint = resource.path(where)

    def logError(status:Int) = {
      log.error("HTTP %s: Could not delete resource %s".format(status, endpoint.getURI))
    }

    val media = endpoint.`type`(MediaType.APPLICATION_JSON_TYPE)
    val response = media.delete(classOf[ClientResponse])
    response.getStatus match {
      case 200 | 204 =>
        response
      case 404 => {
        logError(response.getStatus)
        throw new NotFoundException(where)
      }
      case _ => {
        logError(response.getStatus)
        throw new RuntimeException(response.getStatus.toString)
      }
    }
  }
}

/**
 * This exception denotes an HTTP 404 exception
 */
class NotFoundException(resource:String) extends RuntimeException(resource)

/**
 * Denotes an invalid request
 */
class BadRequestException(resource: String) extends RuntimeException(resource)

/**
 * Denotes access being denied to a resource.
 */
class AccessDeniedException(resource: String) extends RuntimeException(resource)