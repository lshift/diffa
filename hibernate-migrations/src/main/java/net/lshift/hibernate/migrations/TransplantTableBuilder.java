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

import net.lshift.hibernate.migrations.dialects.DialectExtension;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This is used to transplant the contents of one table into another table
 * for example if you need to alter some columns in a way that your database
 * doesn't allow you to do.
 */
public class TransplantTableBuilder implements MigrationElement {

  private final String source;
  private final CreateTableBuilder tempTable;
  private final Map<String,String> renames;
  private final Configuration configuration;
  private final Dialect dialect;
  private final DialectExtension dialectExtension;

  private List<String> statements = new ArrayList<String>();

  public TransplantTableBuilder(Configuration config, Dialect dialect, DialectExtension dialectExtension,
                                String source, CreateTableBuilder tempTable, Map<String, String> renames) {
    this.configuration = config;
    this.dialect = dialect;
    this.dialectExtension = dialectExtension;
    this.source = source;
    this.tempTable = tempTable;
    this.renames = renames;
  }

  public TransplantTableBuilder(Configuration config, Dialect dialect, DialectExtension dialectExtension,
                                String source, CreateTableBuilder tempTable) {
    this(config, dialect, dialectExtension, source, tempTable, null);
  }

  @Override
  public void apply(Connection conn) throws SQLException {

    // Create the temporary table
    tempTable.apply(conn);


    // Copy the data from the old table to the temporary table
    List<String> sourceCols = new ArrayList<String>();
    List<String> destCols = new ArrayList<String>();

    for(Column column : tempTable.getColumns()) {
      String name = column.getName();
      destCols.add(name);
      if (renames != null && renames.containsKey(name)) {
        sourceCols.add(renames.get(name));
      }
      else {
        sourceCols.add(name);
      }
    }

    CopyTableBuilder copyTableBuilder = new CopyTableBuilder(source, tempTable.getTableName(), sourceCols, destCols);
    copyTableBuilder.apply(conn);

    // Delete the source table

    DropTableBuilder dropTableBuilder = new DropTableBuilder(configuration, dialect, source);
    dropTableBuilder.apply(conn);

    // Rename the destination table to the source table's original name
    AlterTableBuilder alterTableBuilder = new AlterTableBuilder(configuration, dialect, dialectExtension, tempTable.getTableName());
    alterTableBuilder.renameTo(source);
    alterTableBuilder.apply(conn);

  }

  @Override
  public List<String> getStatements() {
    return statements;
  }
}
