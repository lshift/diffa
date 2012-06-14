/**
 * Copyright (C) 2012 LShift Ltd.
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
package net.lshift.diffa.agent.rest

import java.security.MessageDigest
import org.apache.commons.codec.binary.Hex
import collection.JavaConversions._
import net.lshift.diffa.participant.scanning._
import java.util.Comparator
import net.lshift.diffa.kernel.config.AsciiCollationOrdering

/**
 * Utilities for helping to make Diffa itself scannable.
 */
object ScannableUtils {
  def generateDigest(values:String*) = {
    val digest = MessageDigest.getInstance("MD5")
    values.filter(_ != null).foreach(v => digest.update(v.getBytes("UTF-8")))

    new String(Hex.encodeHex(digest.digest()))
  }

  def filterByKey[T](values:Seq[T], constraints:Seq[ScanConstraint], extract:T => String) = {
    values.filter(v => {
      constraints.forall {
        case prefix:StringPrefixConstraint => prefix.contains(extract(v))
        case _                             => false
      }
    })
  }


  var defaultComparison: Comparator[AnyRef] = new Comparator[AnyRef] {
    def compare(a: AnyRef, b: AnyRef): Int = compare(a.asInstanceOf[String], b.asInstanceOf[String])
    def compare(a:String, b:String): Int = a.compareTo(b)
  }
  def maybeAggregate(entries:Seq[ScanResultEntry], aggregations:Seq[ScanAggregation]) :java.util.List[ScanResultEntry] =
    maybeAggregate(entries, aggregations, AsciiCollationOrdering)

  def maybeAggregate(entries:Seq[ScanResultEntry], aggregations:Seq[ScanAggregation], collator: Collation):java.util.List[ScanResultEntry] = {
    if (aggregations.length > 0) {
      val digester = new DigestBuilder(aggregations, collator)
      entries.foreach(digester.add(_))
      digester.toDigests
    } else {
      entries
    }
  }
}