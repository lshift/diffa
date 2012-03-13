/**
 * Copyright (C) 2012 LShift Ltd.
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
package net.lshift.diffa.kernel.config

import org.junit.Test
import net.lshift.diffa.kernel.frontend.DefValidationTestBase
import java.util.HashSet

class CategoryDescriptorValidationTest extends DefValidationTestBase {
  @Test
  def shouldRejectRangeDescriptorWithNullDataType() {
    validateError(new RangeCategoryDescriptor(), "config: dataType cannot be null or empty")
  }
  @Test
  def shouldRejectRangeDescriptorWithEmptyDataType() {
    validateError(new RangeCategoryDescriptor(""), "config: dataType cannot be null or empty")
  }
  @Test
  def shouldRejectRangeDescriptorWithInvalidDataType() {
    validateError(new RangeCategoryDescriptor("foo"), "config: dataType foo is not valid. Must be one of [date,datetime,int]")
  }
  @Test
  def shouldAcceptRangeDescriptorWithValidDataTypes() {
    validateAcceptsAll(
      Seq("date", "datetime", "int"),
      dt => new RangeCategoryDescriptor(dt))
  }

  @Test
  def shouldRejectSetDescriptorWithNullValues() {
    validateError(new SetCategoryDescriptor(), "config: Set Category must have at least one element")
  }
  @Test
  def shouldRejectSetDescriptorWithEmptyValues() {
    validateError(new SetCategoryDescriptor(new HashSet[String]()), "config: Set Category must have at least one element")
  }

  @Test
  def shouldRejectPrefixDescriptorWithMaximumShorterThanStart() {
    validateError(new PrefixCategoryDescriptor(5, 4, 1), "config: maximum must be equal to or larger than initial prefix length")
  }
  @Test
  def shouldAcceptPrefixDescriptorWithInitialLengthOf0() {
    new PrefixCategoryDescriptor(0, 1, 1).validate("config")
  }
  @Test
  def shouldRejectPrefixDescriptorWithInitialLengthLessThan0() {
    validateError(new PrefixCategoryDescriptor(-1, 5, 1), "config: length cannot be negative")
  }
  @Test
  def shouldRejectPrefixDescriptorWithMaximumLessThan1() {
    validateError(new PrefixCategoryDescriptor(0, 0, 1), "config: maximum must be at least 1")
  }
  @Test
  def shouldRejectPrefixDescriptorWithStepLessThan1() {
    validateError(new PrefixCategoryDescriptor(1, 5, 0), "config: step must be at least 1")
    validateError(new PrefixCategoryDescriptor(1, 5, -1), "config: step must be at least 1")
  }
}