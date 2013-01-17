package net.lshift.diffa.kernel.frontend

import org.junit.Test
import org.junit.Assert._
import net.lshift.diffa.adapter.scanning.{AsciiCollation, UnicodeCollation}

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

class EndpointDefTest {
  @Test def testGetCollatorForUnicode() = {
    val ep = EndpointDef(collation = UnicodeCollation.get.getName)
    assertEquals(UnicodeCollation.get, ep.lookupCollation)
  }

  @Test def testGetCollatorForAscii() = {
    val ep = EndpointDef(collation = AsciiCollation.get.getName)
    assertEquals(AsciiCollation.get, ep.lookupCollation)
  }
}
