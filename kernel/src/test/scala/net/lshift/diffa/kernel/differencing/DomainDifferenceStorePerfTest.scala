package net.lshift.diffa.kernel.differencing

import org.junit.Assume._
import org.junit.Assert._
import org.hamcrest.CoreMatchers._
import net.lshift.diffa.kernel.events.VersionID
import org.hibernate.cfg.Configuration
import net.lshift.diffa.kernel.config.{HibernateConfigStorePreparationStep, DiffaPairRef}
import org.apache.commons.io.FileUtils
import java.io.File
import org.joda.time.{Interval, DateTime}
import org.junit.{Ignore, Test}
import net.sf.ehcache.CacheManager
import net.lshift.diffa.kernel.util.DatabaseEnvironment
import org.hibernate.dialect.Dialect
import net.lshift.diffa.kernel.hooks.HookManager

/**
 * Performance test for the domain cache.
 */
class DomainDifferenceStorePerfTest {
  assumeThat(System.getProperty("diffa.perftest"), is(equalTo("1")))

  import DomainDifferenceStorePerfTest._

  @Test
  def differenceInsertionShouldBeConstantTime() {
    val pair = DiffaPairRef(key = "pair", domain = "domain")

    runPerformanceTest(4) { count =>
      linearCost(count)(() => {
        for (j <- 0L until count) {
          diffStore.addReportableUnmatchedEvent(VersionID(pair, "id" + j), new DateTime, "uV", "dV", new DateTime)
        }
      })
    }
  }

  @Test
  def differenceUpgradingShouldBeConstantTime() {
    val pair = DiffaPairRef(key = "pair", domain = "domain")

    runPerformanceTest(4) { count =>
      linearCost(count)(() => {
        for (j <- 0L until count) {
          diffStore.addPendingUnmatchedEvent(VersionID(pair, "id" + j), new DateTime, "uV", "dV", new DateTime)
        }
        for (j <- 0L until count) {
          diffStore.upgradePendingUnmatchedEvent(VersionID(pair, "id" + j))
        }
      })
    }
  }

  @Test
  def matchInsertionShouldBeConstantTime() {
    val pair = DiffaPairRef(key = "pair", domain = "domain")

    runPerformanceTest(4) { count =>
      for (j <- 0L until count) {
        diffStore.addReportableUnmatchedEvent(VersionID(pair, "id" + j), new DateTime, "uV", "dV", new DateTime)
      }
      linearCost(count)(() => {
        for (j <- 0L until count) {
          diffStore.addMatchedEvent(VersionID(pair, "id" + j), "uV")
        }
      })
    }
  }

  @Test
  def differenceQueryShouldGrowLinearlyAsDifferencesIncrease() {
    val pair = DiffaPairRef(key = "pair", domain = "domain")
    val pair2 = DiffaPairRef(key = "pair2", domain = "domain")

    runPerformanceTest(4) { count =>
      for (j <- 0L until count) {
        diffStore.addReportableUnmatchedEvent(VersionID(pair, "id" + j), new DateTime, "uV", "dV", new DateTime)
        diffStore.addReportableUnmatchedEvent(VersionID(pair2, "id" + j), new DateTime, "uV", "dV", new DateTime)
      }
      linearCost(count)(() => {
        diffStore.retrievePagedEvents(pair, new Interval(new DateTime().minusHours(2), new DateTime().plusHours(2)),
          0, count.asInstanceOf[Int])
      })
    }
  }

  @Test
  @Ignore("detectedAt index doesn't appear to be correcting this")
  def differenceQueryShouldRemainConstantForSameNumberOfDifferences() {
    val pair = DiffaPairRef(key = "pair", domain = "domain")
    val pair2 = DiffaPairRef(key = "pair2", domain = "domain")

    runPerformanceTest(4, offset = 3) { count =>
      val now = new DateTime()
      val before = now.minusSeconds(10)
      val after = now.plusSeconds(10)
      val lotsAfter = now.plusHours(2)

      // Add count number of "noise entries"
      for (j <- 0L until count) {
        diffStore.addReportableUnmatchedEvent(VersionID(pair, "idB" + j), before, "uV", "dV", before)
        diffStore.addReportableUnmatchedEvent(VersionID(pair2, "id" + j), now, "uV", "dV", now)
      }

      // Add entries that we actually want to retrieve
      for (j <- 0L until 10) {
        diffStore.addReportableUnmatchedEvent(VersionID(pair, "id" + j), after, "uV", "dV", after)
      }

      constantCost(() => {
        diffStore.retrievePagedEvents(pair, new Interval(now, lotsAfter), 0, 10)
      })
    }
  }

  @Test
  def oldMismatchesShouldBeAbleToBeExpiredInConstantTime() {
    val pair = DiffaPairRef(key = "pair", domain = "domain")
    val pair2 = DiffaPairRef(key = "pair2", domain = "domain")

    runPerformanceTest(4) { count =>
      val now = new DateTime()
      val before = now.minusSeconds(10)
      val after = now.plusSeconds(10)

      // Add count number of "noise entries"
      for (j <- 0L until count) {
        diffStore.addReportableUnmatchedEvent(VersionID(pair, "idB" + j), new DateTime, "uV", "dV", after)
        diffStore.addReportableUnmatchedEvent(VersionID(pair2, "id" + j), new DateTime, "uV", "dV", new DateTime)
      }
      
      // Add a constant number of entries to be expired
      for (j <- 0L until 10) {
        diffStore.addReportableUnmatchedEvent(VersionID(pair, "id" + j), new DateTime, "uV", "dV", before)
      }
              
      // Time the expiry
      constantCost(() => {
        diffStore.matchEventsOlderThan(pair, now)
      })
    }
  }

  //
  // Support Methods
  //

  def runPerformanceTest(growth:Int, offset:Int = 1)(f:(Long) => (Long, Double)) {
    // Run the tests, and record the cost per operation at each growth rate
    println("Events,Total,Per Event")
    val costs = (offset until (growth+1)).map { i =>
      // Clear differences before each test run
      diffStore.clearAllDifferences

      val insertCount = scala.math.pow(10.0, i.asInstanceOf[Double]).asInstanceOf[Long]
      val (duration, cost) = f(insertCount)

      println(insertCount + "," + duration + "," + cost)

      i -> cost
    }

    // Ensure that no operation cost exceeds the cost for any smaller run by more than 20%
    ((offset + 1) until (growth+1)).foreach(i => {
      val (currentIdx, currentCost) = costs(i - 1)

      costs.slice(0, i).foreach { case (testIdx, cost) =>
        assertFalse(
          "Cost %s at index %s exceeded previous cost %s at index %s by more than 80%%".format(
            currentCost, currentIdx, cost, testIdx
          ),
          currentCost > (cost*1.8))
      }
    })
  }

  def linearCost(opCount:Long)(f:() => Unit) = {
    val startTime = System.currentTimeMillis()
    f()
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime

    (duration, duration.asInstanceOf[Double] / opCount)
  }

  def constantCost(f:() => Unit) = {
    val startTime = System.currentTimeMillis()
    f()
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime

    (duration, duration.asInstanceOf[Double])
  }
}

object DomainDifferenceStorePerfTest {
  FileUtils.deleteDirectory(new File("target/domain-cache-perf"))

  lazy val config =
    new Configuration().
      addResource("net/lshift/diffa/kernel/config/Config.hbm.xml").
      addResource("net/lshift/diffa/kernel/differencing/DifferenceEvents.hbm.xml").
      setProperty("hibernate.dialect", DatabaseEnvironment.DIALECT).
      setProperty("hibernate.connection.url", DatabaseEnvironment.substitutableURL("target/domain-cache-perf")).
      setProperty("hibernate.connection.driver_class", DatabaseEnvironment.DRIVER).
      setProperty("hibernate.connection.username", DatabaseEnvironment.USERNAME).
      setProperty("hibernate.connection.password", DatabaseEnvironment.PASSWORD).
      setProperty("hibernate.cache.region.factory_class", "net.sf.ehcache.hibernate.EhCacheRegionFactory").
      setProperty("hibernate.connection.autocommit", "true") // Turn this on to make the tests repeatable,
                                                             // otherwise the preparation step will not get committed

  lazy val sessionFactory = {
    val sf = config.buildSessionFactory
    (new HibernateConfigStorePreparationStep).prepare(sf, config)

    sf
  }

  lazy val cacheManager = new CacheManager()
  lazy val dialect = Class.forName(DatabaseEnvironment.DIALECT).newInstance().asInstanceOf[Dialect]
  lazy val diffStore = new HibernateDomainDifferenceStore(sessionFactory, cacheManager, dialect, new HookManager(config))
}
