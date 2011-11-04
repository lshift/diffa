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
public class StringPrefixConstraint extends AbstractScanConstraint {
  private final String prefix;

  public StringPrefixConstraint(String name, String prefix) {
    super(name);

    this.prefix = prefix;
  }

  public String getPrefix() {
    return prefix;
  }

  public boolean contains(String value) {
    return value.startsWith(prefix);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    StringPrefixConstraint that = (StringPrefixConstraint) o;

    if (prefix != null ? !prefix.equals(that.prefix) : that.prefix != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (prefix != null ? prefix.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "StringPrefixConstraint{" +
        "name=" + getAttributeName() +
        ", prefix='" + prefix + '\'' +
        '}';
  }
}
