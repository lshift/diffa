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
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.*;
import org.hibernate.util.StringHelper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static net.lshift.hibernate.migrations.SQLStringHelpers.generateColumnString;
import static net.lshift.hibernate.migrations.SQLStringHelpers.maybeBracketTerm;
import static net.lshift.hibernate.migrations.SQLStringHelpers.qualifyName;

/**
 * Describes an alter table statement.
 */
public class AlterTableBuilder extends TraceableMigrationElement {

  private final Configuration config;
  private final Dialect dialect;
  private final DialectExtension dialectExtension;
  private final String table;
  private final List<String> alterFragments;

  public AlterTableBuilder(Configuration config, Dialect dialect, DialectExtension dialectExtension, String table) {
    this.config = config;
    this.dialect = dialect;
    this.dialectExtension = dialectExtension;
    this.table = table;
    this.alterFragments = new ArrayList<String>();
  }

  public AlterTableBuilder dropColumn(String column) {
    alterFragments.add("drop column " + dialect.openQuote() + column.toUpperCase() + dialect.openQuote());
    return this;
  }

  public AlterTableBuilder addColumn(String name, int sqlType, int length, boolean nullable, Object defaultVal) {
    Column col = buildColumnDefinition(name, sqlType, length, nullable, defaultVal);
    alterFragments.add(dialectExtension.addColumnString() + " " + generateColumnString(dialect, col, false));
    return this;
  }

  public AlterTableBuilder alterColumn(String name, int sqlType, int length, boolean nullable, Object defaultVal) {
    Column col = buildColumnDefinition(name, sqlType, length, nullable, defaultVal);
    alterFragments.add(dialectExtension.alterColumnString() + " " +
      maybeBracketTerm(generateColumnString(dialect, col, false), dialectExtension.shouldBracketAlterColumnStatement()));
    return this;
  }

  public AlterTableBuilder setColumnNullable(String name, int sqlType, int length, boolean nullable) {
    // TODO: clean this up, it is awful.
    alterFragments.add(dialectExtension.alterColumnString() + " " +
        maybeBracketTerm(name + dialectExtension.getTypeStringForSetColumnNullability(dialect, sqlType, length)
            + dialectExtension.setColumnNullString()
            + String.format("%snull", nullable ? "" : "not "),
            dialectExtension.shouldBracketAlterColumnStatement()));
    return this;
  }

  private static Column buildColumnDefinition(String name, int sqlType, int length, boolean nullable, Object defaultVal) {
    Column col = new Column(name);
    col.setSqlTypeCode(sqlType);
    col.setNullable(nullable);
    col.setLength(length);
    col.setDefaultValue(defaultVal != null ? defaultVal.toString() : null);
    return col;
  }

  public AlterTableBuilder addForeignKey(String name, String columnName, String referencedTable, String referencedColumn) {
    return addForeignKey(name, new String[] { columnName }, referencedTable, new String[] { referencedColumn });
  }
  public AlterTableBuilder addForeignKey(String name, String[] columnNames, String referencedTable, String[] referencedColumns) {
    ForeignKey fk = new ForeignKey();
    fk.setName(name);
    for (String col : columnNames) fk.addColumn(new Column(col));
    fk.setTable(new Table(table));

    PrimaryKey refPrimaryKey = new PrimaryKey();
    for (String col : referencedColumns) refPrimaryKey.addColumn(new Column(col));
    Table refTable = new Table(referencedTable);
    refTable.setPrimaryKey(refPrimaryKey);
    
    fk.setReferencedTable(refTable);

    String defaultCatalog = config.getProperties().getProperty(Environment.DEFAULT_CATALOG);
    String defaultSchema = config.getProperties().getProperty(Environment.DEFAULT_SCHEMA);

    // fk.sqlConstraintString appears to generate incorrect SQL against MySQL in some instances.
    // The referenced columns are not always correctly listed.
//    alterFragments.add(" add index " + fk.getName() + " (" + StringHelper.join(", ", columnNames) +
//        "), add constraint " + fk.getName() + " foreign key (" + StringHelper.join(", ", columnNames) +
//        " references " + referencedTable + " (" + StringHelper.join(", ", referencedColumns) + ")");
    alterFragments.add(fk.sqlConstraintString(dialect, fk.getName(), defaultCatalog, defaultSchema));
    return this;
  }
  public AlterTableBuilder addUniqueConstraint(String columnName) {
    UniqueKey uk = new UniqueKey();
    uk.addColumn(new Column(columnName));

    String constraintString = uk.sqlConstraintString(dialect);
    if (constraintString != null) {
      alterFragments.add("add " + constraintString);
    }
    
    return this;
  }
  public AlterTableBuilder addUniqueConstraint(String name, String...columnNames) {
    UniqueKey uk = new UniqueKey();
    for (String col : columnNames) uk.addColumn(new Column(col));

    String defaultCatalog = config.getProperties().getProperty(Environment.DEFAULT_CATALOG);
    String defaultSchema = config.getProperties().getProperty(Environment.DEFAULT_SCHEMA);

    alterFragments.add(uk.sqlConstraintString(dialect, name, defaultCatalog, defaultSchema));
    return this;
  }

  public AlterTableBuilder dropForeignKey(String name) {
    String dropForeignKey = dialect.getDropForeignKeyString() + name;
    if (dialectExtension.indexDropsWithForeignKey()) {
      alterFragments.add(dropForeignKey);
    } else {
      alterFragments.add(dropForeignKey + ", drop index " + name);
    }
    return this;
  }

  public AlterTableBuilder dropConstraint(String name) {
    alterFragments.add("drop constraint " + name);
    return this;
  }

  public AlterTableBuilder dropPrimaryKey() {
    alterFragments.add("drop primary key");
    return this;
  }

  public AlterTableBuilder addPrimaryKey(String...cols) {
    PrimaryKey pk = new PrimaryKey();
    for (String col : cols) {
      pk.addColumn(new Column(col));
    }
    alterFragments.add("add " + pk.sqlConstraintString(dialect));
    return this;
  }
  
  public AlterTableBuilder replacePrimaryKey(String...cols) {
    PrimaryKey pk = new PrimaryKey();
    for (String col : cols) {
      pk.addColumn(new Column(col));
    }
    if (dialectExtension.supportsPrimaryKeyReplace()) {
      alterFragments.add("drop primary key, add " + pk.sqlConstraintString(dialect));
    } else {
      dropPrimaryKey().addPrimaryKey(cols);
    }
    return this;
  }

  public AlterTableBuilder addPartition() {
    alterFragments.add("add partition");
    return this;
  }

  public AlterTableBuilder renameTo(String newName) {
    alterFragments.add("rename to " + newName);
    return this;
  }

  @Override
  public void apply(Connection conn) throws SQLException {
    for (String fragment : alterFragments) {
      String sql = String.format("alter table %s %s", qualifyName(config, dialect, table), fragment);
      prepareAndLogAndExecute(conn, sql);
    }
  }
}
