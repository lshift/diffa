package net.lshift.diffa.kernel.config

import net.lshift.diffa.kernel.util.SessionHelper._
import org.hibernate.SessionFactory
import net.lshift.diffa.kernel.util.HibernateQueryUtils


class HibernateServiceLimitsStore(val sessionFactory: SessionFactory)
  extends ServiceLimitsStore
  with HibernateQueryUtils {

  private def validate(limitValue: Int) {
    if (limitValue < 0 && limitValue != ServiceLimit.UNLIMITED)
      throw new Exception("Invalid limit value")
  }

  def defineLimit(limitName: String, description: String) {
    createOrUpdate[ServiceLimitDefinitions](
      () => ServiceLimitDefinitions(limitName, description),
      () => limitName,
      old => {
        old.limitName = limitName
        old.limitDescription = description
      }
    )
  }

  def deleteDomainLimits(domainName: String) {
    deletePairLimitsByDomain(domainName)
    deleteLimitsByDomain[DomainServiceLimits](domainName, "domainServiceLimitsByDomain")
  }

  def deletePairLimitsByDomain(domainName: String) {
    deleteLimitsByDomain[PairServiceLimits](domainName, "pairServiceLimitsByDomain")
  }

  private def deleteLimitsByDomain[T](domainName: String, queryName: String) {
    sessionFactory.withSession(session => {
      listQuery[T](
        session, queryName, Map("domain_name" -> domainName)
      ).foreach(
        session.delete
      )
    })
  }

  def setSystemHardLimit(limitName: String, limitValue: Int) {
    validate(limitValue)
    setSystemLimit(limitName, limitValue, old => old.hardLimit = limitValue)

    cascadeLimitToSystemDefault(limitName, limitValue)
    cascadeLimitToDomainHardLimit(limitName, limitValue)
  }

  def setDomainHardLimit(domainName: String, limitName: String, limitValue: Int) {
    validate(limitValue)
    setDomainLimit(domainName, limitName, limitValue, old => old.hardLimit = limitValue)

    cascadeLimitToDomainDefaultLimit(limitName, limitValue)
    cascadeLimitToPair(limitName, limitValue)
  }

  def setSystemDefaultLimit(limitName: String, limitValue: Int) {
    setSystemLimit(limitName, limitValue, old => old.defaultLimit = limitValue)
  }

  def setDomainDefaultLimit(domainName: String, limitName: String, limitValue: Int) {
    setDomainLimit(domainName, limitName, limitValue, old => old.defaultLimit = limitValue)
  }

  def setPairLimit(domainName: String, pairKey: String, limitName: String, limitValue: Int) {
    val domain = getDomain(domainName)
    val pair = DiffaPair(key = pairKey, domain = domain)

    createOrUpdate[PairServiceLimits](
      () => PairServiceLimits(domain, pair, limitName, limitValue),
      () => PairScopedLimit(limitName, pair),
      old => old.limitValue = limitValue
    )
  }

  def getSystemHardLimitForName(limitName: String) =
    getLimit("systemHardLimitByName", Map("limit_name" -> limitName))


  def getSystemDefaultLimitForName(limitName: String) =
    getLimit("systemDefaultLimitByName", Map("limit_name" -> limitName))

  def getDomainHardLimitForDomainAndName(domainName: String, limitName: String) =
    getLimit("domainHardLimitByDomainAndName", Map("limit_name" -> limitName, "domain_name" -> domainName))

  def getDomainDefaultLimitForDomainAndName(domainName: String, limitName: String) =
    getLimit("domainDefaultLimitByDomainAndName",Map("limit_name" -> limitName, "domain_name" -> domainName))

  def getPairLimitForPairAndName(domainName: String, pairKey: String, limitName: String) =
    getLimit("pairLimitByPairAndName", Map("limit_name" -> limitName, "domain_name" -> domainName, "pair_key" -> pairKey))

  private def getLimit(queryName: String, params: Map[String, String]) = sessionFactory.withSession(session =>
    singleQueryOpt[Int](
      session, queryName, params
    ))

  private def cascadeLimit(currentLimit: Int, setLimitValue: (String, Int) => Unit,
                           limitName: String, newLimit: Int) {
    if (currentLimit > newLimit) {
      setLimitValue(limitName, newLimit)
    }
  }

  private def cascadeLimitToSystemDefault(limitName: String, limitValue: Int) {
    cascadeLimit(
      getEffectiveLimitByName(limitName),
      setSystemDefaultLimit,
      limitName, limitValue)
  }
  
  private def cascadeLimitToDomainHardLimit(limitName: String, limitValue: Int) {
    sessionFactory.withSession(
      session => listQuery[DomainServiceLimits](session, "domainServiceLimitsByName", Map("limit_name" -> limitName))
    ).foreach { domainLimit =>
      cascadeLimit(
        domainLimit.hardLimit,
        (limitName, limitValue) => setDomainHardLimit(domainLimit.domain.name, limitName, limitValue),
        limitName, limitValue
      )
    }
  }

  private def cascadeLimitToDomainDefaultLimit(limitName: String, limitValue: Int) {
    sessionFactory.withSession(
      session => listQuery[DomainServiceLimits](session, "domainServiceLimitsByName", Map("limit_name" -> limitName))
    ).foreach { domainLimit =>
      cascadeLimit(
        domainLimit.defaultLimit,
        (limitName, limitValue) => setDomainDefaultLimit(domainLimit.domain.name, limitName, limitValue),
        limitName, limitValue
      )
    }
  }
  
  private def cascadeLimitToPair(limitName: String, limitValue: Int) {
    sessionFactory.withSession(
      session => listQuery[PairServiceLimits](session, "pairServiceLimitsByName", Map("limit_name" -> limitName))
    ).foreach { pairLimit =>
      cascadeLimit(
        pairLimit.limitValue,
        (limitName, limitValue) => setPairLimit(pairLimit.pair.domain.name, pairLimit.pair.key, limitName, limitValue),
        limitName, limitValue
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

  private def setSystemLimit(limitName: String, limitValue: Int, updateLimit: SystemServiceLimits => Unit) {
    createOrUpdate[SystemServiceLimits](
      () => SystemServiceLimits(limitName, limitValue, limitValue),
      () => limitName,
      updateLimit
    )
  }

  private def setDomainLimit(domainName: String, limitName: String, limitValue: Int, updateLimit: DomainServiceLimits => Unit) {
    val domain = getDomain(domainName)
    createOrUpdate[DomainServiceLimits](
      () => DomainServiceLimits(domain, limitName, limitValue, limitValue),
      () => DomainScopedLimit(limitName, domain),
      updateLimit
    )
  }
}
