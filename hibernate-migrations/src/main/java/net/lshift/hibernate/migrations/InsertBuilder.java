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
package net.lshift.hibernate.migrations;

import org.hibernate.dialect.Dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an insert into a table.
 */
public class InsertBuilder extends SingleStatementMigrationElement {
  private final Dialect dialect;
  private final String table;
  private final Map<String, Object> insertValues;

  public InsertBuilder(Dialect dialect, String table) {
    this.table = table;
    this.dialect = dialect;
    this.insertValues = new HashMap<String, Object>();
  }

  /**
   * If you intend to use a boolean flag, insert the value as a java.lang.Boolean.
   * If the target dialect does not handle flags, in this fashion, don't worry they will be converted appropriately.
   */
  public InsertBuilder values(Map<String, Object> vals) {
    insertValues.putAll(vals);
    return this;
  }

  @Override
  protected PreparedStatement prepare(Connection conn) throws SQLException {
    StringBuilder namesBuilder = new StringBuilder();
    StringBuilder valuesBuilder = new StringBuilder();
    List<Object> orderedValues = new ArrayList<Object>();

    for (Map.Entry<String, Object> entry : insertValues.entrySet()) {
      if (namesBuilder.length() > 0) {
        namesBuilder.append(",");
        valuesBuilder.append(",");
      }
      namesBuilder.append(entry.getKey());
      valuesBuilder.append("?");

      orderedValues.add(convertValueType(dialect, entry.getValue()));
    }

    PreparedStatement stmt = prepareAndLog(conn,
      String.format("insert into %s(%s) values(%s)", table, namesBuilder.toString(), valuesBuilder.toString()));
    for (int i = 0; i < orderedValues.size(); ++i) {
      stmt.setObject(i+1, orderedValues.get(i));
    }

    return stmt;
  }

  /**
   * A very crude differentiation for dialects that support SQL boolean types as opposed to mapping them as 1s or 0s.
   */
  private Object convertValueType(Dialect dialect, Object value) {
    if (value.getClass().equals(Boolean.class)) {
      String type =  dialect.getTypeName(Types.BIT);
      if (type.contains("bool")) {
        return value;
      }
      else if (type.contains("number") || type.contains("int")) {
        return (Boolean) value ? 1 : 0;
      }
    }
    return value;
  }

  @Override
  protected String getSQL() {
    return null;
  }
}
