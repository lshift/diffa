package net.lshift.diffa.kernel.differencing

import org.junit.Assume._
import org.hamcrest.CoreMatchers._
import org.junit.Test
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.config.DiffaPairRef
import org.joda.time.DateTime

/**
 * Performance test for the local domain cache.
 */
class LocalDomainCachePerfTest {
  assumeThat(System.getProperty("diffa.perftest"), is(equalTo("1")))

  @Test
  def differenceInsertionShouldBeConstantTime() {
    println("Events,Total,Per Event")
    for (i <- 1 until 7) {
      val insertCount = scala.math.pow(10.0, i.asInstanceOf[Double]).asInstanceOf[Long]

      val cache = new LocalDomainCache("domain")
      val pair = DiffaPairRef(key = "pair", domain = "domain")

      val startTime = System.currentTimeMillis()
      for (j <- 0L until insertCount) {
        cache.addReportableUnmatchedEvent(VersionID(pair, "id" + j), new DateTime, "uV", "dV")
      }
      val endTime = System.currentTimeMillis()
      val duration = endTime - startTime

      println(insertCount + "," + duration + "," + duration.asInstanceOf[Double] / insertCount)
    }
  }
}