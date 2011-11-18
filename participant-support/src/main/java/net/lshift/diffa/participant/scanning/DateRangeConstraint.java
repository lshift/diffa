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
package net.lshift.diffa.participant.scanning;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Constraint where a given attribute value is between a given start and end.
 */
public class DateRangeConstraint extends AbstractScanConstraint implements RangeConstraint {
  private static final DateTimeFormatter parser = ISODateTimeFormat.dateParser();
  private static final DateTimeFormatter formatter = ISODateTimeFormat.date();
  private final LocalDate start;
  private final LocalDate end;

  public DateRangeConstraint(String name, String start, String end) {
    this(name, maybeParse(start), maybeParse(end));
  }
  public DateRangeConstraint(String name, LocalDate start, LocalDate end) {
    super(name);

    this.start = start;
    this.end = end;
  }

  public LocalDate getStart() {
    return start;
  }

  public LocalDate getEnd() {
    return end;
  }

  public boolean contains(LocalDate value) {
    if (start != null && value.isBefore(start)) return false;
    if (end != null && value.isAfter(end)) return false;

    return true;
  }

  public boolean containsRange(LocalDate rangeStart, LocalDate rangeEnd) {
    // A null property indicate that the parent value should be inherited
    if (rangeStart == null && rangeEnd == null) return true;
    if (rangeStart == null) return contains(rangeEnd);
    if (rangeEnd == null) return contains(rangeStart);

    return contains(rangeStart) && contains(rangeEnd);
  }

  @Override
  public boolean hasLowerBound() {
    return start != null;
  }

  @Override
  public boolean hasUpperBound() {
    return end != null;
  }

  @Override
  public String getStartText() {
    if (start == null) {
      return null;
    }
    else {
      return start.toString(formatter);
    }
  }

  @Override
  public String getEndText() {
    if (end == null) {
      return null;
    }
    else {
      return end.toString(formatter);
    }
  }

  private static LocalDate maybeParse(String dateStr) {
    if (dateStr == null) {
      return null;
    } else {
      return parser.parseDateTime(dateStr).toLocalDate();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    DateRangeConstraint that = (DateRangeConstraint) o;

    if (end != null ? !end.equals(that.end) : that.end != null) return false;
    if (start != null ? !start.equals(that.start) : that.start != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (start != null ? start.hashCode() : 0);
    result = 31 * result + (end != null ? end.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "DateRangeConstraint{" +
        "name=" + getAttributeName() +
        ", start=" + start +
        ", end=" + end +
        '}';
  }
}
