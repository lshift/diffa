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

import net.lshift.diffa.participant.scanning.ScanResultEntry
import java.io.InputStream

/**
 * a JsonScanResultParser parses a scan result as JSON--chiefly, this allows
 * us to layer in functionality such as response-length checking and other
 * validation / accounting functionality via the stackable-trait pattern. Eg:
 * LengthCheckingParser.
 */

trait JsonScanResultParser {
  def parse(stream: InputStream): Seq[ScanResultEntry]

}
