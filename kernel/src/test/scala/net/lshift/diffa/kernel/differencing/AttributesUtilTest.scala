/**
 * Copyright (C) 2011 LShift Ltd.
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
package net.lshift.diffa.kernel.differencing

import org.junit.Test
import org.junit.Assert._
import net.lshift.diffa.kernel.config.{SetCategoryDescriptor, RangeCategoryDescriptor, PrefixCategoryDescriptor}
import scala.collection.JavaConversions._
import net.lshift.diffa.participant.scanning._
import org.joda.time.{DateTimeZone, LocalDate, DateTime}

class AttributesUtilTest {
  val categories = Map(
    "someString" -> new PrefixCategoryDescriptor(5, 1, 1),
    "someDate"   -> new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31"),
    "someTime"   -> new RangeCategoryDescriptor("datetime", "2011-01-01T10:15:15.000Z", "2011-01-05T11:16:16.000Z"),
    "someInt"    -> new RangeCategoryDescriptor("int", "5", "12"),
    "someSet"    -> new SetCategoryDescriptor(Set("a", "b", "z"))
  )
  val constraints = Seq(
    new StringPrefixConstraint("someString", "abcdef"),
    new DateRangeConstraint("someDate", new LocalDate(2011, 1, 1), new LocalDate(2011, 12, 31)),
    new TimeRangeConstraint("someTime",
      new DateTime(2011, 1, 1, 10, 15, 15, DateTimeZone.UTC), new DateTime(2011, 12, 31, 11, 16, 16, DateTimeZone.UTC)),
    new IntegerRangeConstraint("someInt", 5, 12),
    new SetConstraint("someSet", Set("a", "b", "z"))
  )
  val allAttributes = Map(
    "someString" -> "abcdefg",
    "someDate"   -> "2011-05-01",
    "someTime"   -> "2011-01-02T09:11:21.000Z",
    "someInt"    -> "8",
    "someSet"    -> "b"
  )
  val allWrongAttributes = Map(
    "someString" -> "gadadads",
    "someDate"   -> "2012-05-01",
    "someTime"   -> "2012-01-02T09:11:21.000Z",
    "someInt"    -> "42",
    "someSet"    -> "c"
  )

  @Test
  def shouldAllowMissingAttributesOnToTypedMapOfUntypedMap() {
    val typed = AttributesUtil.toTypedMap(categories, Map("someString" -> "aaa"))
    assertEquals(Map("someString" -> StringAttribute("aaa")), typed)
  }

  @Test
  def shouldNotDetectProblemsWhenThereAreNoIssues() {
    val result = AttributesUtil.detectAttributeIssues(categories, constraints, allAttributes)
    assertEquals(result, Map[String, String]())
  }

  @Test
  def shouldComplainAboutTooManyAttributes() {
    val result = AttributesUtil.detectAttributeIssues(categories, constraints, allAttributes ++ Map("extra" -> "abc"))
    assertEquals(result, Map("extra" -> "no matching category defined"))
  }

  @Test
  def shouldDetectMissingAttributes() {
    val result = AttributesUtil.detectAttributeIssues(categories, constraints, allAttributes -- Seq("someString", "someInt"))
    assertEquals(result, Map("someString" -> "property is missing", "someInt" -> "property is missing"))
  }

  @Test
  def shouldDetectOutOfConstraintAttributes() {
    val result = AttributesUtil.detectAttributeIssues(categories, constraints, allWrongAttributes)
    assertEquals(result, Map(
      "someString" -> "gadadads does not have the prefix abcdef",
      "someDate" -> "2012-05-01 is not in range 2011-01-01 -> 2011-12-31",
      "someTime" -> "2012-01-02T09:11:21.000Z is not in range 2011-01-01T10:15:15.000Z -> 2011-12-31T11:16:16.000Z",
      "someInt" -> "42 is not in range 5 -> 12",
      "someSet" -> "c is not a member of Set(a, b, z)"
    ))
  }
}