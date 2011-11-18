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
 * Extension to the Oracle Dialect, providing additional Oracle-specific SQL information.
 */
public class OracleDialectExtension extends DialectExtension {

  @Override
  public String alterColumnString() {
    return "modify";
  }

  @Override
  public String addColumnString() {
    return "add";
  }

  @Override
  public boolean shouldBracketAlterColumnStatement() {
    return true;
  }
}
