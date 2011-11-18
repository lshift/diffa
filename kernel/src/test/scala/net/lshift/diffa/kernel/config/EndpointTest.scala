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

package net.lshift.diffa.kernel.config

import org.junit.Test
import org.junit.Assert._
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.differencing.{DateTimeAttribute, IntegerAttribute}
import org.junit.runner.RunWith
import org.junit.experimental.theories.{DataPoint, Theories, Theory, DataPoints}
import net.lshift.diffa.kernel.config.EndpointTest.ConstraintExpectation
import net.lshift.diffa.participant.scanning.{IntegerRangeConstraint, TimeRangeConstraint, DateRangeConstraint, ScanConstraint}
import org.joda.time.{DateTimeZone, LocalDate, DateTime}

/**
 * Test cases for the Endpoint class.
 */


@RunWith(classOf[Theories])
class EndpointTest {

  @Test
  def defaultConstraintsForEndpointWithNoCategories = {
    val ep = new Endpoint()
    assertEquals(Seq(), ep.initialConstraints(None))
    assertEquals(Seq(), ep.groupedConstraints(None))
  }

  @Theory
  def shouldBuildConstraintsForEndpoint(expectation:ConstraintExpectation) = {
    val ep = new Endpoint(categories=Map(expectation.name -> expectation.descriptor))
    if (expectation.constraint != null)
      assertEquals(Seq(expectation.constraint), ep.initialConstraints(None))
    else
      assertEquals(Seq(), ep.initialConstraints(None))
  }

  @Test
  def schematize() = {
    val unboundDateCategoryDescriptor = new RangeCategoryDescriptor("datetime")
    val unboundIntCategoryDescriptor = new RangeCategoryDescriptor("int")

    val categoryMap = Map("xyz_attribute" -> unboundIntCategoryDescriptor,
                          "abc_attribute" -> unboundDateCategoryDescriptor,
                          "def_attribute" -> unboundDateCategoryDescriptor)

    val untyped = Map("abc_attribute" -> "2011-01-26T10:24:00.000Z",
                      "def_attribute" -> "2011-01-26T10:36:00.000Z",
                      "xyz_attribute" -> "55")

    val schematized = Map("xyz_attribute" -> IntegerAttribute(55),
                          "abc_attribute" -> DateTimeAttribute(new DateTime(2011, 1, 26, 10, 24, 0, 0, DateTimeZone.UTC)),
                          "def_attribute" -> DateTimeAttribute(new DateTime(2011, 1, 26, 10, 36, 0, 0, DateTimeZone.UTC)))

    var ep = new Endpoint{categories = categoryMap}
    assertEquals(schematized, ep.schematize(untyped))
  }
}

object EndpointTest {

  case class ConstraintExpectation(name:String, descriptor:RangeCategoryDescriptor, constraint:ScanConstraint)

  @DataPoints def unbounded =
    Array(
      ConstraintExpectation("bizDateTime", new RangeCategoryDescriptor("datetime"), null),
      ConstraintExpectation("someInt", new RangeCategoryDescriptor("int"), null)
   )

  @DataPoints def bounded =
    Array(
      ConstraintExpectation("bizDateTime",
        new RangeCategoryDescriptor("datetime", "2011-01-01", "2011-01-31"),
        new TimeRangeConstraint("bizDateTime", new DateTime(2011,1,1,0,0,0,0, DateTimeZone.UTC), new DateTime(2011,1,31,23,59,59,999, DateTimeZone.UTC))),
      ConstraintExpectation("bizDateTime",
        new RangeCategoryDescriptor("datetime", "1998-11-21T14:29:53.894Z", "1998-11-29T22:08:31.637Z"),
        new TimeRangeConstraint("bizDateTime", new DateTime(1998,11,21,14,29,53,894, DateTimeZone.UTC), new DateTime(1998,11,29,22,8,31,637, DateTimeZone.UTC))),
      ConstraintExpectation("bizDate",
        new RangeCategoryDescriptor("date", "1992-10-19", "1992-10-22"),
        new DateRangeConstraint("bizDate", new LocalDate(1992,10,19), new LocalDate(1992,10,22))),
      ConstraintExpectation("someInt",
        new RangeCategoryDescriptor("int", "0", "9"),
        new IntegerRangeConstraint("someInt", 0, 9))
   )
}
