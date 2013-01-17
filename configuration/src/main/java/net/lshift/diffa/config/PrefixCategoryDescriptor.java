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

package net.lshift.diffa.config;


import net.lshift.diffa.adapter.scanning.ScanConstraint;
import net.lshift.diffa.adapter.scanning.StringPrefixConstraint;

import java.util.*;

/**
 * This describes a category that can be constrained by a prefix.
 */
public class PrefixCategoryDescriptor extends AggregatingCategoryDescriptor {

  public PrefixCategoryDescriptor() {
    this.offsets = new TreeSet<Integer>();
  }

  public PrefixCategoryDescriptor(NavigableSet<Integer> offsets) {
    this.offsets = offsets;
  }

  public PrefixCategoryDescriptor(Integer ... offsets) {

    boolean ascending = true;

    for (int i = 1; i < offsets.length && ascending; i++) {
      ascending = ascending && offsets[i] > offsets[i-1];
    }

    if (!ascending) {
      throw new IllegalArgumentException("Offsets were not ascending: " + Arrays.asList(offsets));
    }

    this.offsets = new TreeSet<Integer>();
    Collections.addAll(this.offsets, offsets);

  }

  public NavigableSet<Integer> offsets;

  public NavigableSet<Integer> getOffsets() {
    return offsets;
  }

  public void addOffset(int offset) {
    offsets.add(offset);
  }

  @Override
  public void validate(String path) {
    if (getOffsets() == null || getOffsets().size() == 0) {
      throw new ConfigValidationException(path, "offsets cannot be empty");
    }

    for (int offset : getOffsets()) {
      if (offset < 1) {
        throw new ConfigValidationException(path, "offset cannot be less than 1: " + getOffsets());
      }
    }

  }

  @Override
  public boolean isSameType(CategoryDescriptor other) {
    return (other instanceof PrefixCategoryDescriptor);
  }

  @Override
  public boolean isRefinement(CategoryDescriptor other) {
    return isSameType(other);
  }

  @Override
  public CategoryDescriptor applyRefinement(CategoryDescriptor refinement) {
    if (!isRefinement(refinement)) throw new IllegalArgumentException(refinement + " is not a refinement of " + this);

    return refinement;
  }

  @Override
  public void validateConstraint(ScanConstraint constraint) {
    if (!(constraint instanceof StringPrefixConstraint)) {
      throw new InvalidConstraintException(constraint.getAttributeName(),
        "Prefix Categories only support Prefix Constraints - provided constraint was " + constraint.getClass().getName());
    }
  }

  @Override
  public String toString() {
    return "PrefixCategoryDescriptor{" +
        "offsets=" + offsets +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PrefixCategoryDescriptor)) return false;

    PrefixCategoryDescriptor that = (PrefixCategoryDescriptor) o;

    if (offsets != null ? !offsets.equals(that.offsets) : that.offsets != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return offsets != null ? offsets.hashCode() : 0;
  }
}
