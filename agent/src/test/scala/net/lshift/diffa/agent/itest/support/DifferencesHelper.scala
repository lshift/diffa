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
package net.lshift.diffa.agent.itest.support

import net.lshift.diffa.agent.client.DifferencesRestClient
import org.joda.time.DateTime
import net.lshift.diffa.kernel.differencing.{ExternalDifferenceEvent, DifferenceEvent}
import org.junit.Assert._

/**
 * Helper class for retrieving differences.
 */
class DifferencesHelper(pairKey:String, diffClient:DifferencesRestClient) {
  def pollForAllDifferences(from:DateTime, until:DateTime, n:Int = 20, wait:Int = 100, minLength:Int = 1) =
    tryAgain((d:DifferencesRestClient) => d.getEvents(pairKey, from, until, 0, 100) ,n,wait,minLength)

  def tryAgain(poll:DifferencesRestClient => Seq[ExternalDifferenceEvent], n:Int = 20, wait:Int = 100, minLength:Int = 1) : Seq[ExternalDifferenceEvent]= {
    var i = n
    var diffs = poll(diffClient)
    while(diffs.length < minLength && i > 0) {
      Thread.sleep(wait)

      diffs = poll(diffClient)
      i-=1
    }
    assertNotNull(diffs)
    diffs
  }

  def waitFor(from:DateTime, until:DateTime, conditions:DifferenceCondition*) = {
    val n = 20
    val wait = 100

    def poll() = diffClient.getEvents(pairKey, from, until, 0, 100)
    def satisfied(diffs:Seq[ExternalDifferenceEvent]) = conditions.forall(_.isSatisfiedBy(diffs))

    var i = n
    var diffs = poll()
    while(!satisfied(diffs) && i > 0) {
      Thread.sleep(wait)

      diffs = poll()
      i-=1
    }


    if (!satisfied(diffs)) {
      val message = conditions.filter(!_.isSatisfiedBy(diffs)).map(_.describeIssuesWith(diffs)).mkString(";")
      throw new Exception("Conditions weren't satisfied: " + message)
    }

    diffs
  }
}

abstract class DifferenceCondition {
  def isSatisfiedBy(diffs:Seq[ExternalDifferenceEvent]):Boolean
  def describeIssuesWith(diffs:Seq[ExternalDifferenceEvent]):String
}
case class DiffCount(count:Int) extends DifferenceCondition {
  def isSatisfiedBy(diffs: Seq[ExternalDifferenceEvent]) = diffs.length == count
  def describeIssuesWith(diffs: Seq[ExternalDifferenceEvent]) =
    "Didn't reach required diff count %s. Last attempt returned %s diffs (%s)".format(count, diffs.length, diffs)
}
case class DoesntIncludeObjId(id:String) extends DifferenceCondition {
  def isSatisfiedBy(diffs: Seq[ExternalDifferenceEvent]) = diffs.find(e => e.entityId == id).isEmpty
  def describeIssuesWith(diffs: Seq[ExternalDifferenceEvent]) =
    "Difference ids (%s) shouldn't have included %s".format(diffs.map(e => e.entityId), id)
}
case class IncludesObjId(id:String) extends DifferenceCondition {
  def isSatisfiedBy(diffs: Seq[ExternalDifferenceEvent]) = diffs.find(e => e.entityId == id).isDefined
  def describeIssuesWith(diffs: Seq[ExternalDifferenceEvent]) =
    "Difference ids (%s) should have included %s".format(diffs.map(e => e.entityId), id)
}