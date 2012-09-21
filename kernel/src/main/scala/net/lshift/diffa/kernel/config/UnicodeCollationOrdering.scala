/**
 * Copyright (C) 2010-2011 LShift Ltd.
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

package net.lshift.diffa.kernel.config

import net.lshift.diffa.participant.scanning.{AsciiCollation, UnicodeCollation, Collation}

object UnicodeCollationOrdering extends UnicodeCollation with CollationOrdering {
  val name = "unicode"
}


object AsciiCollationOrdering extends AsciiCollation with CollationOrdering {
  val name = "ascii"
}

object UnorderedCollationOrdering extends CollationOrdering {
  val name = "unordered"
  override def sortsBefore(left: String, right: String) = true
}

trait CollationOrdering extends Collation {
  val name : String
}

object CollationOrdering {
  def named(name: String): CollationOrdering = {
    namedCollations.find(_.name == name).getOrElse {
      throw new InvalidCollationOrderingSpecified(name)
    }
  }


  lazy val namedCollations: Set[CollationOrdering] = Set(
    UnorderedCollationOrdering,
    UnicodeCollationOrdering,
    AsciiCollationOrdering
  )
}

class InvalidCollationOrderingSpecified(name: String) extends Exception("Unknown collation specified: %s".format(name))