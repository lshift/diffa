package net.lshift.diffa.adapter.scanning;

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

public class UnorderedCollation implements Collation {

  private static final UnorderedCollation INSTANCE = new UnorderedCollation();

  private UnorderedCollation() {}

  public static Collation get() {
    return INSTANCE;
  }

  @Override
  public boolean sortsBefore(String left, String right) {
    return true;
  }

  @Override
  public String getName() {
    return "unordered";
  }
}

