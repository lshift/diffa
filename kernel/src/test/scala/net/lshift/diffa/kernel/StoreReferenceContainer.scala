package net.lshift.diffa.kernel

import config.system.HibernateSystemConfigStore
import config.{TestDatabaseEnvironments, PairCache, HibernateConfigStorePreparationStep, HibernateDomainConfigStore}
import differencing.HibernateDomainDifferenceStore
import hooks.HookManager
import org.hibernate.dialect.Dialect
import net.sf.ehcache.CacheManager
import org.slf4j.LoggerFactory
import util.{MissingObjectException, SchemaCleaner, DatabaseEnvironment}
import org.hibernate.SessionFactory
import net.lshift.diffa.kernel.util.SessionHelper.sessionFactoryToSessionHelper
import net.lshift.diffa.kernel.config.User
import collection.JavaConversions._

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
  def sessionFactory: SessionFactory
  def dialect: Dialect
  def systemConfigStore: HibernateSystemConfigStore
  def domainConfigStore: HibernateDomainConfigStore
  def domainDifferenceStore: HibernateDomainDifferenceStore

  def prepareEnvironmentForStores: Unit

  def clearUserConfig {}
  
  def clearConfiguration(domainName: String = defaultDomain) {
    try {
      domainDifferenceStore.removeDomain(domainName)
      systemConfigStore.deleteDomain(domainName)
    }  catch {
      case e: MissingObjectException =>
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

  def sessionFactory = _sessionFactory match {
    case Some(sf) => sf
    case None => throw new IllegalStateException("Failed to initialize environment before using SessionFactory")
  }

  private lazy val _systemConfigStore = _sessionFactory match {
    case Some(sf) =>
      new HibernateSystemConfigStore(sf, pairCache)
    case None =>
      throw new IllegalStateException("Failed to initialize environment before using SystemConfigStore")
  }

  private lazy val _domainConfigStore = _sessionFactory match {
    case Some(sf) =>
      new HibernateDomainConfigStore(sf, pairCache, hookManager)
    case None =>
      throw new IllegalStateException("Failed to initialize environment before using DomainConfigStore")
  }

  private lazy val _domainDifferenceStore = _sessionFactory match {
    case Some(sf) =>
      new HibernateDomainDifferenceStore(sf, cacheManager, dialect, hookManager)
    case None =>
      throw new IllegalStateException("Failed to initialize environment before using DomainDifferenceStore")
  }

  def systemConfigStore: HibernateSystemConfigStore = _systemConfigStore
  def domainConfigStore: HibernateDomainConfigStore = _domainConfigStore
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
