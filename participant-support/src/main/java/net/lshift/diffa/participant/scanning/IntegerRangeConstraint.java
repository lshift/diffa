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

/**
 * Constraint for an integer that should exist (inclusive) within a given range.
 */
public class IntegerRangeConstraint extends AbstractScanConstraint implements RangeConstraint {
  private final Integer start;
  private final Integer end;

  public IntegerRangeConstraint(String name, String start, String end) {
    this(name, maybeParse(start), maybeParse(end));
  }
  public IntegerRangeConstraint(String name, Integer start, Integer end) {
    super(name);

    this.start = start;
    this.end = end;
  }

  public Integer getStart() {
    return start;
  }

  public Integer getEnd() {
    return end;
  }

  public boolean contains(Integer value) {
    if (start != null && value < start) return false;
    if (end != null && value > end) return false;
    
    return true;
  }

  public boolean containsRange(Integer rangeStart, Integer rangeEnd) {
    // A null property indicate that the parent value should be inherited
    if (rangeStart == null && rangeEnd == null) return true;
    if (rangeStart == null) return contains(rangeEnd);
    if (rangeEnd == null) return contains(rangeStart);

    return contains(rangeStart) && contains(rangeEnd);
  }

  @Override
  public String getStartText() {
    return Integer.toString(start);
  }

  @Override
  public String getEndText() {
    return Integer.toString(end);
  }

  private static Integer maybeParse(String intStr) {
    if (intStr == null) {
      return null;
    } else {
      return Integer.parseInt(intStr);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    IntegerRangeConstraint that = (IntegerRangeConstraint) o;

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
    return "IntegerRangeConstraint{" +
        "name=" + getAttributeName() +
        ", start=" + start +
        ", end=" + end +
        '}';
  }
}
