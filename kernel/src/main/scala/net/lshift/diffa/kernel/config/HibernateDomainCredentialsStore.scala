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

import net.lshift.diffa.kernel.frontend.FrontendConversions._
import net.lshift.diffa.kernel.frontend.{OutboundExternalHttpCredentialsDef, InboundExternalHttpCredentialsDef}
import net.lshift.diffa.kernel.util.db.{HibernateQueryUtils, DatabaseFacade}
import net.lshift.diffa.kernel.util.db.SessionHelper._
import scala.collection.JavaConversions._
import org.hibernate.SessionFactory
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.util.AlertCodes._
import java.net.URI
import net.lshift.diffa.kernel.util.MissingObjectException

class HibernateDomainCredentialsStore(val sessionFactory: SessionFactory, db:DatabaseFacade)
  extends DomainCredentialsManager
  with DomainCredentialsLookup
  with HibernateQueryUtils {

  val logger = LoggerFactory.getLogger(getClass)

  def addExternalHttpCredentials(domain:String, creds:InboundExternalHttpCredentialsDef) = sessionFactory.withSession( s => {
    creds.validate()
    s.saveOrUpdate(fromInboundExternalHttpCredentialsDef(domain, creds))
  })

  def deleteExternalHttpCredentials(domain:String, url:String) = sessionFactory.withSession( s => {
    val deleted = s.getNamedQuery("deleteExternalHttpCredentials").
                    setString("domain", domain).
                    setString("url", url).
                    executeUpdate()
    if (deleted == 0) {
      throw new MissingObjectException(url)
    }
  })

  def listCredentials(domain:String) : Seq[OutboundExternalHttpCredentialsDef] = sessionFactory.withSession( s => {
    val resultSet = s.getNamedQuery("externalHttpCredentialsByDomain").setString("domain", domain).list()

    // Result set ordering: select url, key, credential_type from ....

    resultSet.map(result => {
      val row = result.asInstanceOf[Array[_]].map(e => e.asInstanceOf[String])
      OutboundExternalHttpCredentialsDef(url = row(0), key = row(1), `type` = row(2))
    })
  })

  def credentialsForUrl(domain:String, url:String) : Option[HttpCredentials] = credentialsForUri(domain, new URI(url))

  def credentialsForUri(domain:String, searchURI:URI) = sessionFactory.withSession( s => {

    val baseUrl = searchURI.getScheme + "://" + searchURI.getAuthority + "%"
    val results = db.listQuery[ExternalHttpCredentials]("externalHttpCredentialsByDomainAndUrl",
                                                     Map("domain" -> domain, "base_url" -> baseUrl))

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
  })
}
