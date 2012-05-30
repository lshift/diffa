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

import com.google.common.base.Joiner;
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
 * Extension to the Oracle Dialect, providing additional Oracle-specific SQL information.
 */
public class OracleDialectExtension extends DialectExtension {
  @Override
  public String getDialectName() {
    return "ORACLE";
  }

  @Override
  public String alterColumnString() {
    return "modify";
  }

  @Override
  public String addColumnString() {
    return "add";
  }
  
  @Override
  public String setColumnNullString() {
    return " ";
  }

  @Override
  public boolean shouldBracketAlterColumnStatement() {
    return true;
  }

  @Override
  public boolean supportsHashPartitioning() {
    return true;
  }

  @Override
  public boolean supportsListPartitioning() {
    return true;
  }

  @Override
  public String defineHashPartitionString(int partitions, String... columns) {
    Joiner joiner = Joiner.on(",").skipNulls();
    String joined = joiner.join(columns);
    return String.format("partition by hash(%s) partitions ", joined) + partitions;
  }

  @Override
  public String defineListPartitionString(String column, Map<String, String[]> partitionDefinitions) {
    StringBuilder partitionDefString = new StringBuilder();
    for (Map.Entry<String, String[]> partition : partitionDefinitions.entrySet()) {
      if (partitionDefString.length() > 0) partitionDefString.append(", ");

      StringBuilder partitionValuesBuilder = new StringBuilder();
      for (String value : partition.getValue()) {
        if (partitionValuesBuilder.length() > 0)
          partitionValuesBuilder.append(", ");
        
        partitionValuesBuilder.append("'").append(value).append("'");
      }

      partitionDefString.append(
        String.format("partition \"%s\" values (%s)", partition.getKey(), partitionValuesBuilder));
    }

    return String.format("partition by list (%s) (%s)", column, partitionDefString);
  }

  @Override
  public boolean supportsAnalyze() {
    return true;
  }

  @Override
  public String analyzeTableString(String table) {
    return String.format("analyze table %s compute statistics", table);
  }
  
  @Override
  public String schemaPropertyName() {
    return Environment.USER;
  }

  @Override
  public void widenIntColumn(Connection conn, String table, String column) throws SQLException {

    String template = "alter table %s modify (%s number(38))";
    String sql = String.format(template, table, column);

    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.execute();
    stmt.close();
  }
}
