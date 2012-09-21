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
import net.lshift.diffa.participant.scanning.*;

/**
 * This describes a category that can be constrained by range.
 */
public class RangeCategoryDescriptor extends CategoryDescriptor {

  public RangeCategoryDescriptor() {
  }

  public RangeCategoryDescriptor(String dataType) {
    this.dataType = dataType;
  }

  public RangeCategoryDescriptor(String dataType, String lower, String upper) {
    this(dataType);
    this.lower = lower;
    this.upper = upper;
  }

  public RangeCategoryDescriptor(String dataType, String lower, String upper, String maxGranularity) {
    this(dataType, lower, upper);
    this.maxGranularity = maxGranularity;
  }

  /**
   * The name of the type for attributes of this category.
   */
  public String dataType;

  /**
   * The initial lower bound which will be used for top level queries.
   */
  public String lower;

  /**
   * @param The initial upper bound which will be used for top level queries.
   */
  public String upper;

  /**
   * The coarsest granularity that should be applied to a top level query.
   */
  public String maxGranularity;


  public String getMaxGranularity() {
    return maxGranularity;
  }

  public void setMaxGranularity(String maxGranularity) {
    this.maxGranularity = maxGranularity;
  }

  public String getDataType() {
    return dataType;
  }

  public void setDataType(String dataType) {
    this.dataType = dataType;
  }

  public String getLower() {
    return lower;
  }

  public void setLower(String lower) {
    this.lower = lower;
  }

  public String getUpper() {
    return upper;
  }

  public void setUpper(String upper) {
    this.upper = upper;
  }

  public void validate(String path) {
    ValidationUtil.requiredAndNotEmpty(path, "dataType", dataType);
    if (!dataType.equals("date") && !dataType.equals("datetime") && !dataType.equals("int")) {
      throw new ConfigValidationException(path, "dataType " + dataType + " is not valid. Must be one of [date,datetime,int]");
    }
  }

  @Override
  public void ensureOrderedOrNotAggregated(String path, String collationOrder) {
    if (collationOrder.equals(UnorderedCollationOrdering.name()) && maxGranularity != "individual") {
      throw new InvalidAggregationConfigurationException(path);
    }
  }

  @Override
  public boolean isSameType(CategoryDescriptor other) {
    return other instanceof RangeCategoryDescriptor && ((RangeCategoryDescriptor) other).dataType.equals(this.dataType);
  }

  @Override
  public boolean isRefinement(CategoryDescriptor other) {
    if (isSameType(other)) {
      RangeCategoryDescriptor otherDesc = (RangeCategoryDescriptor) other;

      if (dataType.equals("date")) {
        DateRangeConstraint constraint = (DateRangeConstraint) toConstraint("unknown");
        DateRangeConstraint otherConstraint = (DateRangeConstraint) otherDesc.toConstraint("unknown");
        return constraint.containsRange(otherConstraint.getStart(), otherConstraint.getEnd());
      } else if (dataType.equals("datetime")) {
        TimeRangeConstraint constraint = (TimeRangeConstraint) toConstraint("unknown");
        TimeRangeConstraint otherConstraint = (TimeRangeConstraint) otherDesc.toConstraint("unknown");
        return constraint.containsRange(otherConstraint.getStart(), otherConstraint.getEnd());
      } else if (dataType.equals("int")) {
        IntegerRangeConstraint constraint = (IntegerRangeConstraint) toConstraint("unknown");
        IntegerRangeConstraint otherConstraint = (IntegerRangeConstraint) otherDesc.toConstraint("unknown");
        return constraint.containsRange(otherConstraint.getStart(), otherConstraint.getEnd());
      }

      return true;
    }

    return false;
  }

  @Override
  public CategoryDescriptor applyRefinement(CategoryDescriptor refinement) {
    if (!isRefinement(refinement)) throw new IllegalArgumentException(refinement + " is not a refinement of " + this);
    RangeCategoryDescriptor refinedRange = (RangeCategoryDescriptor) refinement;

    return new RangeCategoryDescriptor(
      this.dataType,
      refinedRange.lower != null ? refinedRange.lower : this.lower,
      refinedRange.upper != null ? refinedRange.upper : this.upper,
      refinedRange.maxGranularity != null ? refinedRange.maxGranularity : this.maxGranularity);
  }

  public RangeConstraint toConstraint(String name) {
    if (dataType.equals("date")) {
      return new DateRangeConstraint(name, this.lower, this.upper);
    } else if (dataType.equals("datetime")) {
      return new TimeRangeConstraint(name, this.lower, this.upper);
    } else if (dataType.equals("int")) {
      return new IntegerRangeConstraint(name, this.lower, this.upper);
    } else {
      throw new IllegalArgumentException("Unknown data type " + this.dataType);
    }
  }

  @Override
  public void validateConstraint(ScanConstraint constraint) {
    if (dataType.equals("date")) {
      if (!(constraint instanceof DateRangeConstraint)) {
        throw new InvalidConstraintException(constraint.getAttributeName(),
          "Date Categories only support Date Range Constraints - provided constraint was " + constraint.getClass().getName());
      }

      DateRangeConstraint dConstraint = (DateRangeConstraint) constraint;
      DateRangeConstraint catConstraint = (DateRangeConstraint) toConstraint(constraint.getAttributeName());
      if (!catConstraint.containsRange(dConstraint.getStart(), dConstraint.getEnd())) {
        throw new InvalidConstraintException(constraint.getAttributeName(),
          dConstraint + " isn't contained within " + catConstraint);
      }
    } else if (dataType.equals("datetime")) {
      if (!(constraint instanceof TimeRangeConstraint)) {
        throw new InvalidConstraintException(constraint.getAttributeName(),
          "Date Categories only support Time Range Constraints - provided constraint was " + constraint.getClass().getName());
      }

      TimeRangeConstraint tConstraint = (TimeRangeConstraint) constraint;
      TimeRangeConstraint catConstraint = (TimeRangeConstraint) toConstraint(constraint.getAttributeName());
      if (!catConstraint.containsRange(tConstraint.getStart(), tConstraint.getEnd())) {
        throw new InvalidConstraintException(constraint.getAttributeName(),
          tConstraint + " isn't contained within " + catConstraint);
      }
    } else if (dataType.equals("int")) {
      if (!(constraint instanceof IntegerRangeConstraint)) {
        throw new InvalidConstraintException(constraint.getAttributeName(),
          "Integer Categories only support Integer Range Constraints - provided constraint was " + constraint.getClass().getName());
      }

      IntegerRangeConstraint iConstraint = (IntegerRangeConstraint) constraint;
      IntegerRangeConstraint catConstraint = (IntegerRangeConstraint) toConstraint(constraint.getAttributeName());
      if (!catConstraint.containsRange(iConstraint.getStart(), iConstraint.getEnd())) {
        throw new InvalidConstraintException(constraint.getAttributeName(),
          iConstraint + " isn't contained within " + catConstraint);
      }
    }
  }

  @Override
  public String toString() {
    return "RangeCategoryDescriptor{" +
      "dataType='" + dataType + '\'' +
      ", lower='" + lower + '\'' +
      ", upper='" + upper + '\'' +
      ", maxGranularity='" + maxGranularity + '\'' +
      '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RangeCategoryDescriptor that = (RangeCategoryDescriptor) o;

    if (dataType != null ? !dataType.equals(that.dataType) : that.dataType != null) return false;
    if (lower != null ? !lower.equals(that.lower) : that.lower != null) return false;
    if (maxGranularity != null ? !maxGranularity.equals(that.maxGranularity) : that.maxGranularity != null)
      return false;
    if (upper != null ? !upper.equals(that.upper) : that.upper != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = dataType != null ? dataType.hashCode() : 0;
    result = 31 * result + (lower != null ? lower.hashCode() : 0);
    result = 31 * result + (upper != null ? upper.hashCode() : 0);
    result = 31 * result + (maxGranularity != null ? maxGranularity.hashCode() : 0);
    return result;
  }
}
