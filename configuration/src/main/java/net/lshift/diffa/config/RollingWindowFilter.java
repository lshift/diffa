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

package net.lshift.diffa.config;

import net.lshift.diffa.adapter.scanning.DateRangeConstraint;
import net.lshift.diffa.adapter.scanning.RangeConstraint;
import net.lshift.diffa.adapter.scanning.ScanConstraint;

import net.lshift.diffa.adapter.scanning.TimeRangeConstraint;
import org.joda.time.DateTime;

/**
 * A category that is constrained by a moving window that behaves as a dynamically computed range category.
 */
public class RollingWindowFilter extends CategoryDescriptor {
  public String periodExpression;
  public String offsetDurationExpression;
  private WindowRefiner refiner;

  /**
   * At scan time, the interval (described by periodText) is used to effectively produce a Range Category as follows:
   * start-time = scan-time - interval
   * end-time = scan-time.
   *
   * @param periodExpression A standard ISO period specification, as defined in ISO 8601.
   *                   Examples: 3 months = P3M, 1 week = P1W, 1 day = P1D, 12 hours = PT12H.
   *
   * @see org.joda.time.Period (http://joda-time.sourceforge.net/api-release/index.html?org/joda/time/Period.html)
   * TODO: link in user docs to http://en.wikipedia.org/wiki/ISO_8601#Durations
   */
  public RollingWindowFilter(String periodExpression) {
    this(periodExpression, "");
  }

  /**
   * The first parameter describes a time interval, I.  At scan time, this interval is used to effectively
   * produce a Range Category as follows:.
   * start-time = start-of-day(scan-time - I) + offsetDuration
   * end-time = start-of-day(scan-time) + offsetDuration
   *
   * @param periodExpression An extended ISO period specification, as defined in ISO 8601.
   * @param offsetDurationExpression A Duration representing the offset from the beginning of the day, as described above.
   */
  public RollingWindowFilter(String periodExpression, String offsetDurationExpression) {
    this.periodExpression = periodExpression;
    this.offsetDurationExpression = offsetDurationExpression;
  }

  public RollingWindowFilter() {
    this("");
  }

  public void setPeriod(String periodExpression) {
    this.periodExpression = periodExpression;
  }

  public void setOffset(String offsetDurationExpression) {
    this.offsetDurationExpression = offsetDurationExpression;
  }

  public String getPeriod() {
    return this.periodExpression;
  }

  public String getOffset() {
    return this.offsetDurationExpression;
  }

  public WindowRefiner refiner() {
    if (refiner == null) {
      refiner = WindowRefiner.forPeriodExpression(periodExpression).withOffset(offsetDurationExpression);
    }

    return refiner;
  }

  public TimeRangeConstraint toConstraint(String name) {
    TimeInterval interval = refiner().refineInterval(null, null);

    return new TimeRangeConstraint(name,
        interval.getStartAs(DateTimeType.DATETIME),
        interval.getEndAs(DateTimeType.DATETIME));
  }

  @Override
  public void validate(String path) {
    try {
      refiner();
    } catch (Exception ex) {
      throw new ConfigValidationException(path,
          String.format("Invalid period or offset specified for Rolling Window: %s", ex.getLocalizedMessage()));
    }
  }

  @Override
  public boolean isSameType(CategoryDescriptor other) {
    return other instanceof RollingWindowFilter;
  }

  @Override
  public CategoryDescriptor applyRefinement(CategoryDescriptor refinement) {
    throw new IllegalArgumentException(refinement + " is not a refinement of " + this);
  }

  @Override
  public void validateConstraint(ScanConstraint constraint) {
    if (constraint instanceof DateRangeConstraint) {
      DateRangeConstraint requestedConstraint = (DateRangeConstraint) constraint;
      validateRangeContainment(requestedConstraint,
          requestedConstraint.getStart().toDateTimeAtStartOfDay(),
          requestedConstraint.getEnd().toDateTimeAtStartOfDay());
    } else if (constraint instanceof TimeRangeConstraint) {
      TimeRangeConstraint requestedConstraint = (TimeRangeConstraint) constraint;
      validateRangeContainment(requestedConstraint,
          requestedConstraint.getStart(),
          requestedConstraint.getEnd());
    } else {
      throw new InvalidConstraintException(constraint.getAttributeName(),
          "Rolling Window Categories only provide Date and Time Range Constraints - requested constraint was " +
              constraint.getClass().getName());
    }
  }

  private void validateRangeContainment(RangeConstraint requested, DateTime start, DateTime end) {
    TimeRangeConstraint categoryAsConstraint = toConstraint(requested.getAttributeName());
    if (!categoryAsConstraint.containsRange(start, end)) {
      throw new InvalidConstraintException(requested.getAttributeName(),
          requested + " is not contained within " + categoryAsConstraint);
    }
  }

  @Override
  public String toString() {
    return String.format("RollingWindowFilter{ period='%s', offset='%s' }",
        periodExpression, offsetDurationExpression);
  }
}
