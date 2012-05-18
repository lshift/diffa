package net.lshift.diffa.kernel.config

import limits.Unlimited
import net.lshift.diffa.kernel.util.db.{HibernateQueryUtils, DatabaseFacade}
import net.lshift.diffa.kernel.util.db.SessionHelper._
import org.hibernate.SessionFactory


class HibernateServiceLimitsStore(val sessionFactory: SessionFactory,
                                  db:DatabaseFacade)
  extends ServiceLimitsStore
  with HibernateQueryUtils {

  private def validate(limitValue: Int) {
    if (limitValue < 0 && limitValue != Unlimited.hardLimit)
      throw new Exception("Invalid limit value")
  }

  def defineLimit(limit: ServiceLimit) {
    createOrUpdate[ServiceLimitDefinitions](
      () => ServiceLimitDefinitions(limit.key, limit.description),
      () => limit.key,
      old => {
        old.limitName = limit.key
        old.limitDescription = limit.description
      }
    )
  }

  def deleteDomainLimits(domain: String) {
    deletePairLimitsByDomain(domain)
    db.execute("deleteDomainServiceLimitsByDomain", Map("domain" -> domain))
  }

  def deletePairLimitsByDomain(domain: String) {
    db.execute("deletePairServiceLimitsByDomain", Map("domain" -> domain))
  }

  def setSystemHardLimit(limit: ServiceLimit, limitValue: Int) {
    validate(limitValue)
    setSystemLimit(limit, limitValue, old => old.hardLimit = limitValue)

    cascadeLimitToSystemDefault(limit, limitValue)
    cascadeLimitToDomainHardLimit(limit, limitValue)
  }

  def setDomainHardLimit(domainName: String, limit: ServiceLimit, limitValue: Int) {
    validate(limitValue)
    setDomainLimit(domainName, limit, limitValue, old => old.hardLimit = limitValue)

    cascadeLimitToDomainDefaultLimit(limit, limitValue)
    cascadeLimitToPair(limit, limitValue)
  }

  def setSystemDefaultLimit(limit: ServiceLimit, limitValue: Int) {
    setSystemLimit(limit, limitValue, old => old.defaultLimit = limitValue)
  }

  def setDomainDefaultLimit(domainName: String, limit: ServiceLimit, limitValue: Int) {
    setDomainLimit(domainName, limit, limitValue, old => old.defaultLimit = limitValue)
  }

  def setPairLimit(domainName: String, pairKey: String, limit: ServiceLimit, limitValue: Int) {
    val domain = getDomain(domainName)
    val pair = DiffaPair(key = pairKey, domain = domain)

    createOrUpdate[PairServiceLimits](
      () => PairServiceLimits(domain, pair, limit.key, limitValue),
      () => PairScopedLimit(limit.key, pair),
      old => old.limitValue = limitValue
    )
  }

  def getSystemHardLimitForName(limit: ServiceLimit) =
    getLimit("systemHardLimitByName", Map("limit_name" -> limit.key))


  def getSystemDefaultLimitForName(limit: ServiceLimit) =
    getLimit("systemDefaultLimitByName", Map("limit_name" -> limit.key))

  def getDomainHardLimitForDomainAndName(domainName: String, limit: ServiceLimit) =
    getLimit("domainHardLimitByDomainAndName", Map("limit_name" -> limit.key, "domain_name" -> domainName))

  def getDomainDefaultLimitForDomainAndName(domainName: String, limit: ServiceLimit) =
    getLimit("domainDefaultLimitByDomainAndName",Map("limit_name" -> limit.key, "domain_name" -> domainName))

  def getPairLimitForPairAndName(domainName: String, pairKey: String, limit: ServiceLimit) =
    getLimit("pairLimitByPairAndName", Map("limit_name" -> limit.key, "domain_name" -> domainName, "pair_key" -> pairKey))

  private def getLimit(queryName: String, params: Map[String, String]) = sessionFactory.withSession(session =>
    singleQueryOpt[Int](
      session, queryName, params
    ))

  private def cascadeLimit(currentLimit: Int, setLimitValue: (ServiceLimit, Int) => Unit,
                           limit: ServiceLimit, newLimit: Int) {
    if (currentLimit > newLimit) {
      setLimitValue(limit, newLimit)
    }
  }

  private def cascadeLimitToSystemDefault(limit: ServiceLimit, limitValue: Int) {
    cascadeLimit(
      getEffectiveLimitByName(limit),
      setSystemDefaultLimit,
      limit, limitValue)
  }
  
  private def cascadeLimitToDomainHardLimit(limit: ServiceLimit, limitValue: Int) {
    val limits = db.listQuery[DomainServiceLimits]("domainServiceLimitsByName", Map("limit_name" -> limit.key))
    limits.foreach { domainLimit =>
      cascadeLimit(
        domainLimit.hardLimit,
        (limitName, limitValue) => setDomainHardLimit(domainLimit.domain.name, limit, limitValue),
        limit, limitValue
      )
    }
  }

  private def cascadeLimitToDomainDefaultLimit(limit: ServiceLimit, limitValue: Int) {
    val limits = db.listQuery[DomainServiceLimits]("domainServiceLimitsByName", Map("limit_name" -> limit.key))
    limits.foreach { domainLimit =>
      cascadeLimit(
        domainLimit.defaultLimit,
        (limitName, limitValue) => setDomainDefaultLimit(domainLimit.domain.name, limit, limitValue),
        limit, limitValue
      )
    }
  }
  
  private def cascadeLimitToPair(limit: ServiceLimit, limitValue: Int) {
    val limits = db.listQuery[PairServiceLimits]("pairServiceLimitsByName", Map("limit_name" -> limit.key))
    limits.foreach { pairLimit =>
      cascadeLimit(
        pairLimit.limitValue,
        (limitName, limitValue) => setPairLimit(pairLimit.pair.domain.name, pairLimit.pair.key, limit, limitValue),
        limit, limitValue
      )
    }
  }

  private def createOrUpdate[T: ClassManifest](createLimit: () => T, searchKey: () => java.io.Serializable, updateLimit: T => Unit) {
    sessionFactory.withSession(session => {
      val lim = session.get(classManifest[T].erasure, searchKey()) match {
        case null =>
          createLimit()
        case old: T => {
          updateLimit(old)
          old
        }
      }
      session.saveOrUpdate(lim)
    })
  }

  private def setSystemLimit(limit: ServiceLimit, limitValue: Int, updateLimit: SystemServiceLimits => Unit) {
    createOrUpdate[SystemServiceLimits](
      () => SystemServiceLimits(limit.key, limitValue, limitValue),
      () => limit.key,
      updateLimit
    )
  }

  private def setDomainLimit(domainName: String, limit: ServiceLimit, limitValue: Int, updateLimit: DomainServiceLimits => Unit) {
    val domain = getDomain(domainName)
    createOrUpdate[DomainServiceLimits](
      () => DomainServiceLimits(domain, limit.key, limitValue, limitValue),
      () => DomainScopedLimit(limit.key, domain),
      updateLimit
    )
  }
}
