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

package net.lshift.diffa.kernel.config

import org.jooq.{Result, Record}
import net.lshift.diffa.kernel.frontend.{PairViewDef, DomainPairDef}
import net.lshift.diffa.schema.tables.Pair.PAIR
import net.lshift.diffa.schema.tables.PairViews.PAIR_VIEWS
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.util.MissingObjectException
import java.util

object ResultMappingUtil {

  def recordToDomainPairDef(r:Record) : DomainPairDef = {
    DomainPairDef(
      key = r.getValue(PAIR.PAIR_KEY),
      upstreamName = r.getValue(PAIR.UPSTREAM),
      downstreamName = r.getValue(PAIR.DOWNSTREAM),
      matchingTimeout = r.getValue(PAIR.MATCHING_TIMEOUT),
      versionPolicyName = r.getValue(PAIR.VERSION_POLICY_NAME),
      scanCronSpec = r.getValue(PAIR.SCAN_CRON_SPEC),
      allowManualScans = r.getValue(PAIR.ALLOW_MANUAL_SCANS),
      views = null // Probably not needed by things that use this query, famous last words.....
    )
  }

  /**
   * This fetches the associated PAIR_VIEWS child records from the PAIR parent,
   * under the assumption that the result set only contains one parent record.
   */
  def singleParentRecordToDomainPairDef(result:Result[Record]) : DomainPairDef = {

    val record = result.get(0)

    val pair = DomainPairDef(
      domain = record.getValue(PAIR.DOMAIN),
      key = record.getValue(PAIR.PAIR_KEY),
      upstreamName = record.getValue(PAIR.UPSTREAM),
      downstreamName = record.getValue(PAIR.DOWNSTREAM),
      matchingTimeout = record.getValue(PAIR.MATCHING_TIMEOUT),
      versionPolicyName = record.getValue(PAIR.VERSION_POLICY_NAME),
      scanCronSpec = record.getValue(PAIR.SCAN_CRON_SPEC),
      allowManualScans = record.getValue(PAIR.ALLOW_MANUAL_SCANS),
      views = new util.ArrayList[PairViewDef]()
    )

    result.iterator().filterNot(_.getValue(PAIR_VIEWS.NAME) == null).foreach(r => {
      pair.views.add(PairViewDef(
        name = r.getValue(PAIR_VIEWS.NAME),
        scanCronSpec = r.getValue(PAIR_VIEWS.SCAN_CRON_SPEC)
      ))
    })

    pair
  }
}
