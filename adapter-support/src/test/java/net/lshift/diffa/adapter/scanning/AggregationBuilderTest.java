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
package net.lshift.diffa.adapter.scanning;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.TreeSet;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Test cases for the AggregationBuilder.
 */
public class AggregationBuilderTest {
  @Test
  public void shouldNotAddDateAggregationForEmptyRequest() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    AggregationBuilder builder = new AggregationBuilder(req);

    builder.maybeAddDateAggregation("test");
    assertEquals(0, builder.toList().size());
  }

  @Test
  public void shouldAddDateAggregationWhenParameterIsAvailable() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addParameter("bizDate-granularity", "monthly");
    req.addParameter("bizDate-parent", "2009");
    AggregationBuilder builder = new AggregationBuilder(req);

    builder.maybeAddDateAggregation("bizDate");
    assertEquals(1, builder.toList().size());
    assertThat(builder.toList().get(0), is(instanceOf(DateAggregation.class)));
  }

  @Test
  public void shouldNotAddDateAggregationWhenDifferentParameterIsAvailable() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addParameter("someString-granularity", "prefix(1)");
    AggregationBuilder builder = new AggregationBuilder(req);

    builder.maybeAddDateAggregation("bizDate");
    assertEquals(0, builder.toList().size());
  }

  @Test
  public void shouldNotAddNyNameAggregationForEmptyRequest() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    AggregationBuilder builder = new AggregationBuilder(req);

    builder.maybeAddByNameAggregation("test");
    assertEquals(0, builder.toList().size());
  }

  @Test
  public void shouldAddByNameAggregationWhenParameterIsAvailable() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addParameter("someString-granularity", "by-name");
    AggregationBuilder builder = new AggregationBuilder(req);

    builder.maybeAddByNameAggregation("someString");
    assertEquals(1, builder.toList().size());
    assertThat(builder.toList().get(0), is(instanceOf(ByNameAggregation.class)));
  }

  @Test
  public void shouldNotAddIntegerAggregationForEmptyRequest() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    AggregationBuilder builder = new AggregationBuilder(req);

    builder.maybeAddIntegerAggregation("test");
    assertEquals(0, builder.toList().size());
  }

  @Test
  public void shouldAddIntegerAggregationWhenParameterIsAvailable() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addParameter("someInt-granularity", "1000s");
    AggregationBuilder builder = new AggregationBuilder(req);

    builder.maybeAddIntegerAggregation("someInt");
    assertEquals(1, builder.toList().size());
    assertThat(builder.toList().get(0), is(instanceOf(IntegerAggregation.class)));

    IntegerAggregation a = (IntegerAggregation) builder.toList().get(0);
    assertEquals("someInt", a.getAttributeName());
    assertEquals(1000, a.getGranularity());
  }

  @Test
  public void shouldBucketInteger() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addParameter("someInt-granularity", "100s");
    AggregationBuilder builder = new AggregationBuilder(req);
    builder.maybeAddIntegerAggregation("someInt");

    IntegerAggregation a = (IntegerAggregation) builder.toList().get(0);
    assertEquals("500", a.bucket("523"));
  }

  @Test
  public void shouldNotAddStringPrefixAggregationForEmptyRequest() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    AggregationBuilder builder = new AggregationBuilder(req);

    builder.maybeAddStringPrefixAggregation("test");
    assertEquals(0, builder.toList().size());
  }

  @Test
  public void shouldAddStringPrefixAggregationWhenParameterIsAvailable() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addParameter("someString-offset", "3");
    req.addParameter("someString-offset", "5");
    req.addParameter("someString-offset", "7");
    AggregationBuilder builder = new AggregationBuilder(req);

    builder.maybeAddStringPrefixAggregation("someString");
    assertEquals(1, builder.toList().size());
    assertThat(builder.toList().get(0), is(instanceOf(StringPrefixAggregation.class)));

    StringPrefixAggregation a = (StringPrefixAggregation) builder.toList().get(0);
    assertEquals("someString", a.getAttributeName());

    assertEquals(new TreeSet<Integer>(Arrays.asList(3,5,7)), a.getOffsets());
  }

  @Test
  public void shouldBucketStringPrefix() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addParameter("someString-offset", "4");
    AggregationBuilder builder = new AggregationBuilder(req);
    builder.maybeAddStringPrefixAggregation("someString");

    StringPrefixAggregation a = (StringPrefixAggregation) builder.toList().get(0);
    assertEquals("abcd", a.bucket("abcdef"));
  }

  @Test
  public void shouldBucketStringPrefixWithShortString() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addParameter("someString-offset", "3");
    AggregationBuilder builder = new AggregationBuilder(req);
    builder.maybeAddStringPrefixAggregation("someString");

    StringPrefixAggregation a = (StringPrefixAggregation) builder.toList().get(0);
    assertEquals("ab", a.bucket("ab"));
  }
}
