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
import org.codehaus.jackson.annotate.JsonSubTypes;
import org.codehaus.jackson.annotate.JsonTypeInfo;

/**
 * This provides various endpoint-specific attributes of a category that are necessary for the kernel
 * to be able auto-narrow a category.
 */
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include= JsonTypeInfo.As.PROPERTY, property="@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = RangeCategoryDescriptor.class, name = "range"),
  @JsonSubTypes.Type(value = SetCategoryDescriptor.class, name = "set"),
  @JsonSubTypes.Type(value = PrefixCategoryDescriptor.class, name = "prefix")
})
abstract public class CategoryDescriptor {

  protected CategoryDescriptor() {
  }

  private int id;

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  /**
   * Requests that the descriptor ensure its content is valid. This method will be called when new descriptors
   * are received from clients.
   * @param path the path to this category descriptor, to be included in any validation errors.
   */
  public abstract void validate(String path);

  /**
   * Determines whether the given other category descriptor is a refinement of this category descriptor. This allows
   * for validation of views - ensuring that they don't specify configuration that isn't achievable.
   * @param other the other category descriptor to validate.
   * @return true - the provided other descriptor is a refinement; false - the other descriptor is outside the bounds of
   *      this descriptor.
   */
  public abstract boolean isRefinement(CategoryDescriptor other);

  /**
   * Applies the given other category descriptor as a refinement to this category descriptor. In many cases,
   * this will just be a case of returning the refinement descriptor. In other cases, configuration options from this
   * parent descriptor will be applied to the refinement (such as max-granularity on range descriptors) if not defined
   * in the refinement.
   * @param refinement the refinement to apply.
   * @return the refinement that is the union of the new options defined in the specified refinement and the options
   *  defined in this descriptor.
   * @throws RuntimeException if the provided descriptor isn't a refinement, as determined by this instance's
   *  <code>isRefinement</code> method.
   */
  public abstract CategoryDescriptor applyRefinement(CategoryDescriptor refinement);

  /**
   * Ensures that the given constraint is acceptable to this category.
   * @param constraint the constraint to validate.
   * @throws InvalidConstraintException if the constraint is invalid.
   */
  public abstract void validateConstraint(ScanConstraint constraint);
}
