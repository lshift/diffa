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

package net.lshift.diffa.kernel.config;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.format.ISOPeriodFormat;
import org.joda.time.format.PeriodFormatter;

/**
 * 1) Determine whether a period is a refinement of a date range;
 * 2) Calculate the intersection of a period and a date range.
 */
public class WindowRefiner implements IntervalRefinement {
  private static final PeriodFormatter periodFormatter = ISOPeriodFormat.standard();

  private String periodExpression;
  private String offsetExpression;
  private Interval windowInterval;

  @Override
  public boolean isRefinementOf(String start, String end) {
    return new BoundedTimeInterval(start, end).overlaps(windowInterval);
  }

  @Override
  public TimeInterval refineInterval(String start, String end) {
    if (!isRefinementOf(start, end)) {
      return null;
    }

    return TimeIntervalFactory.fromRange(start, end).overlap(windowInterval);
  }

  public static WindowRefiner forPeriodExpression(String periodExpression) {
    return new WindowRefiner(periodExpression);
  }

  public WindowRefiner withOffset(String offsetExpression) {
    return new WindowRefiner(this.periodExpression, offsetExpression);
  }

  WindowRefiner usingTime(DateTime mockTime) {
    return new WindowRefiner(this.periodExpression, this.offsetExpression, mockTime);
  }

  private WindowRefiner(String periodExpression) {
    this(periodExpression, null);
  }

  private WindowRefiner(String periodExpression, String offsetExpression) {
    this(periodExpression, offsetExpression, DateTime.now().withZone(DateTimeZone.UTC));
  }

  private WindowRefiner(String periodExpression, String offsetExpression, DateTime now) {
    this.periodExpression = periodExpression;
    this.offsetExpression = offsetExpression;

    DateTime end;
    if (offsetExpression == null || offsetExpression.equals("")) {
      end = now;
    } else {
      DateTime startOfDay = now.toDateMidnight().toDateTime();
      end = startOfDay.plus(periodFormatter.parsePeriod(offsetExpression).toDurationFrom(startOfDay));
    }
    DateTime start = end.minus(periodFormatter.parsePeriod(periodExpression));
    this.windowInterval = new Interval(start, end);
  }
}
