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

import net.lshift.hibernate.migrations.dialects.DialectExtension;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PrimaryKey;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.lshift.hibernate.migrations.SQLStringHelpers.generateColumnString;
import static net.lshift.hibernate.migrations.SQLStringHelpers.generateIdentityColumnString;
import static net.lshift.hibernate.migrations.SQLStringHelpers.generateNonIdentityColumnString;

/**
 * Describes a table that is to be created.
 */
public class CreateTableBuilder extends TraceableMigrationElement {
  private final Dialect dialect;
  private final String name;
  private final List<String> primaryKeys;
  private final List<Column> columns;
  private boolean useNativeIdentityGenerator = false;
  private PartitionAwareTableHelper partitionHelper;

  public CreateTableBuilder(Dialect dialect, DialectExtension dialectExtension, String name, String... primaryKeys) {
    this.dialect = dialect;
    this.partitionHelper = new PartitionAwareTableHelper(dialectExtension);
    this.name = name;
    this.primaryKeys = new ArrayList<String>(Arrays.asList(primaryKeys));
    this.columns = new ArrayList<Column>();
  }

  public String getTableName() {
    return name;
  }

  //
  // Builder Methods
  //

  public CreateTableBuilder withNativeIdentityGenerator() {
    useNativeIdentityGenerator = true;
    return this;
  }

  public CreateTableBuilder pk(String...names) {
    primaryKeys.addAll(Arrays.asList(names));
    return this;
  }

  public CreateTableBuilder column(String name, int sqlType, boolean nullable) {
    return column(name, sqlType, Column.DEFAULT_LENGTH, nullable, null);
  }

  public CreateTableBuilder column(String name, int sqlType, boolean nullable, Object defaultVal) {
    return column(name, sqlType, Column.DEFAULT_LENGTH, nullable, defaultVal);
  }

  public CreateTableBuilder column(String name, int sqlType, int length, boolean nullable) {
    return column(name, sqlType, length, nullable, null);
  }

  public CreateTableBuilder column(String name, int sqlType, int length, boolean nullable, Object defaultVal) {
    Column col = new Column(name);
    col.setNullable(nullable);
    col.setSqlTypeCode(sqlType);
    col.setLength(length);
    col.setDefaultValue(defaultVal != null ? defaultVal.toString() : null);

    columns.add(col);

    return this;
  }

  public CreateTableBuilder virtualColumn(String name, int sqlType, int length, String generator) {
    VirtualColumn col = new VirtualColumn(name);
    col.setSqlTypeCode(sqlType);
    col.setLength(length);
    col.setGenerator(generator);

    columns.add(col);

    return this;
  }

  public CreateTableBuilder hashPartitions(int partitions, String ... columns) {
    partitionHelper.defineHashPartitions(partitions, columns);
    return this;
  }

  public CreateTableBuilder listPartitioned(String column) {
    partitionHelper.useListPartitioning(column);
    return this;
  }

  public CreateTableBuilder listPartition(String name, String...values) {
    partitionHelper.addListPartition(name, values);
    return this;
  }

  public List<Column> getColumns() {
    return columns;
  }


  //
  // Output Methods
  //

  private String createTableSql() {
    StringBuffer buffer =
        new StringBuffer(dialect.getCreateTableString()).append(' ').append(dialect.quote(name)).append(" (");

    for (Column col : columns) {
      int indexOfPrimaryKey = primaryKeys.indexOf(col.getName());
      if (indexOfPrimaryKey == 0 && useNativeIdentityGenerator && dialect.supportsIdentityColumns()) {
        // only apply native identity generator to first column of primary key
        buffer.append(generateIdentityColumnString(dialect, col));
      } else if (indexOfPrimaryKey >= 0) {
          buffer.append(generateNonIdentityColumnString(dialect, col));
      } else {
        buffer.append(generateColumnString(dialect, col, true));
      }
      buffer.append(", ");
    }
    buffer.append(getPrimaryKey().sqlConstraintString(dialect));

    buffer.append(")");

    partitionHelper.appendPartitionString(buffer);

    return buffer.toString();
  }

  private void createSequences(Connection conn) throws SQLException {
    for (Column col : columns) {
      if (primaryKeys.contains(col.getName())) {
        for( String sql : dialect.getCreateSequenceStrings(dialect.quote(name + "_sequence"))) {
          prepareAndLogAndExecute(conn, sql);
          return;
        }
      }
    }
  }

  public PrimaryKey getPrimaryKey() {
    PrimaryKey result = new PrimaryKey();
    for (String pk : primaryKeys) result.addColumn(new Column(pk));
    return result;
  }

  @Override
  public void apply(Connection conn) throws SQLException {

    prepareAndLogAndExecute(conn, createTableSql());

    if (useNativeIdentityGenerator && ! dialect.supportsIdentityColumns()) {
      createSequences(conn);
    }
  }
}
