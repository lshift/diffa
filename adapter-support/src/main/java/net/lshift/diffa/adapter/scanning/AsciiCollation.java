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

public class AsciiCollation implements Collation {

  private static final AsciiCollation INSTANCE = new AsciiCollation();

  public static Collation get() {
    return INSTANCE;
  }

  @Override
  public boolean sortsBefore(String left, String right) {
    return left.compareTo(right) < 0;
  }

  @Override
  public String getName() {
    return "ascii";
  }
}
