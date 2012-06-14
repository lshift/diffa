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
import net.lshift.diffa.kernel.config.DiffaPairRef
import net.lshift.diffa.participant.scanning.{Collation, ScanAggregation, ScanConstraint, ScanResultEntry}


/**
 * Version policy where two events are considered the same based on the downstream reporting the same upstream
 * version upon processing. The downstream is not expected to reproduce the same digests as the upstream on demand,
 * and matching recovery will require messages to be reprocessed via a differencing back-channel to determine
 * whether they are identical.
 */
class CorrelatedVersionPolicy(stores:VersionCorrelationStoreFactory,
                              listener:DifferencingListener,
                              systemConfigStore:SystemConfigStore,
                              diagnostics:DiagnosticsManager)
    extends BaseScanningVersionPolicy(stores, listener, systemConfigStore, diagnostics) {

  def downstreamStrategy(us:UpstreamParticipant, ds:DownstreamParticipant, collation: Collation) =
    new DownstreamCorrelatingScanStrategy(us,ds, collation)
  
  protected class DownstreamCorrelatingScanStrategy(val us:UpstreamParticipant, val ds:DownstreamParticipant,
                                                     collation: Collation                                                     )
      extends ScanStrategy {
    val name = "DownstreamCorrelating"


    def getAggregates(pair:DiffaPairRef, bucketing:Seq[ScanAggregation], constraints:Seq[ScanConstraint]) = {
      val aggregator = new Aggregator(bucketing, collation)
      stores(pair).queryDownstreams(constraints, aggregator.collectDownstream)
      aggregator.digests
    }

    def getEntities(pair:DiffaPairRef, constraints:Seq[ScanConstraint]) = {
      stores(pair).queryDownstreams(constraints).map(x => {
        ScanResultEntry.forEntity(x.id, x.downstreamDVsn, x.lastUpdate, mapAsJavaMap(x.downstreamAttributes))
      })
    }

    def handleMismatch(pair:DiffaPairRef, writer: LimitedVersionCorrelationWriter, vm:VersionMismatch, listener:DifferencingListener) = {
      vm match {
        case VersionMismatch(id, categories, _, null, storedVsn) =>
          handleUpdatedCorrelation(writer.clearDownstreamVersion(VersionID(pair, id)))
        case VersionMismatch(id, categories, lastUpdated, partVsn, _) =>
          val content = us.retrieveContent(id)
          val response = ds.generateVersion(content)

          if (response.getDvsn == partVsn) {
            // This is the same destination object, so we're safe to store the correlation
            handleUpdatedCorrelation(writer.storeDownstreamVersion(VersionID(pair, id), categories, lastUpdated, response.getUvsn, response.getDvsn))
          } else {
            // We don't know of an upstream version, so we'll put in a proxy dummy value.
              // TODO: Is this an appropriate behaviour?
            handleUpdatedCorrelation(writer.storeDownstreamVersion(VersionID(pair, id), categories, lastUpdated, "UNKNOWN", partVsn))
          }
      }
    }
  }
}
