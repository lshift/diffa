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

import net.lshift.hibernate.migrations.MigrationBuilder;
import net.lshift.hibernate.migrations.MigrationElement;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.Dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class providing extensions to specific Hibernate dialects. When a Hibernate dialect doesn't have a specific
 * extension, this base extension will be used.
 */
public abstract class DialectExtension {

  public abstract String getDialectName() ;

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
  
  public String setColumnNullString() {
    return " set ";
  }

  /**
   * Retrieves whether the column definition in an alter column statement should be bracketed.
   * @return whether the definition should be bracketed.
   */
  public boolean shouldBracketAlterColumnStatement() {
    return false;
  }

  /**
   * Indicates whether the database permits tables to be partitioned using a hash-partitioning scheme.
   * @return
   */
  public boolean supportsHashPartitioning() {
    return false;
  }

  /**
   * Indicates whether the database permits tables to be partitioned using a list/value partitioning scheme.
   * @return
   */
  public boolean supportsListPartitioning() {
    return false;
  }
  
  /**
   * Retrieves the string to be used when specifying a partitioning hash scheme
   * @param partitions The number of partitions to create.
   * @param columns The columns to base the partitioning on.
   * @return the string.
   */
  public String defineHashPartitionString(int partitions, String ... columns) {
    throw new RuntimeException("Hash partitioning is not supported by this dialect");
  }

  /**
   * Retrieves the string to be used when specifying the list partitioning scheme on a table.
   * @param column the column that the partitioning is based upon.
   * @param partitionDefinitions definitions of the partitions.
   * @return the string.
   */
  public String defineListPartitionString(String column, Map<String, String[]> partitionDefinitions) {
    throw new RuntimeException("List partitioning is not supported by this dialect");
  }

  /**
   * Indicates whether the database permits tables to be analyzed.
   * @return
   */
  public boolean supportsAnalyze() {
    return false;
  }

  /**
   * Retrieves the string to be used when analyzing a table
   * @param table The table to analyze.
   */
  public String analyzeTableString(String table) {
    throw new RuntimeException("analyze table is not supported by this dialect");
  }

  public boolean supportsPrimaryKeyReplace() {
    return false;
  }

  public String schemaPropertyName() {
    return Environment.DEFAULT_SCHEMA;
  }
  
  public String getTypeStringForSetColumnNullability(Dialect dialect, int sqlType, int length) {
    return "";
  }
  
  public String getDropIndex(String name) {
    return "drop index " + name;
  }
  
  public boolean indexDropsWithForeignKey() {
    return true;
  }
  
  public boolean supportsFractionalSeconds() {
    return true;
  }

  public boolean supportsColumnTypeChanges() {
    return true;
  }

  /**
   * Provides a series of statements that can perform a column widening operation
   * for a particular dialect, under the assumption there are no foreign keys on the column to be widened.
   */
  public void widenIntColumn(Connection conn, String table, String column) throws SQLException{
    String template = "alter table %s alter column %s bigint";
    String sql = String.format(template, table, column);

    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.execute();
    stmt.close();
  }
}
