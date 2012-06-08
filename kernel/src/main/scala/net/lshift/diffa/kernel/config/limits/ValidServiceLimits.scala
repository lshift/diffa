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
package net.lshift.diffa.kernel.config.limits

import net.lshift.diffa.kernel.config.ServiceLimit
import net.lshift.diffa.kernel.util.MissingObjectException

/**
 * This is a registry of known service limit definitions.
 */
object ValidServiceLimits {

  private val definitions = Seq(
    ChangeEventRate,
    DiagnosticEventBufferSize,
    ExplainFiles,
    ScanConnectTimeout,
    ScanReadTimeout,
    ScanResponseSizeLimit
  ).map(d => (d.key, d)).toMap[String,ServiceLimit]

  /**
   * @throws MissingObjectException If this limit is not defined
   */
  def lookupLimit(key:String) : ServiceLimit = definitions.get(key) match {
    case Some(limit) => limit
    case None        => throw new MissingObjectException(key)
  }
}
