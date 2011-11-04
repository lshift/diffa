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

package net.lshift.diffa.kernel.participants

import org.junit.Test
import org.junit.Assert._

import net.lshift.diffa.kernel.participants.IntegerCategoryFunction._
import net.lshift.diffa.participant.scanning.{InvalidAttributeValueException, IntegerRangeConstraint}

class IntegerPartitionTest {

  protected val tens = IntegerCategoryFunction("someInt", 10, 10)
  protected val hundreds = IntegerCategoryFunction("someInt", 100, 10)

  @Test
  def tensPartition {
    assertEquals("120", tens.bucket("123"))
    assertEquals("10", tens.bucket("12"))
    assertEquals("0", tens.bucket("1"))
  }

  @Test
  def hundredsPartition {
    assertEquals("1200", hundreds.bucket("1234"))
    assertEquals("100", hundreds.bucket("123"))
    assertEquals("0", hundreds.bucket("12"))
    assertEquals("0", hundreds.bucket("1"))
  }

  @Test
  def autoDescendingIntegerCategoryFunction {
    def binaryCategoryFunction(denom: Int) = IntegerCategoryFunction("someInt", denom, 2)
    val myBinaryCategoryFunction = binaryCategoryFunction(128)
    assertEquals("256", myBinaryCategoryFunction.bucket("300"))
    assertEquals(new IntegerRangeConstraint("someInt", 256, 383),
                 myBinaryCategoryFunction.constrain(None, "256"))
    assertEquals(Some(binaryCategoryFunction(64)), myBinaryCategoryFunction.descend)
  }

  @Test
  def halfOpenDateRangeShouldOnlyAcceptValuesGreaterThanTheDefinedBound = {
    val unboundedUpper = new IntegerRangeConstraint("someInt", 100, null)
    assertTrue(unboundedUpper.contains(101))
    assertFalse(unboundedUpper.contains(99))
  }

  @Test
  def halfOpenDateRangeShouldOnlyAcceptValuesLessThanTheDefinedBound = {
    val unboundedLower = new IntegerRangeConstraint("someInt", null, 100)
    assertTrue(unboundedLower.contains(99))
    assertFalse(unboundedLower.contains(101))
  }

  @Test
  def completelyUnboundDateRangeShouldAcceptAnyValue = {
    val unbounded = new IntegerRangeConstraint("someInt", null.asInstanceOf[java.lang.Integer], null)
    assertTrue(unbounded.contains(100))
    val unboundedAsString = new IntegerRangeConstraint("someInt", null.asInstanceOf[String], null)
    assertTrue(unboundedAsString.contains(100))
  }

  @Test
  def descendFromTensPartition {
    assertEquals(None, tens.descend)
    assertEquals(new IntegerRangeConstraint("someInt", 10, 19), tens.constrain(None, "10"))
  }

  @Test
  def descendFromHundredsPartition {
    assertEquals(Some(tens), hundreds.descend)
    assertEquals(new IntegerRangeConstraint("someInt", 100, 199), hundreds.constrain(None, "100"))
  }

  @Test(expected=classOf[InvalidAttributeValueException])
  def shouldThrowInvalidCategoryExceptionIfValueIsNotInteger {
    tens.owningPartition("NOT_AN_INTEGER")
  }

  @Test(expected=classOf[InvalidAttributeValueException])
  def descendShouldThrowInvalidAttributeValueExceptionIfPartitionValueIsInvalid {
    tens.constrain(None, "123")
  }

  @Test(expected=classOf[IllegalArgumentException])
  def autoDescendingIntegerCategoryShouldThrowIllegalArgumentExceptionIfInstantiatedWithInvalidArgs {
    new IntegerCategoryFunction("someInt", 100, 3) // 3 is not a factor of 100
  }

}
