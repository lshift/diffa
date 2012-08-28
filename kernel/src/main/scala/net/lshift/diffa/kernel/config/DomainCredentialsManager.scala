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
package net.lshift.diffa.kernel.config

import net.lshift.diffa.kernel.frontend.{OutboundExternalHttpCredentialsDef, InboundExternalHttpCredentialsDef}
import java.net.URI

/**
 * Interface the administration and retrieval of external credentials.
 * This interface is split into a basic interface that will not
 * return any secrets from the database and an interface that only internal components can use.
 */
trait DomainCredentialsManager {

  /**
   * Adds a new set of external HTTP credentials for the given domain.
   */
  def addExternalHttpCredentials(space:Long, creds:InboundExternalHttpCredentialsDef)

  /**
   * Removes the external HTTP credentials for the given domain and url
   */
  def deleteExternalHttpCredentials(space:Long, url:String)

  /**
   * Lists all credentials in the current domain.
   */
  def listCredentials(space:Long) : Seq[OutboundExternalHttpCredentialsDef]
}

/**
 * This provides a mechanism for internal components to retrieve credentials for an external URL.
 * Note that this interface is designed exclusively for internal consumption, since it can supply
 * a client with sensitive credentials.
 */
trait DomainCredentialsLookup {
  /**
   * Returns the most specific credentials that matches the given URL
   */
  def credentialsForUrl(space:Long, url:String) : Option[HttpCredentials]

  /**
   * Returns the most specific credentials that matches the given URI
   */
  def credentialsForUri(space:Long, uri:URI) : Option[HttpCredentials]
}

trait HttpCredentials
case class BasicAuthCredentials(username:String, password:String) extends HttpCredentials
case class QueryParameterCredentials(name:String, value:String) extends HttpCredentials

/**
 * Stubbed out provider of the DomainCredentialsLookup interface that always responds with the same
 * credentials. This is used for testing purposes.
 */
class FixedDomainCredentialsLookup(space:Long, credentials:Option[HttpCredentials]) extends DomainCredentialsLookup {

  def credentialsForUrl(space:Long, url: String) = credentialsForUri(space, new URI(url))

  def credentialsForUri(spaceId:Long, uri: URI) = {
    if (space == spaceId) {
      credentials
    }
    else {
      throw new IllegalArgumentException("Wrong domain: supported = " + spaceId + "; requested = " + space)
    }
  }
}

