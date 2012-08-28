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
package net.lshift.diffa.kernel.participants

import net.lshift.diffa.kernel.config.{PairRef, Endpoint, DiffaPairRef}

/**
 * Parent trait inherited by factories that create objects based on addresses and content types.
 */
trait AddressDrivenFactory[T] {
  /**
   * Determines whether this factory accepts addresses of the given form.
   */
  def supports(endpoint:Endpoint):Boolean

  /**
   * Creates a participant reference using the given address. It is expected the factory has
   * already been checked for compatibility via supportsAddress. The behaviour when calling this method without
   * previously checking is undefined, and the factory implementation may return a non-functional proxy.
   */
  def createParticipantRef(endpoint:Endpoint, pair:PairRef): T
}