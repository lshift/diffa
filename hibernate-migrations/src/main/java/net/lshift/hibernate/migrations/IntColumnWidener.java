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

import net.lshift.hibernate.migrations.dialects.DialectExtension;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class IntColumnWidener implements MigrationElement {

  private final Dialect dialect;
  private final DialectExtension dialectExtension;
  private final Configuration config;
  private final String table;
  private String column = null;
  private List<String> statements = new ArrayList<String>();

  private CreateTableBuilder tempTable = null;

  public IntColumnWidener(Configuration config, Dialect dialect, DialectExtension dialectExtension, String table) {
    this.config = config;
    this.dialectExtension = dialectExtension;
    this.dialect = dialect;
    this.table = table;
    this.tempTable = new CreateTableBuilder(dialect, dialectExtension, getTempTableName());
  }

  public IntColumnWidener column(String column) {
    this.column = column;
    return this;
  }

  private String getTempTableName() {
    return table + "_temp";
  }

  public CreateTableBuilder getTempTable() {
    return tempTable;
  }

  public boolean requiresNewTable() {
    return !dialectExtension.supportsColumnTypeChanges();
  }



  @Override
  public void apply(Connection conn) throws SQLException {

    if (requiresNewTable()) {

      TransplantTableBuilder transplantTableBuilder =
          new TransplantTableBuilder(config, dialect, dialectExtension, table, tempTable);

      transplantTableBuilder.apply(conn);

    }
    else {

      dialectExtension.widenIntColumn(conn, table, column);

    }

  }

  @Override
  public List<String> getStatements() {
    return statements;
  }
}
