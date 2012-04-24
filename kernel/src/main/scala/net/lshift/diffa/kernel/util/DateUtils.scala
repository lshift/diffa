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

import org.joda.time.DateTime

/**
 * Utility methods that can be imported to help with working with dateTimes in tests.
 */
object DateUtils {
  /**
   * Forces the given date to be the very start of the day
   */
  def startOfDay(date:DateTime) = date.withMillisOfDay(0)

  /**
   * Returns the time just before the end of the day that start resides in.
   */
  def endOfDay(date:DateTime) = startOfDay(date).plusDays(1).minusMillis(1)

  /**
   * Checks whether the given start time is before the given end time. If either value is null, the check will not
   * be performed.
   * @returns true - if the start is before the end (or a bound is null); false - the start is not before the end;
   */
  def safeIsBefore(start:DateTime, end:DateTime):Boolean = {
    if (start != null && end != null) {
      if (start.isAfter(end)) {
        return false
      }
    }

    true
  }
}