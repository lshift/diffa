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

package net.lshift.diffa.kernel.util

import net.lshift.diffa.kernel.util.SessionHelper._
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.kernel.config.{Pair => DiffaPair}
import org.slf4j.{LoggerFactory, Logger}
import scala.collection.JavaConversions._
import scala.collection.Map
import org.hibernate._
import java.io.Closeable
import net.lshift.diffa.kernel.differencing.StoreCheckpoint

/**
 * Mixin providing a bunch of useful query utilities for stores.
 */
trait HibernateQueryUtils {
  def sessionFactory:SessionFactory

  protected val log:Logger = LoggerFactory.getLogger(getClass)

  def getOrFail[ReturnType](s: Session, c:Class[ReturnType], id:java.io.Serializable, entityName:String):ReturnType = {
    s.get(c, id) match {
      case null => throw new MissingObjectException(entityName)
      case e    => e.asInstanceOf[ReturnType]
    }
  }

  /**
   * Executes a list query in the given session, forcing the result type into a typed list of the given
   * return type.
   */
  def listQuery[ReturnType](s: Session, queryName: String, params: Map[String, Any], firstResult:Option[Int] = None, maxResults:Option[Int] = None): Seq[ReturnType] = {
    sessionFactory.withSession(s => {
      val query: Query = s.getNamedQuery(queryName)
      params foreach {case (param, value) => query.setParameter(param, value)}
      firstResult.map(f => query.setFirstResult(f))
      maxResults.map(m => query.setMaxResults(m))
      query.list map (item => item.asInstanceOf[ReturnType])
    })
  }

  /**
   * Returns a handle to a scrollable cursor. Note that this requires the caller to manage the session for themselves.
   */
  def scrollableQuery[T](s: Session, queryName: String, params: Map[String, Any]) : Cursor[T] = {
    val query: Query = s.getNamedQuery(queryName)
    params foreach {case (param, value) => query.setParameter(param, value)}

    val underlying = query.scroll()

    new Cursor[T] {
      def next = underlying.next
      def get = underlying.get(0).asInstanceOf[T]
      def close = underlying.close
    }
  }

  /**
   * Apply the closure to the query result set in a streaming fashion.
   */
  def processAsStream[T](queryName:String, params: Map[String, Any], f:(Session, T) => Unit) = {

    val session = sessionFactory.openSession
    var cursor:Cursor[T] = null

    try {
      cursor = scrollableQuery[T](session, queryName, params)

      var count = 0
      while(cursor.next) {

        f(session, cursor.get)

        count += 1
        if ( count % 100 == 0 ) {
          // Periodically tell hibernate to let go of any objects it may still be referencing
          session.flush()
          session.clear()
        }
      }
    }
    finally {
      try {
        cursor.close
      } finally {
        session.flush()
        session.close()
      }

    }
  }

  /**
   * Executes a query that is expected to return a single result in the given session. Throws an exception if the
   * requested object is not available.
   */
  def singleQuery[ReturnType](s: Session, queryName: String, params: Map[String, Any], entityName: String): ReturnType = {
    singleQueryOpt(s, queryName, params) match {
      case None => throw new MissingObjectException(entityName)
      case Some(x) => x
    }
  }

  /**
   * Executes a query that may return a single result in the current session. Returns either None or Some(x) for the
   * object.
   */
  def singleQueryOpt[ReturnType](s:Session, queryName: String, params: Map[String, Any]): Option[ReturnType] =
    singleQueryOptBuilder(s,queryName, params, (_) => ())

  /**
   * Executes a query that may return a single result in the current session. Returns either None or Some(x) for the
   * object.
   *
   * The difference to singleQueryOpt/3 is that the underlying SQL query may, from the DB's perspective return
   * more than one result, so to counter this, the max result set size is limited to one to guarantee only one result.
   */
  def limitedSingleQueryOpt[ReturnType](s:Session, queryName: String, params: Map[String, Any]): Option[ReturnType] =
    singleQueryOptBuilder(s,queryName, params, (q:Query) => q.setMaxResults(1))

  private def singleQueryOptBuilder[ReturnType](s:Session, queryName: String, params: Map[String, Any], f:Query => Unit): Option[ReturnType] = {

    val query: Query = s.getNamedQuery(queryName)
    params foreach {case (param, value) => query.setParameter(param, value)}

    f(query)

    try {
      query.uniqueResult match {
        case null => None
        case r: ReturnType => Some(r)
      }
    }
    catch {
      case e: NonUniqueResultException => {
        log.warn("Non unique result for: " + queryName)
        params.foreach(p => log.debug("Key: [" + p._1 + "], Value: [" + p._2 + "]" ))
        log.debug("Logging stack trace for non unique result", e)
        None
      }
    }
  }

  def executeUpdate(s:Session, queryName: String, params: Map[String, Any]) = {
    val query: Query = s.getNamedQuery(queryName)
    params foreach {case (param, value) => query.setParameter(param, value)}
    query.executeUpdate()
  }

  /**
   * Returns a domain by its name
   */
  def getDomain(name: String) = sessionFactory.withSession(s => {
    singleQuery[Domain](s, "domainByName", Map("domain_name" -> name), "domain %s".format(name))
  })

  def removeDomainDifferences(domain:String) = sessionFactory.withSession(s => {
    // TODO Maybe this should be integrated with HibernateSystemConfigStore:deleteDomain/1
    executeUpdate(s, "removeDomainCheckpoints", Map("domain_name" -> domain))
    executeUpdate(s, "removeDomainDiffs", Map("domain" -> domain))
    executeUpdate(s, "removeDomainPendingDiffs", Map("domain" -> domain))
  })

  /**
   * This is un-protected call to set a configuration option.
   * It is up to the calling context to establish this is authorized.
   */
  def writeConfigOption(domainName:String, key:String, value:String) = sessionFactory.withSession(s => {
    val domain = getDomain(domainName)
    val scopedKey = DomainScopedKey(key, domain)
    val co = s.get(classOf[ConfigOption], scopedKey) match {
      case null =>
        new ConfigOption(domain = domain, key = key, value = value)
      case current:ConfigOption =>  {
        current.value = value
        current
      }
    }
    s.saveOrUpdate(co)
  })

  /**
   * This is un-protected call to clear a configuration option.
   * It is up to the calling context to establish this is authorized.
   */
  def deleteConfigOption(domain:String, key:String) = sessionFactory.withSession(s => {
    val scopedKey = DomainScopedKey(key, getDomain(domain))
    s.get(classOf[ConfigOption], scopedKey) match {
      case null =>
      case current:ConfigOption =>  s.delete(current)
    }
  })

  def getStoreCheckpoint(pair:DiffaPairRef) = sessionFactory.withSession(s => {
    singleQueryOpt[StoreCheckpoint](s, "storeCheckpointByPairAndDomain",
      Map("pair_key" -> pair.key, "domain_name" -> pair.domain))
  })

  def deleteStoreCheckpoint(pair:DiffaPairRef) = sessionFactory.withSession(s => {
    getStoreCheckpoint(pair) match {
      case Some(x) => s.delete(x)
      case None    => // nothing to do
    }
  })

  def getEndpoint(s: Session, domain:String, name: String) = getOrFail(s, classOf[Endpoint], DomainScopedName(name, Domain(name = domain)), "endpoint")

  def getUser(s: Session, name: String) = singleQuery[User](s, "userByName", Map("name" -> name), "user %s".format(name))

  def getPair(s: Session, domain:String, key: String) = getOrFail(s, classOf[Pair], DomainScopedKey(key, Domain(name = domain)), "pair")

  def getRepairAction(s: Session, domain:String, name: String, pairKey: String) =
    singleQuery[RepairAction](s, "repairActionsByNameAndPair",
                              Map("name" -> name, "pair_key" -> pairKey, "domain_name" -> domain),
                              "repair action %s for pair %s in domain %s".format(name, pairKey, domain))

  def getEscalation(s: Session, domain:String, name: String, pairKey: String) =
    singleQuery[Escalation](s, "escalationsByNameAndPair",
                            Map("name" -> name, "pair_key" -> pairKey, "domain_name" -> domain),
                            "esclation %s for pair %s in domain %s".format(name, pairKey, domain))

  def getReport(s: Session, domain:String, name: String, pairKey: String) =
    singleQuery[PairReport](s, "reportsByNameAndPair",
                            Map("name" -> name, "pair_key" -> pairKey, "domain_name" -> domain),
                            "report %s for pair %s in domain %s".format(name, pairKey, domain))

  def listPairsInDomain(domain:String) = sessionFactory.withSession(s => listQuery[DiffaPair](s, "pairsByDomain", Map("domain_name" -> domain)))

}

/**
 * A simple wrapper around a DB cursor
 */
trait Cursor[T] extends Closeable {

  /**
   * Returns the bound value of the current cursor point.
   */
  def get : T

  /**
   * Moves the cursor along and if the end of the result has not been reached
   */
  def next : Boolean
}