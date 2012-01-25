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

import net.lshift.diffa.kernel.participants._
import net.lshift.diffa.kernel.events._
import scala.collection.JavaConversions._
import net.lshift.diffa.participant.scanning.{ScanConstraint, ScanResultEntry}
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.config.DiffaPairRef

/**
 * Version policy where two events are considered the same only when the upstream and downstream provide the
 * same version information. The downstream is expected to have the same interpretation of versions as the upstream,
 * and hashing mechanisms are assumed to be portable.
 *
 * Compliance with this policy could also be achieved by the downstream simply recording the versions of received
 * upstream events.
 */
class SameVersionPolicy(stores:VersionCorrelationStoreFactory, listener:DifferencingListener, systemConfigStore:SystemConfigStore, diagnostics:DiagnosticsManager)
    extends BaseScanningVersionPolicy(stores, listener, systemConfigStore, diagnostics) {

  def downstreamStrategy(us:UpstreamParticipant, ds:DownstreamParticipant) = new DownstreamSameScanStrategy

  protected class DownstreamSameScanStrategy extends ScanStrategy {
    val name = "DownstreamSame"

    def getAggregates(pair:DiffaPairRef, bucketing:Seq[CategoryFunction], constraints:Seq[ScanConstraint]) = {
      val aggregator = new Aggregator(bucketing)
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
        case VersionMismatch(id, categories, lastUpdated, partVsn, _) =>
          if (partVsn != null) {
            handleUpdatedCorrelation(writer.storeDownstreamVersion(VersionID(pair, id), categories, lastUpdated, partVsn, partVsn))
          } else {
            handleUpdatedCorrelation(writer.clearDownstreamVersion(VersionID(pair, id)))
          }
      }
    }
  }
}