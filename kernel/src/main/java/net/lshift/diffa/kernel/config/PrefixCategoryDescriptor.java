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
import net.lshift.diffa.participant.scanning.StringPrefixConstraint;

/**
 * This describes a category that can be constrained by a prefix.
 */
public class PrefixCategoryDescriptor extends CategoryDescriptor {

  public PrefixCategoryDescriptor() {
  }

  public PrefixCategoryDescriptor(int prefixLength, int maxLength, int step) {
    this.prefixLength = prefixLength;
    this.maxLength = maxLength;
    this.step = step;
  }

  public int prefixLength;
  public int maxLength;
  public int step;

  public int getPrefixLength() {
    return prefixLength;
  }

  public void setPrefixLength(int prefixLength) {
    this.prefixLength = prefixLength;
  }

  public int getMaxLength() {
    return maxLength;
  }

  public void setMaxLength(int maxLength) {
    this.maxLength = maxLength;
  }

  public int getStep() {
    return step;
  }

  public void setStep(int step) {
    this.step = step;
  }

  @Override
  public void validate(String path) {
    if (getPrefixLength() < 0) {
      throw new ConfigValidationException(path, "length cannot be negative");
    }
    if (getMaxLength() < 1) {
      throw new ConfigValidationException(path, "maximum must be at least 1");
    }
    if (getStep() < 1) {
      throw new ConfigValidationException(path, "step must be at least 1");
    }
    if (getPrefixLength() > getMaxLength()) {
      throw new ConfigValidationException(path, "maximum must be equal to or larger than initial prefix length");
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

    StringPrefixConstraint pConstraint = (StringPrefixConstraint) constraint;
    if (pConstraint.getPrefix().length() < this.getPrefixLength()) {
      throw new InvalidConstraintException(constraint.getAttributeName(),
        "Prefix " + pConstraint.getPrefix() + " is shorter than configured start length " + getPrefixLength());
    }
    if (pConstraint.getPrefix().length() > this.getMaxLength()) {
      throw new InvalidConstraintException(constraint.getAttributeName(),
        "Prefix " + pConstraint.getPrefix() + " is longer than configured max length " + getMaxLength());
    }
  }

  @Override
  public String toString() {
    return "PrefixCategoryDescriptor{" +
      "prefixLength=" + prefixLength +
      ", maxLength=" + maxLength +
      ", step=" + step +
      '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PrefixCategoryDescriptor)) return false;

    PrefixCategoryDescriptor that = (PrefixCategoryDescriptor) o;

    if (maxLength != that.maxLength) return false;
    if (prefixLength != that.prefixLength) return false;
    if (step != that.step) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = prefixLength;
    result = 31 * result + maxLength;
    result = 31 * result + step;
    return result;
  }
}
