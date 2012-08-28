package net.lshift.diffa.kernel

import config._
import config.Member
import config.system.JooqSystemConfigStore
import config.User
import differencing.JooqDomainDifferenceStore
import hooks.HookManager
import org.hibernate.dialect.Dialect
import org.slf4j.LoggerFactory
import preferences.JooqUserPreferencesStore
import scanning.JooqScanActivityStore
import util.cache.HazelcastCacheProvider
import util.sequence.HazelcastSequenceProvider
import util.MissingObjectException
import org.hibernate.SessionFactory
import net.lshift.diffa.schema.hibernate.SessionHelper.sessionFactoryToSessionHelper
import net.lshift.diffa.schema.cleaner.SchemaCleaner
import net.lshift.diffa.schema.environment.{DatabaseEnvironment, TestDatabaseEnvironments}
import net.lshift.diffa.schema.migrations.HibernateConfigStorePreparationStep
import collection.JavaConversions._
import com.jolbox.bonecp.BoneCPDataSource
import net.lshift.diffa.schema.jooq.DatabaseFacade
import scala.Some

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
  def facade: DatabaseFacade
  def dialect: Dialect
  def systemConfigStore: JooqSystemConfigStore
  def domainConfigStore: JooqDomainConfigStore
  def domainDifferenceStore: JooqDomainDifferenceStore
  def serviceLimitsStore: ServiceLimitsStore

  def prepareEnvironmentForStores: Unit

  def clearUserConfig {}
  
  def clearConfiguration(space: Long) {
    try {
      serviceLimitsStore.deletePairLimitsByDomain(space)
      domainDifferenceStore.removeDomain(space)
      serviceLimitsStore.deleteDomainLimits(space)
      systemConfigStore.deleteSpace(space)
    }  catch {
      case e: MissingObjectException => {
        logger.warn("Could not clear configuration for domain " + space)
      }
    }
  }

  def defaultDomain = "domain"
  def defaultUser = "guest"
  
  def tearDown: Unit
}

/**
 * A Store reference container that also implements initialisation of the associated environment.
 */
class LazyCleanStoreReferenceContainer(val applicationEnvironment: DatabaseEnvironment) extends StoreReferenceContainer {
  private val log = LoggerFactory.getLogger(getClass)
  
  private val applicationConfig = applicationEnvironment.getHibernateConfigurationWithoutMappingResources.
    setProperty("hibernate.generate_statistics", "true").
    setProperty("hibernate.connection.autocommit", "true")  // Turn this on to make the tests repeatable,
                                                            // otherwise the preparation step will not get committed
  val dialect = Dialect.getDialect(applicationConfig.getProperties)
  private var _sessionFactory: Option[SessionFactory] = None
  private var _ds: Option[BoneCPDataSource] = None

  def facade = new DatabaseFacade(ds, applicationEnvironment.jooqDialect)

  private val hookManager = new HookManager(applicationEnvironment.jooqDialect)
  private val membershipListener = new DomainMembershipAware {
    def onMembershipCreated(member: Member) {}
    def onMembershipRemoved(member: Member) {}
  }
  private def cacheProvider = new HazelcastCacheProvider
  private def sequenceProvider = new HazelcastSequenceProvider

  def sessionFactory = _sessionFactory.getOrElse {
    throw new IllegalStateException("Failed to initialize environment before using SessionFactory")
  }

  def ds = _ds.getOrElse {
    throw new IllegalStateException("Failed to initialize environment before using DataSource")
  }

  private lazy val jooqDatabaseFacade = new DatabaseFacade(ds, applicationEnvironment.jooqDialect)

  private def makeStore[T](consFn: SessionFactory => T, className: String): T = _sessionFactory match {
    case Some(sf) =>
      consFn(sf)
    case None =>
      throw new IllegalStateException("Failed to initialize environment before using " + className)
  }

  private lazy val _serviceLimitsStore =
    makeStore[ServiceLimitsStore](sf => new JooqServiceLimitsStore(jooqDatabaseFacade), "ServiceLimitsStore")

  private lazy val _domainConfigStore =
    makeStore(sf => new JooqDomainConfigStore(jooqDatabaseFacade, hookManager, cacheProvider, membershipListener), "domainConfigStore")

  private lazy val _systemConfigStore =
    makeStore(sf => {
      val store = new JooqSystemConfigStore(jooqDatabaseFacade, cacheProvider, sequenceProvider)
      store.registerDomainEventListener(_domainConfigStore)
      store
    }, "SystemConfigStore")

  private lazy val _domainCredentialsStore =
    makeStore(sf => new JooqDomainCredentialsStore(facade), "domainCredentialsStore")

  private lazy val _userPreferencesStore =
    makeStore(sf => new JooqUserPreferencesStore(facade, cacheProvider), "userPreferencesStore")

  private lazy val _domainDifferenceStore =
    makeStore(sf => new JooqDomainDifferenceStore(facade, cacheProvider, sequenceProvider, hookManager), "DomainDifferenceStore")

  private lazy val _scanActivityStore =
    makeStore(sf => new JooqScanActivityStore(facade), "scanActivityStore")

  def serviceLimitsStore: ServiceLimitsStore = _serviceLimitsStore
  def systemConfigStore: JooqSystemConfigStore = _systemConfigStore
  def domainConfigStore: JooqDomainConfigStore = _domainConfigStore
  def domainCredentialsStore: JooqDomainCredentialsStore = _domainCredentialsStore
  def domainDifferenceStore: JooqDomainDifferenceStore = _domainDifferenceStore
  def userPreferencesStore: JooqUserPreferencesStore = _userPreferencesStore
  def scanActivityStore: JooqScanActivityStore = _scanActivityStore

  def prepareEnvironmentForStores {
    performCleanerAction(cleaner => cleaner.clean)

    _sessionFactory = Some(applicationConfig.buildSessionFactory)
    _sessionFactory foreach { sf =>
      (new HibernateConfigStorePreparationStep).prepare(sf, applicationConfig)
      log.info("Schema created")
    }
    _ds = Some({
      val ds = new BoneCPDataSource()
      ds.setJdbcUrl(applicationEnvironment.url)
      ds.setUsername(applicationEnvironment.username)
      ds.setPassword(applicationEnvironment.password)
      ds.setDriverClass(applicationEnvironment.driver)
      ds
    })
  }

  def tearDown {
    log.debug("Dropping test schema")

    // This is a bit of a hack, but basically what is happening is that we are connecting to the DB as a DBA and
    // are nuking the user session on the server side, hence the client side data source (and Hibernate session factory)
    // will be toast

    try {
      _ds.get.close()
    } catch {
      case x => {
        log.warn("Could not close data source", x)
      }
    }

    try {
      _sessionFactory.get.close()
    } catch {
      case x => {
        log.warn("Could not close sessionFactory", x)
      }
    }

    _sessionFactory = None
    _ds = None

    try {
      performCleanerAction(cleaner => cleaner.drop)
    } catch {
      case _ =>
    }

  }

  private def performCleanerAction(action: SchemaCleaner => (DatabaseEnvironment, DatabaseEnvironment) => Unit) {
    val sysEnv = TestDatabaseEnvironments.adminEnvironment

    val dialect = Dialect.getDialect(sysEnv.getHibernateConfigurationWithoutMappingResources.getProperties)
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
    _sessionFactory.get.withSession(s =>
      s.createCriteria(classOf[User]).list.filterNot {
        case u: User => u.name == "guest"
      }.foreach(s.delete(_))
    )
  }
}
