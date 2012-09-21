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

package net.lshift.diffa.kernel.differencing

import net.lshift.diffa.kernel.events._
import net.lshift.diffa.kernel.participants._
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.config.PairRef
import net.lshift.diffa.participant.scanning.{Collation, ScanAggregation, ScanConstraint, ScanResultEntry}


/**
 * Version policy where two events are considered the same based on the downstream reporting the same upstream
 * version upon processing. The downstream is not expected to reproduce the same digests as the upstream on demand,
 * and matching recovery will require messages to be reprocessed via a differencing back-channel to determine
 * whether they are identical.
 */
class CorrelatedVersionPolicy(stores:VersionCorrelationStoreFactory,
                              listener:DifferencingListener,
                              diagnostics:DiagnosticsManager)
    extends BaseScanningVersionPolicy(stores, listener, diagnostics) {

  def downstreamStrategy(us:UpstreamParticipant, ds:DownstreamParticipant, collation: Collation) =
    new DownstreamCorrelatingScanStrategy(us,ds, collation)
  
  protected class DownstreamCorrelatingScanStrategy(val us:UpstreamParticipant, val ds:DownstreamParticipant,
                                                     collation: Collation                                                     )
      extends ScanStrategy {
    val name = "DownstreamCorrelating"


    def getAggregates(pair:PairRef, bucketing:Seq[ScanAggregation], constraints:Seq[ScanConstraint]) = {
      val aggregator = new Aggregator(bucketing, collation)
      stores(pair).queryDownstreams(constraints, aggregator.collectDownstream)
      aggregator.digests
    }

    def getEntities(pair:PairRef, constraints:Seq[ScanConstraint]) = {
      stores(pair).queryDownstreams(constraints).map(x => {
        ScanResultEntry.forEntity(x.id, x.downstreamDVsn, x.lastUpdate, mapAsJavaMap(x.downstreamAttributes))
      })
    }

    def handleMismatch(scanId:Option[Long], pair:PairRef, writer: LimitedVersionCorrelationWriter, vm:VersionMismatch, listener:DifferencingListener) = {
      vm match {
        case VersionMismatch(id, categories, _, null, storedVsn) =>
          val correlation = writer.synchronized{
            writer.clearDownstreamVersion(VersionID(pair, id), scanId)
          }
          handleUpdatedCorrelation(correlation)
        case VersionMismatch(id, categories, lastUpdated, partVsn, _) =>
          val content = us.retrieveContent(id)
          val response = ds.generateVersion(content)

          val correlation = writer.synchronized {
            if (response.getDvsn == partVsn) {
              // This is the same destination object, so we're safe to store the correlation
              writer.storeDownstreamVersion(VersionID(pair, id), categories, lastUpdated, response.getUvsn, response.getDvsn, scanId)
            } else {
              // We don't know of an upstream version, so we'll put in a proxy dummy value.
                // TODO: Is this an appropriate behaviour?
              writer.storeDownstreamVersion(VersionID(pair, id), categories, lastUpdated, "UNKNOWN", partVsn, scanId)
            }
          }
          handleUpdatedCorrelation(correlation)
      }
    }
  }
}
