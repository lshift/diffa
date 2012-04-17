/**
 * Copyright (C) 201-2012 LShift Ltd.
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

import com.google.common.base.Joiner;
import org.hibernate.dialect.Dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class UpdateBuilder extends SingleStatementMigrationElement {

  private final String table;
  private final SortedMap<String, String> updatePredicate;
  private boolean updateFromSelect = false;
  private boolean updateValueToNull = false;
  private String updateValue;
  private String updateColumn;

  private SelectBuilder selectBuilder;

  public UpdateBuilder(String table) {
    this.table = table;
    this.updatePredicate = new TreeMap<String, String>();
  }

  /**
   * Currently this only supports strings - if you need something, else you'll have to add it yourself
   */
  public UpdateBuilder predicate(String columnName, String value) {
    updatePredicate.put(columnName, value);
    return this;
  }

  /**
   * The key of this map
   *
   * Currently this only supports strings - if you need something, else you'll have to add it yourself
   */
  public UpdateBuilder withValue(String newValue) {
    if (newValue == null) {
      throw new IllegalArgumentException("To update to a null value, use withNullValue() instead.");
    }
    updateFromSelect = false;
    updateValue = newValue;
    return this;
  }

  public UpdateBuilder withNullValue() {
    updateValueToNull = true;
    return this;
  }

  public UpdateBuilder updateColumn(String columnName) {
    updateColumn = columnName;
    return this;
  }

  public UpdateBuilder withSelect(SelectBuilder builder) {
    updateFromSelect = true;
    this.selectBuilder = builder;
    return this;
  }

  private boolean hasValueToUpdate() {

    if (updateValue != null && updateValueToNull) {
      throw new IllegalArgumentException("This builder has been configured with a column to update and the instruction to supply a null value");
    }

    return updateValue != null || updateValueToNull || updateFromSelect;
  }

  @Override
  protected PreparedStatement prepare(Connection conn) throws SQLException {

    if (updateColumn == null) {
      throw new IllegalArgumentException("This builder has not been configured with a column to update");
    }

    if (!hasValueToUpdate()) {
      throw new IllegalArgumentException("This builder has not been configured with a value to update the target column with");
    }

    String updateValueFragment;

    if (updateFromSelect) {
      updateValueFragment = String.format("( %s )", selectBuilder.getSQL());
    }
    else if (updateValueToNull) {
      updateValueFragment = "null";
    }
    else {
      updateValueFragment = "'" + updateValue + "'";
    }

    List<String> predicates = new ArrayList<String>();

    for (Map.Entry<String, String> entry : updatePredicate.entrySet()) {
      predicates.add(entry.getKey() + " = '" + entry.getValue() + "'");
    }

    String sql = String.format("update %s set %s = %s", table, updateColumn, updateValueFragment);
    Joiner joiner = Joiner.on(" and ").skipNulls();
    if (!predicates.isEmpty()) {
      sql = sql + " where " + joiner.join(predicates);
    }

    PreparedStatement stmt = prepareAndLog(conn, sql);

    return stmt;
  }

  @Override
  protected String getSQL() {
    return null;
  }
}
