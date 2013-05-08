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
package net.lshift.diffa.participant.changes;

import net.lshift.diffa.participant.common.MissingMandatoryFieldException;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(Theories.class)
public class ChangeEventTest {

  @DataPoint public static ChangeEvent completelyEmptyEvent = new ChangeEvent();
  @DataPoint public static ChangeEvent nullIdWithValidVersion = new ChangeEvent(null,"version", null, null, null);
  @DataPoint public static ChangeEvent emptyIdWithValidVersion = new ChangeEvent("" ,"version", null, null, null);

  @Theory
  @Test(expected = MissingMandatoryFieldException.class)
  public void shouldRejectMissingMandatoryFields(ChangeEvent bogusEvent) {
    bogusEvent.ensureContainsMandatoryFields();
  }

  @Test
  public void shouldAcceptMinimumFields() {
    ChangeEvent validEvent = new ChangeEvent();
    validEvent.setId("id");
    validEvent.ensureContainsMandatoryFields();
  }
}
