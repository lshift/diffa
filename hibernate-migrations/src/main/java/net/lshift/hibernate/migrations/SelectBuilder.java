/**
 * Copyright (C) 2010-2012 LShift Ltd.
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


public class SelectBuilder {

  private String tableName;
  private String valueColumn;
  private String predicateColumn;
  private String predicateValue;

  public SelectBuilder() {
  }



  public String getSQL() {

    validate();

    return String.format("select %s from %s where %s = '%s'", valueColumn,
                                                              tableName,
                                                              predicateColumn,
                                                              predicateValue);
  }

  private void validate() {
    if (tableName == null) {
      throw new IllegalArgumentException("Table name is required");
    }
    if (valueColumn == null) {
      throw new IllegalArgumentException("Value column is required");
    }
    if (predicateColumn == null) {
      throw new IllegalArgumentException("Predicate column is required");
    }
    if (predicateValue == null) {
      throw new IllegalArgumentException("Predicate value is required");
    }
  }

  public SelectBuilder select(String valueColumn) {
    this.valueColumn = valueColumn;
    return this;
  }

  public SelectBuilder from(String tableName) {
    this.tableName = tableName;
    return this;
  }

  public SelectBuilder where(String predicateColumn) {
    this.predicateColumn = predicateColumn;
    return this;
  }

  public SelectBuilder is(String predicateValue) {
    this.predicateValue = predicateValue;
    return this;
  }
}
