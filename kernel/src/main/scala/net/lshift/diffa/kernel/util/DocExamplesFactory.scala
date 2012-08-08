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

package net.lshift.diffa.kernel.util

import net.lshift.diffa.kernel.events.VersionID
import org.joda.time.DateTime
import scala.collection.Map
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.differencing.{MatchState, DifferenceEvent}
import net.lshift.diffa.kernel.client.Actionable
import net.lshift.diffa.kernel.frontend.wire.InvocationResult
import net.lshift.diffa.kernel.frontend._
import net.lshift.diffa.kernel.config.{DiffaPairRef, RangeCategoryDescriptor}

/**
 * Factory that returns a map of example usages of classes for doc generation.
 */
class DocExamplesFactory {

  val categoryDescriptor = new RangeCategoryDescriptor("datetime")

  val up = new EndpointDef(name = "upstream-system",
                           scanUrl = "http://acme.com/upstream/scan",
                           contentRetrievalUrl = "http://acme.com/upstream/node-content",
                           inboundUrl = "http://diff.io/domain/changes",
                           categories = Map("bizDate" -> categoryDescriptor))

  val pair = PairDef(key = "pairKey", upstreamName = "upstream", downstreamName = "downstream",
                     versionPolicyName = "same", scanCronSpec = "0 15 10 ? * *", matchingTimeout = 10)

  val repair = RepairActionDef(name = "resend",
                               url = "http://acme.com/repairs/resend/{id}",
                               scope = "entity")

  val escalation = EscalationDef(name = "some-escalation",
                                 action = "resend", actionType = "repair",
                                 rule = "downstreamVsn is null", delay = 10)

  val actionable = Actionable(name = "resend",
                              scope = "entity",
                              path = "/actions/pairKey/resend/entity/${id}",
                              pair = "pairKey")

  val result = InvocationResult(code = "200", output = "OK")

  val user = UserDef(name = "joe.public", email = "joe.public@acme.com")

  val event = DifferenceEvent("6f72b9",VersionID(DiffaPairRef("pairKey", "mydomain"), "4f8a99"),
                           new DateTime(),
                           MatchState.UNMATCHED, "upstreamV", "downstreamV", new DateTime)

  def getExamples: java.util.Map[Class[_], Object] = {
    val map = new java.util.HashMap[Class[_], Object]

    map.put(classOf[EndpointDef], up)
    map.put(classOf[PairDef], pair)
    map.put(classOf[DifferenceEvent], event)
    map.put(classOf[RepairActionDef], repair)
    map.put(classOf[EscalationDef], escalation)
    map.put(classOf[Actionable], actionable)
    map.put(classOf[InvocationResult], result)
    map.put(classOf[UserDef], user)

    map
  }

}