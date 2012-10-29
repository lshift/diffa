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

import net.lshift.diffa.kernel.config.ConstraintGroupingTest.GroupExpectation
import org.junit.experimental.theories.{Theories, Theory, DataPoint}
import org.junit.runner.RunWith
import org.junit.Assert._
import scala.collection.JavaConversions._
import net.lshift.diffa.adapter.scanning.{SetConstraint, ScanConstraint}

@RunWith(classOf[Theories])
class ConstraintGroupingTest {

  @Theory
  def shouldGroupConstraintsForEndpoint(expectation:GroupExpectation) = {
    val endpoint = new Endpoint{categories = expectation.categories}
    assertEquals(expectation.grouped.map(s => s.toSet).toSet, endpoint.groupedConstraints(None).map(s => s.toSet).toSet)
  }
}

object ConstraintGroupingTest {
  case class GroupExpectation(categories:Map[String,AggregatingCategoryDescriptor], grouped:Seq[Seq[ScanConstraint]])

  @DataPoint def rangeOnly =
    GroupExpectation(
      categories = Map(
        "bizDate" -> new RangeCategoryDescriptor("datetime"),
        "someInt" -> new RangeCategoryDescriptor("int")
      ),
      grouped = Seq()
    )

  @DataPoint def setOnly =
    GroupExpectation(
      categories = Map(
        "someString" -> new SetCategoryDescriptor(Set("A","B"))
      ),
      grouped = Seq(
        Seq(new SetConstraint("someString", Set("A"))),
        Seq(new SetConstraint("someString", Set("B")))
      )
    )

  @DataPoint def multipleSets =
    GroupExpectation(
      categories = Map(
        "someString" -> new SetCategoryDescriptor(Set("A","B")),
        "someOtherString" -> new SetCategoryDescriptor(Set("1","2"))
      ),
      grouped = Seq(
        Seq(new SetConstraint("someString", Set("A")),new SetConstraint("someOtherString", Set("1"))),
        Seq(new SetConstraint("someString", Set("A")),new SetConstraint("someOtherString", Set("2"))),
        Seq(new SetConstraint("someString", Set("B")),new SetConstraint("someOtherString", Set("1"))),
        Seq(new SetConstraint("someString", Set("B")),new SetConstraint("someOtherString", Set("2")))
      )
    )

  @DataPoint def singleRangeAndSet =
    GroupExpectation(
      categories = Map(
        "bizDate" -> new RangeCategoryDescriptor("datetime"),
        "someString" -> new SetCategoryDescriptor(Set("A","B"))
      ),
      grouped = Seq(
        Seq(new SetConstraint("someString", Set("A"))),
        Seq(new SetConstraint("someString", Set("B")))
      )
    )

  @DataPoint def multipleRangesAndSets =
    GroupExpectation(
      categories = Map(
        "bizDate" -> new RangeCategoryDescriptor("datetime"),
        "someInt" -> new RangeCategoryDescriptor("int"),
        "someString" -> new SetCategoryDescriptor(Set("A","B")),
        "someOtherString" -> new SetCategoryDescriptor(Set("1","2"))
      ),
      grouped = Seq(
        Seq(new SetConstraint("someString", Set("A")),
            new SetConstraint("someOtherString", Set("1"))
        ),
        Seq(new SetConstraint("someString", Set("A")),
            new SetConstraint("someOtherString", Set("2"))
        ),
        Seq(new SetConstraint("someString", Set("B")),
            new SetConstraint("someOtherString", Set("1"))
        ),
        Seq(new SetConstraint("someString", Set("B")),
            new SetConstraint("someOtherString", Set("2"))
        )
      )
    )
}