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

import org.joda.time.LocalDate;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DateRangeConstraintTest {
  @Test
  public void shouldSupportContainsCheckForBoundsOnBothSides() {
    DateRangeConstraint constraint = new DateRangeConstraint("someDate", new LocalDate(2011, 1, 1), new LocalDate(2011, 12, 31));

    assertFalse(constraint.contains(new LocalDate(2010, 10, 10)));
    assertFalse(constraint.contains(new LocalDate(2010, 12, 31)));
    assertTrue(constraint.contains(new LocalDate(2011, 1, 1)));
    assertTrue(constraint.contains(new LocalDate(2011, 5, 12)));
    assertTrue(constraint.contains(new LocalDate(2011, 12, 31)));
    assertFalse(constraint.contains(new LocalDate(2012, 1, 1)));
    assertFalse(constraint.contains(new LocalDate(2013, 5, 6)));
  }

  @Test
  public void shouldSupportContainsCheckForBoundsOnTopSideOnly() {
    DateRangeConstraint constraint = new DateRangeConstraint("someDate", null, new LocalDate(2011, 12, 31));

    assertTrue(constraint.contains(new LocalDate(2010, 10, 10)));
    assertTrue(constraint.contains(new LocalDate(2010, 12, 31)));
    assertTrue(constraint.contains(new LocalDate(2011, 1, 1)));
    assertTrue(constraint.contains(new LocalDate(2011, 5, 12)));
    assertTrue(constraint.contains(new LocalDate(2011, 12, 31)));
    assertFalse(constraint.contains(new LocalDate(2012, 1, 1)));
    assertFalse(constraint.contains(new LocalDate(2013, 5, 6)));
  }

  @Test
  public void shouldSupportContainsCheckForBoundsOnBottomSideOnly() {
    DateRangeConstraint constraint = new DateRangeConstraint("someDate", new LocalDate(2011, 1, 1), null);

    assertFalse(constraint.contains(new LocalDate(2010, 10, 10)));
    assertFalse(constraint.contains(new LocalDate(2010, 12, 31)));
    assertTrue(constraint.contains(new LocalDate(2011, 1, 1)));
    assertTrue(constraint.contains(new LocalDate(2011, 5, 12)));
    assertTrue(constraint.contains(new LocalDate(2011, 12, 31)));
    assertTrue(constraint.contains(new LocalDate(2012, 1, 1)));
    assertTrue(constraint.contains(new LocalDate(2013, 5, 6)));
  }

  @Test
  public void shouldSupportContainsCheckForBoundsOnNoSides() {
    DateRangeConstraint constraint = new DateRangeConstraint("someInt", (LocalDate) null, null);

    assertTrue(constraint.contains(new LocalDate(2010, 10, 10)));
    assertTrue(constraint.contains(new LocalDate(2010, 12, 31)));
    assertTrue(constraint.contains(new LocalDate(2011, 1, 1)));
    assertTrue(constraint.contains(new LocalDate(2011, 5, 12)));
    assertTrue(constraint.contains(new LocalDate(2011, 12, 31)));
    assertTrue(constraint.contains(new LocalDate(2012, 1, 1)));
    assertTrue(constraint.contains(new LocalDate(2013, 5, 6)));
  }
}
