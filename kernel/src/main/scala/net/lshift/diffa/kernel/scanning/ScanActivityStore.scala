/**
 * Copyright (C) 2010-2012 LShift Ltd.
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
package net.lshift.diffa.kernel.scanning

import org.joda.time.DateTime
import net.lshift.diffa.kernel.config.PairRef
import net.lshift.diffa.kernel.differencing.PairScanState

/**
 * Provides access to reading and writing scan statements
 */
trait ScanActivityStore {

  def createOrUpdateStatement(s:ScanStatement)
  def getStatement(pair:PairRef, id:Long) : ScanStatement

}

case class ScanStatement(id:Long = System.currentTimeMillis(),
                         space:Long,
                         pair:String,
                         initiatedBy:Option[String] = None,
                         startTime:DateTime = new DateTime(),
                         endTime:Option[DateTime] = None,
                         state:String = "UNKNOWN")

object ScanStatement {
  def resolveScanState(scanState:PairScanState) = scanState match {
    case PairScanState.UP_TO_DATE => "COMPLETED"
    case PairScanState.FAILED     => "FAILED"
    case PairScanState.CANCELLED  => "CANCELLED"
    case _                        => "UNKNOWN"
  }
}
