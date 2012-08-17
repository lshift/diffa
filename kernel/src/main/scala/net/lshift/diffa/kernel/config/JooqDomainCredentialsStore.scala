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

import net.lshift.diffa.schema.Tables._
import net.lshift.diffa.kernel.frontend.{OutboundExternalHttpCredentialsDef, InboundExternalHttpCredentialsDef}
import scala.collection.JavaConversions._
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.util.AlertCodes._
import java.net.URI
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.schema.tables.ExternalHttpCredentials.EXTERNAL_HTTP_CREDENTIALS
import net.lshift.diffa.schema.jooq.DatabaseFacade
import java.lang.{Long => LONG}

class JooqDomainCredentialsStore(val db: DatabaseFacade, spacePathCache:SpacePathCache)
  extends DomainCredentialsManager
  with DomainCredentialsLookup {

  val logger = LoggerFactory.getLogger(getClass)

  def addExternalHttpCredentials(domain:String, creds:InboundExternalHttpCredentialsDef) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    db.execute { t =>
      creds.validate()

      t.insertInto(EXTERNAL_HTTP_CREDENTIALS).
        set(EXTERNAL_HTTP_CREDENTIALS.SPACE, space.id:LONG).
        set(EXTERNAL_HTTP_CREDENTIALS.URL, creds.url).
        set(EXTERNAL_HTTP_CREDENTIALS.CRED_TYPE, creds.`type`).
        set(EXTERNAL_HTTP_CREDENTIALS.CRED_KEY, creds.key).
        set(EXTERNAL_HTTP_CREDENTIALS.CRED_VALUE, creds.value).
        onDuplicateKeyUpdate().
        set(EXTERNAL_HTTP_CREDENTIALS.CRED_TYPE, creds.`type`).
        set(EXTERNAL_HTTP_CREDENTIALS.CRED_KEY, creds.key).
        set(EXTERNAL_HTTP_CREDENTIALS.CRED_VALUE, creds.value).
        execute()
    }
  }

  def deleteExternalHttpCredentials(domain:String, url:String) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    db.execute { t =>
      val deleted =
        t.delete(EXTERNAL_HTTP_CREDENTIALS).
          where(EXTERNAL_HTTP_CREDENTIALS.SPACE.equal(space.id)).
          and(EXTERNAL_HTTP_CREDENTIALS.URL.equal(url)).
          execute()

      if (deleted == 0) {
        throw new MissingObjectException(url)
      }
    }
  }

  def listCredentials(domain:String) : Seq[OutboundExternalHttpCredentialsDef] = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    db.execute { t =>
      t.select().from(EXTERNAL_HTTP_CREDENTIALS).where(EXTERNAL_HTTP_CREDENTIALS.SPACE.equal(space.id)).fetch().map { r =>
        OutboundExternalHttpCredentialsDef(
          url = r.getValue(EXTERNAL_HTTP_CREDENTIALS.URL),
          key = r.getValue(EXTERNAL_HTTP_CREDENTIALS.CRED_KEY),
          `type` = r.getValue(EXTERNAL_HTTP_CREDENTIALS.CRED_TYPE)
        )
      }
    }
  }

  def credentialsForUrl(domain:String, url:String) : Option[HttpCredentials] = credentialsForUri(domain, new URI(url))

  def credentialsForUri(domain:String, searchURI:URI) = {

    val space = spacePathCache.resolveSpacePathOrDie(domain)

    db.execute { t =>

      val baseUrl = searchURI.getScheme + "://" + searchURI.getAuthority + "%"

      val results = t.selectFrom(EXTERNAL_HTTP_CREDENTIALS).
        where(EXTERNAL_HTTP_CREDENTIALS.SPACE.equal(space.id)).
        and(EXTERNAL_HTTP_CREDENTIALS.URL.like(baseUrl)).
        fetch().map { r =>
        ExternalHttpCredentials(
          domain = domain,
          url = r.getValue(EXTERNAL_HTTP_CREDENTIALS.URL),
          key = r.getValue(EXTERNAL_HTTP_CREDENTIALS.CRED_KEY),
          value = r.getValue(EXTERNAL_HTTP_CREDENTIALS.CRED_VALUE),
          credentialType = r.getValue(EXTERNAL_HTTP_CREDENTIALS.CRED_TYPE)
        )
      }

      if (results.isEmpty) {
        None
      }
      else {
        val candidateCredentials = results.map(c =>  {
          c.credentialType match {
            case ExternalHttpCredentials.BASIC_AUTH      => ( new URI(c.url), BasicAuthCredentials(c.key, c.value) )
            case ExternalHttpCredentials.QUERY_PARAMETER => ( new URI(c.url), QueryParameterCredentials(c.key, c.value) )
            case _                                       =>
              // Be very careful not to log a password
              val message = "%s - Wrong credential type for url: %s".
                format(formatAlertCode(domain, INVALID_EXTERNAL_CREDENTIAL_TYPE), searchURI)
              logger.error(message)
              throw new Exception("Wrong credential type")
          }
        }).filter( c => searchURI.getPath.startsWith(c._1.getPath))

        if (candidateCredentials.isEmpty) {
          None
        }
        else {
          val sortedByNumberOfPathSegments = candidateCredentials.sortBy( c => c._1.getPath.split("/").length).reverse
          Some(sortedByNumberOfPathSegments.head._2)
        }

      }
    }
  }
}
