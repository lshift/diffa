/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.client

import org.junit.runner.RunWith
import org.junit.experimental.theories.{DataPoint, Theory, Theories}
import org.junit.Test
import net.lshift.diffa.adapter.scanning.{TimeRangeConstraint, StringPrefixConstraint}
import org.junit.Assert._
import org.hamcrest.CoreMatchers._
import net.lshift.diffa.kernel.participants.StringPrefixCategoryFunction
import java.net.URI
import org.joda.time.{DateTimeZone, Duration, ReadableDuration, DateTime}

@RunWith(classOf[Theories])
class DiffaHttpQueryTest {
  import DiffaHttpQueryTest._

  val dummyQuery = DiffaHttpQuery("http://dummy/")

  @Test
  def withConstraints() {
    val constraints = Seq(new StringPrefixConstraint("property", "thePrefix"))
    assertThat(
      dummyQuery.withConstraints(constraints).query,
      equalTo(Map("property-prefix" -> Seq("thePrefix"))))
  }

  @Test
  def withTimeRangeConstraints() {

    val start = new DateTime(2012, 7, 4, 16, 47, DateTimeZone.UTC)
    val end = start.plus(3600 * 1000)
    val constraints = Seq(new TimeRangeConstraint("property", start, end))
    val expectedQueryParameters = Map("property-start" -> Seq("2012-07-04T16:47:00.000Z"), "property-end" -> Seq("2012-07-04T17:47:00.000Z"))
    var withConstraints = dummyQuery.withConstraints(constraints)
    assertThat(
      withConstraints.query, equalTo(expectedQueryParameters))
    assertThat(withConstraints.fullUri, equalTo(
      new URI("http://dummy/?property-start=2012-07-04T16%3A47%3A00.000Z&property-end=2012-07-04T17%3A47%3A00.000Z")))
  }


  @Test
  def withAggregations {
    val aggregates = Seq(new StringPrefixCategoryFunction("property", 1, 2, 3))
    assertThat(
      dummyQuery.withAggregations(aggregates).query,
      equalTo(Map("property-length" -> Seq("1"))))

  }


  @Theory
  def verifyRequestUri(ex: Example) {

    var query = DiffaHttpQuery(ex.requestUrl).withQuery(ex.query)
    assertThat(query.fullUri, equalTo(ex.expected))
  }

}

object DiffaHttpQueryTest {

  case class Example(requestUrl: String,
                     query: Map[String, Seq[String]],
                     expected: URI)
  @DataPoint def nullExample = Example("/", Map(), new URI("/"))

  @DataPoint def simpleExample = Example("/", Map("dummy" -> Seq("value")), new URI("/?dummy=value"))

  @DataPoint def baseWithAuth = Example("/?auth=dummy", Map(), new URI("/?auth=dummy"))
  @DataPoint def baseWithQueryParamIncludingDangerousCharacters =
    Example("/", Map("colon" -> Seq("Mr :")), new URI("/?colon=Mr+%3a"))


  @DataPoint def baseWithAuthPlusQuery = Example(
    "/?auth=dummy", Map("query" -> Seq("value")), new URI("/?auth=dummy&query=value"))

}