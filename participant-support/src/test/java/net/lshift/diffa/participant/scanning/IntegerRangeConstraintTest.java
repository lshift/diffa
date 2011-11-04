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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IntegerRangeConstraintTest {
  @Test
  public void shouldSupportContainsCheckForBoundsOnBothSides() {
    IntegerRangeConstraint constraint = new IntegerRangeConstraint("someInt", 5, 12);

    assertFalse(constraint.contains(1));
    assertFalse(constraint.contains(4));
    assertTrue(constraint.contains(5));
    assertTrue(constraint.contains(6));
    assertTrue(constraint.contains(12));
    assertFalse(constraint.contains(13));
    assertFalse(constraint.contains(18));
  }

  @Test
  public void shouldSupportContainsCheckForBoundsOnTopSideOnly() {
    IntegerRangeConstraint constraint = new IntegerRangeConstraint("someInt", null, 12);

    assertTrue(constraint.contains(-100));
    assertTrue(constraint.contains(4));
    assertTrue(constraint.contains(6));
    assertTrue(constraint.contains(12));
    assertFalse(constraint.contains(13));
    assertFalse(constraint.contains(18));
  }

  @Test
  public void shouldSupportContainsCheckForBoundsOnBottomSideOnly() {
    IntegerRangeConstraint constraint = new IntegerRangeConstraint("someInt", 5, null);

    assertFalse(constraint.contains(-100));
    assertFalse(constraint.contains(4));
    assertTrue(constraint.contains(6));
    assertTrue(constraint.contains(12));
    assertTrue(constraint.contains(1000));
  }

  @Test
  public void shouldSupportContainsCheckForBoundsOnNoSides() {
    IntegerRangeConstraint constraint = new IntegerRangeConstraint("someInt", (Integer) null, null);

    assertTrue(constraint.contains(-100));
    assertTrue(constraint.contains(4));
    assertTrue(constraint.contains(6));
    assertTrue(constraint.contains(12));
    assertTrue(constraint.contains(1000));
  }
}
