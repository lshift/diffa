/**
 * Copyright (C) 2012 LShift Ltd.
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
package net.lshift.hibernate.migrations;

import org.hibernate.mapping.Column;

/**
 * Extension of a standard column definition to define a virtual column in a table.
 */
public class VirtualColumn extends Column {
  private String generator;

  public VirtualColumn(String columnName) {
    super(columnName);
  }

  /**
   * The SQL string defining how the column is defined (eg, "foo || '-' || bar").
   */
  public String getGenerator() {
    return generator;
  }
  public void setGenerator(String generator) {
    this.generator = generator;
  }
}
