package net.lshift.diffa.agent.itest.support

import net.lshift.diffa.kernel.differencing.PairScanState
import org.junit.Assert._
import net.lshift.diffa.agent.client.ScanningRestClient

object ScanningHelper {

  def waitForScanStatus(scanClient:ScanningRestClient, pairKey:String, state:PairScanState, n:Int = 30, wait:Int = 100) {
    def hasReached(states:Map[String, PairScanState], status: PairScanState = state) = {
      val currentStatus = states.getOrElse(pairKey, PairScanState.UNKNOWN)
      currentStatus == status
    }

    var i = n
    var scanStatus = scanClient.getScanStatus
    while(!hasReached(scanStatus) && !hasReached(scanStatus, PairScanState.FAILED) && i > 0) {
      Thread.sleep(wait)

      scanStatus = scanClient.getScanStatus
      i-=1
    }
    assertTrue("Unexpected scan state (pair = %s): %s (wanted %s)".format(pairKey, scanStatus, state), hasReached(scanStatus))
  }
}
