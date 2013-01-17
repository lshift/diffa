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
 * Base implementation of a ScanAggregation.
 */
public abstract class AbstractScanAggregation implements ScanAggregation {

  protected final String attrName;
  protected final String parent;

  public AbstractScanAggregation(String attrName, String parent) {
    this.attrName = attrName;
    this.parent = parent;
  }

  @Override
  public String getAttributeName() {
    return attrName;
  }

  @Override
  public String getParent() {
    return parent;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AbstractScanAggregation)) return false;

    AbstractScanAggregation that = (AbstractScanAggregation) o;

    if (attrName != null ? !attrName.equals(that.attrName) : that.attrName != null) return false;
    if (parent != null ? !parent.equals(that.parent) : that.parent != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = attrName != null ? attrName.hashCode() : 0;
    result = 31 * result + (parent != null ? parent.hashCode() : 0);
    return result;
  }
}
