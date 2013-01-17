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
package net.lshift.diffa.adapter.scanning;

/**
 * Aggregation for integers.
 */
public class IntegerAggregation extends AbstractScanAggregation implements GranularityAggregation {
  private final int granularity;


  public IntegerAggregation(String name, String granularity, String parent) {
    this(name, parseGranularity(granularity), parent);
  }

  public IntegerAggregation(String name, int granularity, String parent) {
    super(name, parent);

    this.granularity = granularity;
  }

  @Override
  public String bucket(String attributeVal) {
    int val = Integer.parseInt(attributeVal);

    return Integer.toString(granularity * (val / granularity));
  }

  public int getGranularity() {
    return granularity;
  }

  @Override
  public String getGranularityString() {
    return Integer.toString(granularity) + "s";
  }

  public static int parseGranularity(String granStr) {
    if (!granStr.endsWith("s")) {
      throw new IllegalArgumentException("Invalid granularity " + granStr + " - must end with s for integer granularity");
    }

    return Integer.parseInt(granStr.substring(0, granStr.length() - 1));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    IntegerAggregation that = (IntegerAggregation) o;

    if (granularity != that.granularity) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + granularity;
    return result;
  }
}
