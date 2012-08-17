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
import net.lshift.diffa.schema.tables.Pairs.PAIRS
import net.lshift.diffa.schema.tables.Spaces.SPACES
import net.lshift.diffa.schema.tables.PairViews.PAIR_VIEWS
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.util.MissingObjectException
import java.util

object ResultMappingUtil {

  def recordToDomainPairDef(r:Record) : DomainPairDef = {
    DomainPairDef(
      space = r.getValue(PAIRS.SPACE),
      key = r.getValue(PAIRS.NAME),
      domain = r.getValue(SPACES.NAME),
      upstreamName = r.getValue(PAIRS.UPSTREAM),
      downstreamName = r.getValue(PAIRS.DOWNSTREAM),
      matchingTimeout = r.getValue(PAIRS.MATCHING_TIMEOUT),
      versionPolicyName = r.getValue(PAIRS.VERSION_POLICY_NAME),
      scanCronSpec = r.getValue(PAIRS.SCAN_CRON_SPEC),
      allowManualScans = r.getValue(PAIRS.ALLOW_MANUAL_SCANS),
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
      space = record.getValue(PAIRS.SPACE),
      key = record.getValue(PAIRS.NAME),
      upstreamName = record.getValue(PAIRS.UPSTREAM),
      downstreamName = record.getValue(PAIRS.DOWNSTREAM),
      matchingTimeout = record.getValue(PAIRS.MATCHING_TIMEOUT),
      versionPolicyName = record.getValue(PAIRS.VERSION_POLICY_NAME),
      scanCronSpec = record.getValue(PAIRS.SCAN_CRON_SPEC),
      scanCronEnabled = record.getValue(PAIRS.SCAN_CRON_ENABLED),
      allowManualScans = record.getValue(PAIRS.ALLOW_MANUAL_SCANS),
      views = new util.ArrayList[PairViewDef]()
    )

    result.iterator().filterNot(_.getValue(PAIR_VIEWS.NAME) == null).foreach(r => {
      pair.views.add(PairViewDef(
        name = r.getValue(PAIR_VIEWS.NAME),
        scanCronSpec = r.getValue(PAIR_VIEWS.SCAN_CRON_SPEC),
        scanCronEnabled = r.getValue(PAIR_VIEWS.SCAN_CRON_ENABLED)
      ))
    })

    pair
  }
}
