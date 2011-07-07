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

/**
 * Listener that receives notifications when the synchronization state of the given pair changes.
 */
trait PairSyncListener {
  /**
   * Indicates that the sync state of the given pair has changed.
   */
  def pairSyncStateChanged(pairKey:String, syncState:PairScanState, statusMessage:String)

  /**
   * Indicates that the status message for the given pair has changed (but the overall state is still the same).
   */
  def pairSyncStatusChanged(pairKey:String, statusMessage:String)
}