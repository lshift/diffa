package net.lshift.diffa.kernel

import config._
import config.system.HibernateSystemConfigStore
import differencing.HibernateDomainDifferenceStore
import hooks.HookManager
import org.hibernate.dialect.Dialect
import net.sf.ehcache.CacheManager
import org.slf4j.LoggerFactory
import util.cache.HazelcastCacheProvider
import util.db.HibernateDatabaseFacade
import util.sequence.HazelcastSequenceProvider
import util.{MissingObjectException, SchemaCleaner, DatabaseEnvironment}
import org.hibernate.SessionFactory
import net.lshift.diffa.kernel.util.db.SessionHelper.sessionFactoryToSessionHelper
import collection.JavaConversions._
import com.jolbox.bonecp.BoneCPDataSource

object StoreReferenceContainer {
  def withCleanDatabaseEnvironment(env: DatabaseEnvironment) = {
    val stores = new LazyCleanStoreReferenceContainer(env)
    stores.prepareEnvironmentForStores
    stores
  }
}

/**
 * Maintains references to Store objects used in testing and provides an
 * interface for simple initialisation of their configuration.
 */
trait StoreReferenceContainer {

  private val logger = LoggerFactory.getLogger(getClass)

  def sessionFactory: SessionFactory
  def dialect: Dialect
  def systemConfigStore: HibernateSystemConfigStore
  def domainConfigStore: HibernateDomainConfigStore
  def domainDifferenceStore: HibernateDomainDifferenceStore
  def serviceLimitsStore: ServiceLimitsStore

  def prepareEnvironmentForStores: Unit

  def clearUserConfig {}
  
  def clearConfiguration(domainName: String = defaultDomain) {
    try {
      serviceLimitsStore.deletePairLimitsByDomain(domainName)
      domainDifferenceStore.removeDomain(domainName)
      serviceLimitsStore.deleteDomainLimits(domainName)
      systemConfigStore.deleteDomain(domainName)
    }  catch {
      case e: MissingObjectException => {
        logger.warn("Could not clear configuration for domain " + domainName, e)
      }
    }
  }

  def defaultDomain = "domain"
  
  def tearDown: Unit
}

/**
 * A Store reference container that also implements initialisation of the associated environment.
 */
class LazyCleanStoreReferenceContainer(val applicationEnvironment: DatabaseEnvironment) extends StoreReferenceContainer {
  private val log = LoggerFactory.getLogger(getClass)
  
  private val applicationConfig = applicationEnvironment.getHibernateConfiguration.
    setProperty("hibernate.generate_statistics", "true").
    setProperty("hibernate.connection.autocommit", "true")  // Turn this on to make the tests repeatable,
                                                            // otherwise the preparation step will not get committed
  val dialect = Dialect.getDialect(applicationConfig.getProperties)
  private var _sessionFactory: Option[SessionFactory] = None

  private val cacheManager = new CacheManager
  private val pairCache = new PairCache(cacheManager)
  private val hookManager = new HookManager(applicationConfig)
  private val membershipListener = new DomainMembershipAware {
    def onMembershipCreated(member: Member) {}
    def onMembershipRemoved(member: Member) {}
  }
  private def cacheProvider = new HazelcastCacheProvider
  private def sequenceProvider = new HazelcastSequenceProvider

  def sessionFactory = _sessionFactory match {
    case Some(sf) => sf
    case None => throw new IllegalStateException("Failed to initialize environment before using SessionFactory")
  }

  private def makeStore[T](consFn: SessionFactory => T, className: String): T = _sessionFactory match {
    case Some(sf) =>
      consFn(sf)
    case None =>
      throw new IllegalStateException("Failed to initialize environment before using " + className)
  }

  private val ds = new BoneCPDataSource()
  ds.setJdbcUrl(applicationEnvironment.url)
  ds.setUsername(applicationEnvironment.username)
  ds.setPassword(applicationEnvironment.password)
  ds.setDriverClass(applicationEnvironment.driver)

  private lazy val _serviceLimitsStore =
    makeStore[ServiceLimitsStore](sf => new HibernateServiceLimitsStore(sf, new HibernateDatabaseFacade(sf,ds)), "ServiceLimitsStore")

  private lazy val _systemConfigStore =
    makeStore(sf => new HibernateSystemConfigStore(sf, new HibernateDatabaseFacade(sf,ds), pairCache), "SystemConfigStore")

  private lazy val _domainConfigStore =
    makeStore(sf => new HibernateDomainConfigStore(sf, new HibernateDatabaseFacade(sf,ds), pairCache, hookManager, cacheManager, membershipListener), "domainConfigStore")

  private lazy val _domainCredentialsStore =
    makeStore(sf => new HibernateDomainCredentialsStore(sf, new HibernateDatabaseFacade(sf,ds)), "domainCredentialsStore")

  private lazy val _domainDifferenceStore =
    makeStore(sf => new HibernateDomainDifferenceStore(sf, new HibernateDatabaseFacade(sf,ds), cacheProvider, sequenceProvider, cacheManager, dialect, hookManager), "DomainDifferenceStore")

  def serviceLimitsStore: ServiceLimitsStore = _serviceLimitsStore
  def systemConfigStore: HibernateSystemConfigStore = _systemConfigStore
  def domainConfigStore: HibernateDomainConfigStore = _domainConfigStore
  def domainCredentialsStore: HibernateDomainCredentialsStore = _domainCredentialsStore
  def domainDifferenceStore: HibernateDomainDifferenceStore = _domainDifferenceStore

  def prepareEnvironmentForStores {
    performCleanerAction(cleaner => cleaner.clean)

    _sessionFactory = Some(applicationConfig.buildSessionFactory)
    _sessionFactory foreach { sf =>
      (new HibernateConfigStorePreparationStep).prepare(sf, applicationConfig)
      log.info("Schema created")
    }
  }

  def tearDown {
    log.debug("Dropping test schema")
    try {
      performCleanerAction(cleaner => cleaner.drop)
    } catch {
      case _ =>
    }
    _sessionFactory.get.close()
    _sessionFactory = None
  }

  private def performCleanerAction(action: SchemaCleaner => (DatabaseEnvironment, DatabaseEnvironment) => Unit) {
    val sysEnv = TestDatabaseEnvironments.adminEnvironment

    val dialect = Dialect.getDialect(sysEnv.getHibernateConfiguration.getProperties)
    val cleaner = SchemaCleaner.forDialect(dialect)
    try {
      action(cleaner)(sysEnv, applicationEnvironment)
    } catch {
      case ex: Exception =>
        log.info("Failed to clean schema %s".format(applicationEnvironment.username))
        throw ex
    }
  }

  override def clearUserConfig {
    _sessionFactory.get.withSession(s => s.createCriteria(classOf[User]).list.foreach(s.delete(_)))
  }

  def sessionStatistics = _sessionFactory.get.getStatistics
}
