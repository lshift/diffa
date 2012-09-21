/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.lshift.diffa.client

import java.io.{IOException, InputStream}
import net.lshift.diffa.participant.common.JSONHelper
import net.lshift.diffa.schema.servicelimits.ScanResponseSizeLimit
import net.lshift.diffa.kernel.differencing.ScanLimitBreachedException
import net.lshift.diffa.kernel.config.{PairServiceLimitsView, PairRef}
import scala.collection.JavaConversions._

class ValidatingScanResultParser(validatorFactory: ScanEntityValidatorFactory) extends JsonScanResultParser {
  def parse(s: InputStream) = JSONHelper.readQueryResult(s, validatorFactory.createValidator).toSeq
}


trait LengthCheckingParser extends JsonScanResultParser {
  val serviceLimitsView: PairServiceLimitsView
  val pair: PairRef

  abstract override def parse(s: InputStream) = {
    val responseSizeLimit = serviceLimitsView.getEffectiveLimitByNameForPair(
      pair.space, pair.name, ScanResponseSizeLimit)
    try {
      super.parse(new LengthCheckingInputStream(s, responseSizeLimit))
    } catch {
      case e:IOException if e.getCause.isInstanceOf[ScanLimitBreachedException] => throw e.getCause
    }
  }

  class LengthCheckingInputStream(stream: InputStream, sizeLimit:Int) extends InputStream {
    var numBytes = 0;
    def read(): Int = {
      val byte = stream.read()
      if (byte >=0) numBytes += 1

      if (numBytes > sizeLimit) {
        val msg = "Scan response size for pair %s exceeded configured limit of %d bytes".format(
          pair.name, sizeLimit)
        throw new ScanLimitBreachedException(msg)
      } else {
        byte
      }
    }
  }

}
