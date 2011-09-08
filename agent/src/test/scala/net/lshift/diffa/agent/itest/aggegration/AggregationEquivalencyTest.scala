package net.lshift.diffa.kernel.differencing

import org.junit.Assert._
import org.hibernate.cfg.Configuration
import net.sf.ehcache.CacheManager
import org.hibernate.SessionFactory
import scala.collection.JavaConversions._
import org.hibernate.transform.Transformers
import java.sql.Timestamp
import net.lshift.diffa.kernel.config.system.HibernateSystemConfigStore
import org.junit.{Before, Test}
import net.lshift.diffa.kernel.config.{Domain, HibernateDomainConfigStore, DiffaPairRef}
import net.lshift.diffa.kernel.events.VersionID
import com.eaio.uuid.UUID
import net.lshift.diffa.kernel.frontend.{EndpointDef, PairDef}
import org.joda.time.{Interval, DateTime}

/**
 * This is an integration test that is designed compare cursor based time range aggregation and getting the
 * database to perform the aggregation. It performs the following tasks:
 *
 * - Generates a fixed set of reportable difference events
 * - Run a baseline aggregation over this data
 * - Benchmark that the baseline
 * - Run DB based aggregations for different supported DBs (currently only Oracle is supported).
 *
 */
class AggregationEquivalencyTest {

  private val url = "jdbc:oracle:thin:@diffadevdb01......change.me."
  private val password = "diffa"
  private val username = "DIFFA_OWNER"
  private val driver = "oracle.jdbc.driver.OracleDriver"

  private val config =
        new Configuration().
          addResource("net/lshift/diffa/kernel/config/Config.hbm.xml").
          addResource("net/lshift/diffa/kernel/differencing/DifferenceEvents.hbm.xml").
          setProperty("hibernate.dialect", "org.hibernate.dialect.Oracle10gDialect").
          setProperty("hibernate.connection.url", url).
          setProperty("hibernate.connection.username", username).
          setProperty("hibernate.connection.password", password).
          setProperty("hibernate.connection.driver_class", driver).
          setProperty("hibernate.cache.region.factory_class", "net.sf.ehcache.hibernate.EhCacheRegionFactory").
          setProperty("hibernate.connection.autocommit", "true") // Turn this on to make the tests repeatable,
                                                                 // otherwise the preparation step will not get committed

  val cacheManager = new CacheManager()
  val sf:SessionFactory = config.buildSessionFactory
  val diffStore = new HibernateDomainDifferenceStore(sf, cacheManager)
  val systemConfigStore = new HibernateSystemConfigStore(sf)
  val domainConfigStore = new HibernateDomainConfigStore(sf)

  val domainName = "diffa"
  val pairKey = "aggregation-test-pair"
  val pairRef = DiffaPairRef(pairKey, domainName)

  val up = EndpointDef(name = "up", contentType = "a", scanUrl  = "u")
  val down = EndpointDef(name = "down", contentType = "a", scanUrl  = "d")

  val pair = PairDef(key = pairKey, upstreamName = "up", downstreamName = "down")
  val domain = Domain(name = domainName)

  val lowerBound = new DateTime(2009,5,5,17,14,55,742)

  val expectedEvents = 10
  val offsetPerEvent  = 1 // Every 5 minutes
  val interval = new Interval(lowerBound, lowerBound.plusMinutes(expectedEvents * offsetPerEvent))

  /**
   * Run the baseline before running any DB specific tests
   */
  @Before
  def createBaseline = {

    systemConfigStore.createOrUpdateDomain(domain)
    domainConfigStore.createOrUpdateEndpoint(domainName, up)
    domainConfigStore.createOrUpdateEndpoint(domainName, down)
    diffStore.removePair(pairRef)
    domainConfigStore.deletePair(domainName, pairKey)
    domainConfigStore.createOrUpdatePair(domainName, pair)

    for (i <- 0 until expectedEvents) {
      val id = VersionID(pairRef, new UUID().toString)
      val detectionTime = lowerBound.plusMinutes(i * offsetPerEvent)
      diffStore.addReportableUnmatchedEvent(id, detectionTime, new UUID().toString, new UUID().toString, detectionTime)
      if (i % 100 == 0) {
        println("Created %sth reportable event".format(i))
      }
    }

    var count = 0
    val start = System.currentTimeMillis()

    diffStore.retrieveUnmatchedEvents(pairRef, interval, (e:ReportedDifferenceEvent) => {
      count += 1
    })
    val stop = System.currentTimeMillis()

    println("Baseline: %s results -> %s".format(count,  (stop - start) ))
    assertEquals(expectedEvents, count)
  }

  @Test
  def dbAggregationShouldProduceSameResult = {

    val lower = convertToTimestamp(interval.getStart)
    val upper = convertToTimestamp(interval.getEnd)

    val s = sf.openSession
    val query = s.getNamedQuery("aggregatedDiffs")

    val start = System.currentTimeMillis()

    val result = query.
                 setParameter("domain", domainName).
                 setParameter("pair", pairKey).
                 setParameter("lowerBound", lower).
                 setParameter("upperBound", upper).
                 setResultTransformer(Transformers.aliasToBean(classOf[DifferenceAggregate])).
                 list()

    val stop = System.currentTimeMillis()
    var count = 0

    result.foreach(count += _.asInstanceOf[DifferenceAggregate].COUNT.toBigInteger.intValue())

    println("SP (%s -> %s): %s results -> %s".format(lower, upper, count, (stop - start) ))

    s.close()
    assertEquals(expectedEvents, count)
  }

  /**
   * Really shouldn't need this function .....
   */
  private def convertToTimestamp(d:DateTime) = {
    val year = d.getYear - 1900
    val month = d.getMonthOfYear - 1
    val day = d.getDayOfMonth
    val hour = d.getHourOfDay - 1 // This is because off the timezone :-( really need to get to the bottom of this
    val minutes = d.getMinuteOfHour
    val seconds = d.getSecondOfMinute
    val nanos = d.getMillisOfSecond * 10 ^ 6
    new Timestamp(year,month,day,hour,minutes,seconds,nanos)
  }

}