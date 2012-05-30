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
package net.lshift.diffa.kernel.util.sequence

import com.hazelcast.core.Hazelcast

class HazelcastSequenceProvider extends SequenceProvider {

  def nextSequenceValue(name: String) = {
    val atomic = Hazelcast.getAtomicNumber(name)
    atomic.incrementAndGet()
  }

  def currentSequenceValue(name: String) = {
    val atomic = Hazelcast.getAtomicNumber(name)
    atomic.get()
  }

  def upgradeSequenceValue(name: String, expectation: Long, newValue: Long) = {
    val atomic = Hazelcast.getAtomicNumber(name)
    atomic.compareAndSet(expectation, newValue)
  }
}
