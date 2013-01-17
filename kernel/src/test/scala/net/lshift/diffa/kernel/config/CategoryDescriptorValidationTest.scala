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
import net.lshift.diffa.config.{PrefixCategoryDescriptor, SetCategoryDescriptor, RangeCategoryDescriptor}

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
    validateErrorOnConstruction(() => new PrefixCategoryDescriptor(5, 4, 1), "Offsets were not ascending: [5, 4, 1]")
  }

  @Test
  def shouldRejectPrefixDescriptorWithOffsetLessThan1() {
    validateError(new PrefixCategoryDescriptor(0, 1, 2), "config: offset cannot be less than 1: [0, 1, 2]")
    validateError(new PrefixCategoryDescriptor(-6, 4, 6), "config: offset cannot be less than 1: [-6, 4, 6]")
  }

}