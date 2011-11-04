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
package net.lshift.diffa.participant.scanning;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimeRangeConstraintTest {
  @Test
  public void shouldSupportDateStringAsProvidedValueAndWidenItOnEnd() {
    TimeRangeConstraint constraint = new TimeRangeConstraint("someTime", "2011-11-04", "2011-11-05");
    assertEquals(new DateTime(2011, 11, 4, 0, 0, 0, 0, DateTimeZone.UTC), constraint.getStart());
    assertEquals(new DateTime(2011, 11, 5, 23, 59, 59, 999, DateTimeZone.UTC), constraint.getEnd());
  }

  @Test
  public void shouldSupportTimeStringAsProvidedValue() {
    TimeRangeConstraint constraint = new TimeRangeConstraint("someTime", "2011-11-04T12:13:14.000Z", "2011-11-05T13:14:15.000Z");
    assertEquals(new DateTime(2011, 11, 4, 12, 13, 14, 0, DateTimeZone.UTC), constraint.getStart());
    assertEquals(new DateTime(2011, 11, 5, 13, 14, 15, 0, DateTimeZone.UTC), constraint.getEnd());
  }
  
  @Test
  public void shouldSupportNullStringAsProvidedValue() {
    TimeRangeConstraint constraint = new TimeRangeConstraint("someTime", (String) null, (String) null);
    assertEquals(null, constraint.getStart());
    assertEquals(null, constraint.getEnd());
  }

  @Test
  public void shouldSupportContainsCheckForBoundsOnBothSides() {
    TimeRangeConstraint constraint = new TimeRangeConstraint("someTime",
      new DateTime(2011, 1, 1, 5, 15, 21, 0, DateTimeZone.UTC),
      new DateTime(2011, 12, 31, 15, 12, 54, 999, DateTimeZone.UTC));

    assertFalse(constraint.contains(new DateTime(2010, 10, 10, 5, 6, 7, 0, DateTimeZone.UTC)));
    assertFalse(constraint.contains(new DateTime(2010, 12, 31, 23, 59, 59, 999, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 1, 1, 5, 15, 21, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 5, 12, 6, 7, 11, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 12, 31, 15, 12, 54, 999, DateTimeZone.UTC)));
    assertFalse(constraint.contains(new DateTime(2011, 12, 31, 15, 12, 55, 0, DateTimeZone.UTC)));
    assertFalse(constraint.contains(new DateTime(2013, 5, 6, 6, 15, 24, 0, DateTimeZone.UTC)));
  }

  @Test
  public void shouldSupportContainsCheckForBoundsOnTopSideOnly() {
    TimeRangeConstraint constraint = new TimeRangeConstraint("someTime",
      null, new DateTime(2011, 12, 31, 15, 12, 54, 999, DateTimeZone.UTC));

    assertTrue(constraint.contains(new DateTime(2010, 10, 10, 5, 6, 7, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2010, 12, 31, 23, 59, 59, 999, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 1, 1, 5, 15, 21, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 5, 12, 6, 7, 11, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 12, 31, 15, 12, 54, 999, DateTimeZone.UTC)));
    assertFalse(constraint.contains(new DateTime(2011, 12, 31, 15, 12, 55, 0, DateTimeZone.UTC)));
    assertFalse(constraint.contains(new DateTime(2013, 5, 6, 6, 15, 24, 0, DateTimeZone.UTC)));
  }

  @Test
  public void shouldSupportContainsCheckForBoundsOnBottomSideOnly() {
    TimeRangeConstraint constraint = new TimeRangeConstraint("someTime",
      new DateTime(2011, 1, 1, 5, 15, 21, 0, DateTimeZone.UTC),
      null);

    assertFalse(constraint.contains(new DateTime(2010, 10, 10, 5, 6, 7, 0, DateTimeZone.UTC)));
    assertFalse(constraint.contains(new DateTime(2010, 12, 31, 23, 59, 59, 999, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 1, 1, 5, 15, 21, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 5, 12, 6, 7, 11, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 12, 31, 15, 12, 54, 999, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 12, 31, 15, 12, 55, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2013, 5, 6, 6, 15, 24, 0, DateTimeZone.UTC)));
  }

  @Test
  public void shouldSupportContainsCheckForBoundsOnNoSides() {
    TimeRangeConstraint constraint = new TimeRangeConstraint("someTime", (DateTime) null, null);

    assertTrue(constraint.contains(new DateTime(2010, 10, 10, 5, 6, 7, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2010, 12, 31, 23, 59, 59, 999, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 1, 1, 5, 15, 21, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 5, 12, 6, 7, 11, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 12, 31, 15, 12, 54, 999, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2011, 12, 31, 15, 12, 55, 0, DateTimeZone.UTC)));
    assertTrue(constraint.contains(new DateTime(2013, 5, 6, 6, 15, 24, 0, DateTimeZone.UTC)));
  }
}
