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

package net.lshift.diffa.kernel.config.system

import net.lshift.diffa.kernel.util.db.{DatabaseFacade,HibernateQueryUtils}
import net.lshift.diffa.kernel.util.db.SessionHelper._
import scala.collection.JavaConversions._
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.util.{AlertCodes, MissingObjectException}
import net.lshift.diffa.kernel.differencing.StoreCheckpoint
import org.hibernate.{Query, Session, SessionFactory}
import org.apache.commons.lang.RandomStringUtils
import net.lshift.diffa.kernel.config._

class HibernateSystemConfigStore(val sessionFactory:SessionFactory,
                                 db:DatabaseFacade,
                                 val pairCache:PairCache)
    extends SystemConfigStore with HibernateQueryUtils {

  val logger = LoggerFactory.getLogger(getClass)

  def createOrUpdateDomain(d: Domain) = sessionFactory.withSession( s => {
    pairCache.invalidate(d.name)
    s.saveOrUpdate(d)
  })

  def deleteDomain(domain:String) = sessionFactory.withSession( s => {

    pairCache.invalidate(domain)

    // TODO Why is do we not just do a DELETE CASCADE?

    s.getNamedQuery("deleteExternalHttpCredentialsByDomain").setString("domain",domain).executeUpdate()

    deleteByDomain[EndpointView](s, domain, "endpointViewsByDomain")
    deleteByDomain[Escalation](s, domain, "escalationsByDomain")
    deleteByDomain[PairReport](s, domain, "reportsByDomain")
    deleteByDomain[RepairAction](s, domain, "repairActionsByDomain")
    deleteByDomain[PairView](s, domain, "pairViewsByDomain")
    deleteByDomain[DiffaPair](s, domain, "pairsByDomain")
    deleteByDomain[Endpoint](s, domain, "endpointsByDomain")
    deleteByDomain[ConfigOption](s, domain, "configOptionsByDomain")
    deleteByDomain[Member](s, domain, "membersByDomain")
    removeDomainDifferences(domain)

    s.flush()

    val deleted = s.createSQLQuery("delete from domains where name = '%s'".format(domain)).executeUpdate()
    if (deleted == 0) {
      logger.error("%s: Attempt to delete non-existent domain: %s".format(AlertCodes.INVALID_DOMAIN, domain))
      throw new MissingObjectException(domain)
    }
  })

  def doesDomainExist(name: String) = null != sessionFactory.withSession(s => s.get(classOf[Domain], name))

  def listDomains = db.listQuery[Domain]("allDomains", Map())

  def getPair(pair:DiffaPairRef) : DiffaPair = getPair(pair.domain, pair.key)
  def getPair(domain:String, key: String) = sessionFactory.withSession(s => getPair(s, domain, key))

  def listPairs = db.listQuery[DiffaPair]("allPairs", Map())
  def listEndpoints = db.listQuery[Endpoint]("allEndpoints", Map())


  @Deprecated
  def createOrUpdateUser(user: User) = {
    if (updateUser(user) == 0) {
      createUser(user)
    }
  }

  def createUser(user: User) = db.execute("insertUser", Map(
    "name" -> user.name,
    "password_enc" -> user.passwordEnc,
    "email" -> user.email,
    "superuser" -> user.superuser
  ))

  def updateUser(user: User) = db.execute("updateUser", Map(
    "name" -> user.name,
    "password_enc" -> user.passwordEnc,
    "email" -> user.email,
    "superuser" -> user.superuser
  ))

  def getUserToken(username: String) = {
    sessionFactory.withSession(s => {
      val user = getUser(s, username)
      if (user.token == null) {
        // Generate token on demand
        user.token = RandomStringUtils.randomAlphanumeric(40)
      }
      user.token
    })
  }
  def clearUserToken(username: String) {
    sessionFactory.withSession(s => {
      val user = getUser(s, username)
      user.token = null

      s.saveOrUpdate(user)
    })
  }

  def deleteUser(name: String) = sessionFactory.withSession(s => {
    val user = getUser(s, name)
    s.delete(user)
  })

  def getUser(name: String) : User = sessionFactory.withSession(getUser(_,name))

  // TODO This needs to be cached
  def getUserByToken(token: String) : User
    = db.singleQuery[User]("userByToken", Map("token" -> token), "user token %s".format(token))

  def listUsers : Seq[User] = db.listQuery[User]("allUsers", Map())
  def listDomainMemberships(username: String) : Seq[Member] =
    db.listQuery[Member]("membersByUser", Map("user_name" -> username))

  def containsRootUser(usernames: Seq[String]) : Boolean =
    sessionFactory.withSession(s => {
      val query: Query = s.getNamedQuery("rootUserCount")
      query.setParameterList("user_names", seqAsJavaList(usernames))

      query.uniqueResult().asInstanceOf[java.lang.Long] > 0
    })

  // TODO Add a unit test for this
  def maybeSystemConfigOption(key: String) = {
    sessionFactory.withSession(s => {
      s.get(classOf[SystemConfigOption], key) match {
        case null                       => None
        case current:SystemConfigOption => Some(current.value)
      }
    })
  }
  def setSystemConfigOption(key:String, value:String) {
    sessionFactory.withSession(s => {
      val co = s.get(classOf[SystemConfigOption], key) match {
        case null =>
          new SystemConfigOption(key = key, value = value)
        case current:SystemConfigOption =>  {
          current.value = value
          current
        }
      }
      s.saveOrUpdate(co)
    })
  }
  def clearSystemConfigOption(key:String) = sessionFactory.withSession(s => {
    s.get(classOf[SystemConfigOption], key) match {
      case null =>
      case current:SystemConfigOption =>  s.delete(current)
    }
  })

  def systemConfigOptionOrDefault(key:String, defaultVal:String) = {
    maybeSystemConfigOption(key) match {
      case Some(s)   => s
      case None      => defaultVal
    }
  }

  private def deleteByDomain[T](s:Session, domain:String, queryName:String) =
    db.listQuery[T](queryName, Map("domain_name" -> domain)).foreach(s.delete(_))
}

/**
 * Indicates that the system not configured correctly
 */
class InvalidSystemConfigurationException(msg:String) extends RuntimeException(msg)
