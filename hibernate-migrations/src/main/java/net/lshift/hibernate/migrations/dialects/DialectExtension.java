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
package net.lshift.hibernate.migrations.dialects;

/**
 * Class providing extensions to specific Hibernate dialects. When a Hibernate dialect doesn't have a specific
 * extension, this base extension will be used.
 */
public class DialectExtension {

  /**
   * Retrieves the string to be used when specifying that a column is being altered.
   * @return the string.
   */
  public String alterColumnString() {
    return "alter column";
  }

  /**
   * Retrieves the string to be used when specifying that a column is being added.
   * @return the string.
   */
  public String addColumnString() {
    return "add column";
  }

  /**
   * Retrieves whether the column definition in an alter column statement should be bracketed.
   * @return whether the definition should be bracketed.
   */
  public boolean shouldBracketAlterColumnStatement() {
    return false;
  }
}
