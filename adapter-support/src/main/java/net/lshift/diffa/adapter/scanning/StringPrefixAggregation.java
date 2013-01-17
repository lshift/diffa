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

import java.util.HashSet;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Aggregation for strings by prefix.
 */
public class StringPrefixAggregation extends AbstractScanAggregation {

  public static final NavigableSet<Integer> DEFAULT_OFFSETS = new TreeSet<Integer>();

  static {
    DEFAULT_OFFSETS.add(1);
  }

  private final NavigableSet<Integer> offsets;


  public StringPrefixAggregation(String name, String parent, String ... offsets) {
    this(name, parent, parseOffsets(offsets));
  }

  public StringPrefixAggregation(String name, String parent, NavigableSet<Integer> offsets) {
    super(name, parent);

    this.offsets = offsets;
  }

  public NavigableSet<Integer> getOffsets() {
    return offsets;
  }

  @Override
  public String bucket(String attributeVal) {
    int length = (parent == null || parent.length() == 0) ? offsets.first() : offsets.higher(parent.length());
    if (attributeVal.length() <= length) return attributeVal;
    return attributeVal.substring(0, length);
  }



  public static NavigableSet<Integer> parseOffsets(String ... args) {

    if (args == null || args.length == 0) {
      return DEFAULT_OFFSETS;
    } else {

      TreeSet<Integer> offsets = new TreeSet<Integer>();

      for (String arg : args) {
        offsets.add(Integer.parseInt(arg));
      }

      return offsets;
    }

  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StringPrefixAggregation)) return false;
    if (!super.equals(o)) return false;

    StringPrefixAggregation that = (StringPrefixAggregation) o;

    if (offsets != null ? !offsets.equals(that.offsets) : that.offsets != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (offsets != null ? offsets.hashCode() : 0);
    return result;
  }
}
