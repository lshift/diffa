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

import org.apache.commons.codec.digest.DigestUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Test cases for the digest builder.
 */

@RunWith(Theories.class)
public class DigestBuilderTest {
  private static final ScanAggregation bizDateAggregation =
      new DateAggregation("bizDate", DateGranularityEnum.Daily, "2009-06");
  private static final ScanAggregation someStringAggregation =
      new ByNameAggregation("someString", null);
  private static final List<ScanAggregation> aggregations = Arrays.asList(
      bizDateAggregation, someStringAggregation);

  private static final DateTime JUN_6_2009_1 = new DateTime(2009, 6, 6, 12, 45, 12, 0, DateTimeZone.UTC);
  private static final DateTime JUN_6_2009_2 = new DateTime(2009, 6, 6, 15, 32, 16, 0, DateTimeZone.UTC);
  private static final DateTime JUN_7_2009_1 = new DateTime(2009, 6, 7, 13, 51, 31, 0, DateTimeZone.UTC);

  @Test
  public void shouldReturnEmptyDigestsForNoInput() {
    DigestBuilder builder = new DigestBuilder(aggregations);
    assertEquals(0, builder.toDigests().size());
  }

  @Test
  public void shouldObserveAllAggregationFactors() {
    DigestBuilder builder = new DigestBuilder(aggregations);

    builder.add("id1", createAttrMap(JUN_6_2009_1, "a"), "vsn1");
    builder.add("id2", createAttrMap(JUN_7_2009_1, "b"), "vsn2");
    builder.add("id3", createAttrMap(JUN_6_2009_2, "c"), "vsn3");
    builder.add("id4", createAttrMap(JUN_6_2009_2, "a"), "vsn4");

    assertEquals(
      new HashSet<ScanResultEntry>(Arrays.asList(
        ScanResultEntry.forAggregate(DigestUtils.md5Hex("vsn1" + "vsn4"), createAttrMap("2009-06-06", "a")),
        ScanResultEntry.forAggregate(DigestUtils.md5Hex("vsn2"), createAttrMap("2009-06-07", "b")),
        ScanResultEntry.forAggregate(DigestUtils.md5Hex("vsn3"), createAttrMap("2009-06-06", "c"))
      )),
      new HashSet<ScanResultEntry>(builder.toDigests()));
  }

  @Test
  public void shouldObserveAttributesThatArentAggregationFactors() {
    DigestBuilder builder = new DigestBuilder(Arrays.asList(bizDateAggregation));

    builder.add("id1", createAttrMap(JUN_6_2009_1, "a"), "vsn1");
    builder.add("id2", createAttrMap(JUN_7_2009_1, "b"), "vsn2");
    builder.add("id3", createAttrMap(JUN_6_2009_2, "c"), "vsn3");
    builder.add("id4", createAttrMap(JUN_6_2009_2, "a"), "vsn4");

    assertEquals(
      new HashSet<ScanResultEntry>(Arrays.asList(
        ScanResultEntry.forAggregate(DigestUtils.md5Hex("vsn1" + "vsn4"), createAttrMap("2009-06-06", "a")),
        ScanResultEntry.forAggregate(DigestUtils.md5Hex("vsn2"), createAttrMap("2009-06-07", "b")),
        ScanResultEntry.forAggregate(DigestUtils.md5Hex("vsn3"), createAttrMap("2009-06-06", "c"))
      )),
      new HashSet<ScanResultEntry>(builder.toDigests()));
  }

  /**
   * The idea behind this is that a bucket should be immutable after it
   * has been digested, hence adding a new item to the same bucket
   * after it has been digested should result in an error.
   */
  @Test
  public void bucketsShouldBeSealedAfterQuery() {
    DigestBuilder builder = new DigestBuilder(aggregations);
    builder.add("id0", createAttrMap(JUN_6_2009_1, "a"), "vsn0");

    builder.toDigests();

    try {
      builder.add("id1", createAttrMap(JUN_6_2009_1, "a"), "vsn1");
      fail("Expected to provoke SealedBucketException");
    } catch (SealedBucketException e) {
    }
  }

  @Test
  public void shouldAddViaScanResultEntries() {
    DigestBuilder builder = new DigestBuilder(aggregations);

    builder.add(ScanResultEntry.forEntity("id1", "vsn1", null, createAttrMap(JUN_6_2009_1, "a")));
    builder.add(ScanResultEntry.forEntity("id2", "vsn2", null, createAttrMap(JUN_7_2009_1, "b")));
    builder.add(ScanResultEntry.forEntity("id3", "vsn3", null, createAttrMap(JUN_6_2009_2, "c")));
    builder.add(ScanResultEntry.forEntity("id4", "vsn4", null, createAttrMap(JUN_6_2009_2, "a")));

    assertEquals(
      new HashSet<ScanResultEntry>(Arrays.asList(
        ScanResultEntry.forAggregate(DigestUtils.md5Hex("vsn1" + "vsn4"), createAttrMap("2009-06-06", "a")),
        ScanResultEntry.forAggregate(DigestUtils.md5Hex("vsn2"), createAttrMap("2009-06-07", "b")),
        ScanResultEntry.forAggregate(DigestUtils.md5Hex("vsn3"), createAttrMap("2009-06-06", "c"))
      )),
      new HashSet<ScanResultEntry>(builder.toDigests()));
  }

  @Test(expected = OutOfOrderException.class)
  public void shouldRejectOutOfOrderIds() {
    DigestBuilder builder = new DigestBuilder(aggregations);

    builder.add(ScanResultEntry.forEntity("id2", "vsn2", null, createAttrMap(JUN_7_2009_1, "b")));
    builder.add(ScanResultEntry.forEntity("id1", "vsn1", null, createAttrMap(JUN_6_2009_1, "a")));

  }


  static class Scenario {
      final Collation collation;
      final List<String> okaySequence;
      final List<String> failingSequence;

      Scenario(Collation coll, List<String> okaySequence, List<String> failingSequence) {
          this.collation = coll;
          this.okaySequence = okaySequence;
          this.failingSequence = failingSequence;
      }
  }

  // Create a collator which supports the Unicode Collation Algorithm; see either
  // http://unicode.org/reports/tr10/ if you want all of the detail, or
  // http://wiki.apache.org/couchdb/View_collation for the practical ramifications of this.


  @DataPoint public static Scenario unicode = new Scenario(
        new UnicodeCollation(),
        Arrays.asList("bar", "Foo"),
        Arrays.asList("Far", "boo" )
  );

  @DataPoint public static Scenario binary = new Scenario(
          new AsciiCollation(),
          Arrays.asList("Bar", "Foo"),
          Arrays.asList("far", "boo" ));


  @Theory
  public void shouldAcceptSpecifiedCollationOrdering(Scenario ex) {
    DigestBuilder builder = new DigestBuilder(aggregations, ex.collation);

    for (String id: ex.okaySequence) {
      builder.add(ScanResultEntry.forEntity(id, "vsn" + id, null, createAttrMap(JUN_7_2009_1, "b")));
    }
  }

  @Theory
  public void shouldRejectInvalidOrderWithSpecifiedCollationOrdering(Scenario ex) {
    DigestBuilder builder = new DigestBuilder(aggregations, ex.collation);

    try {
      for (String id: ex.failingSequence) {
        builder.add(ScanResultEntry.forEntity(id, "vsn" + id, null, createAttrMap(JUN_7_2009_1, "b")));
      }
      fail(String.format("Dis-ordered insertion of %s with collation %s should throw Exception",
              ex.failingSequence, ex.collation));
    } catch (Throwable t) {
      // Pass  \o/
    }
  }

    private static Map<String, String> createAttrMap(DateTime bizDate, String ss) {
    return createAttrMap(bizDate.toString(), ss);
  }
  private static Map<String, String> createAttrMap(String bizDate, String ss) {
    Map<String, String> attrs = new HashMap<String, String>();
    attrs.put("bizDate", bizDate.toString());
    attrs.put("someString", ss);
    return attrs;
  }
}
