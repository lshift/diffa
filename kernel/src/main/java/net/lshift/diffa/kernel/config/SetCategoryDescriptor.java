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

package net.lshift.diffa.kernel.config;

import net.lshift.diffa.kernel.util.InvalidConstraintException;
import net.lshift.diffa.participant.scanning.ScanConstraint;
import net.lshift.diffa.participant.scanning.SetConstraint;

import java.util.HashSet;
import java.util.Set;

/**
 * This describes a category that constrains attributes based on a set of values.
 */
public class SetCategoryDescriptor extends CategoryDescriptor {

  /**
   * The set of attribute values that a search space should contain.
   */
  public Set<String> values = new HashSet<String>();

  public SetCategoryDescriptor() {
  }

  public SetCategoryDescriptor(Set<String> values) {
    this.values = values;
  }

  public Set<String> getValues() {
    return values;
  }

  public void setValues(Set<String> values) {
    this.values = values;
  }

  @Override
  public boolean isRefinement(CategoryDescriptor other) {
    return other instanceof SetCategoryDescriptor &&
      this.values.containsAll(((SetCategoryDescriptor) other).values);
  }

  @Override
  public CategoryDescriptor applyRefinement(CategoryDescriptor refinement) {
    if (!isRefinement(refinement)) throw new IllegalArgumentException(refinement + " is not a refinement of " + this);

    return refinement;
  }

  @Override
  public void validateConstraint(ScanConstraint constraint) {
    if (!(constraint instanceof SetConstraint)) {
      throw new InvalidConstraintException(constraint.getAttributeName(),
        "Set Categories only support Set Constraints - provided constraint was " + constraint.getClass().getName());
    }

    SetConstraint sConstraint = (SetConstraint) constraint;
    if (!this.values.containsAll(sConstraint.getValues())) {
      throw new InvalidConstraintException(constraint.getAttributeName(),
        "Not all of the values " + sConstraint.getValues() + " are supported by category " + getValues());
    }
  }

  @Override
  public String toString() {
    return "SetCategoryDescriptor{" +
      "values=" + values +
      '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SetCategoryDescriptor)) return false;

    SetCategoryDescriptor that = (SetCategoryDescriptor) o;

    if (values != null ? !values.equals(that.values) : that.values != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return values != null ? values.hashCode() : 0;
  }
}
