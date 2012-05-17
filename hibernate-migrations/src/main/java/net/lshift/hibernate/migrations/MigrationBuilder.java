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
import net.lshift.hibernate.migrations.dialects.DialectExtensionSelector;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper for describing database migrations.
 */
public class MigrationBuilder {
  
  static Logger log = LoggerFactory.getLogger(MigrationBuilder.class);
  
  private final Dialect dialect;
  private final DialectExtension dialectExtension;
  private final List<MigrationElement> elements;
  private final Configuration config;
  private final List<String> statements;
  private String preconditionPredicate;
  private String preconditionTable;
  private int expectedPreconditionCount;

  public MigrationBuilder(Configuration config) {
    this.config = config;
    this.dialect = Dialect.getDialect(config.getProperties());
    this.dialectExtension = DialectExtensionSelector.select(this.dialect);
    this.elements = new ArrayList<MigrationElement>();
    this.statements = new ArrayList<String>();
  }

  //
  // Builder Methods
  //

  public CreateTableBuilder createTable(String name, String...pks) {
    return register(new CreateTableBuilder(dialect, dialectExtension, name, pks));
  }

  public InsertBuilder insert(String table) {
    return register(new InsertBuilder(dialect, table));
  }

  public DeleteBuilder delete(String table) {
    return register(new DeleteBuilder(table));
  }

  public AlterTableBuilder alterTable(String table) {
    return register(new AlterTableBuilder(config, dialect, dialectExtension, table));
  }

  public DropTableBuilder dropTable(String table) {
    return register(new DropTableBuilder(config, dialect, table));
  }

  public CreateIndexBuilder createIndex(String name, String table, String...columns) {
    return register(new CreateIndexBuilder(name, table, columns));
  }

  public CopyTableBuilder copyTableContents(String source, String destination, Iterable<String> columns) {
    return register(new CopyTableBuilder(source, destination, columns));
  }

  public AnalyzeTableBuilder analyzeTable(String table) {
    return register(new AnalyzeTableBuilder(dialectExtension, table));
  }

  public ScriptLoader executeDatabaseScript(String scriptName, String packageName) {
    return register(new ScriptLoader(dialectExtension, scriptName, packageName));
  }

  public StoredProcedureCallBuilder executeStoredProcedure(String procedureName) {
    return register(new StoredProcedureCallBuilder(procedureName));
  }

  public RawSqlBuilder sql(String sql) {
    return register(new RawSqlBuilder(sql));
  }

  private <T extends MigrationElement> T register(T el) {
    elements.add(el);
    return el;
  }


  public void addPrecondition(String table, String predicate, int expectedRows) {
    this.preconditionPredicate = predicate;
    this.preconditionTable = table;
    this.expectedPreconditionCount = expectedRows;
  }

  private String buildQueryString(String table, String predicate) {
    return "select count(*) from " + table + " " + predicate;
  }

  //
  // Application Methods
  //

  public void apply(Connection conn) throws SQLException {
    
    if(preconditionPredicate != null && preconditionTable != null) {
      int count = 0;
      ResultSet metaDataRs = null;
      ResultSet countRs = null;
      Statement statement = null;
      try {

        metaDataRs = conn.getMetaData().getTables(null, null, preconditionTable, null);
        if (metaDataRs.next()) {
          statement = conn.createStatement();
          String sql = buildQueryString(preconditionTable, preconditionPredicate);

          if (log.isTraceEnabled()) {
            log.trace("Executing migration precondition statement: " + sql);
          }

          countRs = statement.executeQuery(sql);

          if (countRs.next()) {
            count = countRs.getInt(1);
          }

        }
      }
      finally {

        try {

          if (statement != null) {
            statement.close();
          }

        }
        finally {

          try {
            if (metaDataRs != null) {
              metaDataRs.close();
            }
          } finally {
            if (countRs != null) {
              countRs.close();
            }
          }

        }
      }

      if (count != expectedPreconditionCount) {
        log.info(String.format(
            "Precondition [%s] not fulfilled: count was %s, expected %s",
            new Object[]{preconditionPredicate, count, expectedPreconditionCount}));
        return;
      }

    }
    
    for (MigrationElement el : elements) {
      try {
        el.apply(conn);
      } finally {
        statements.addAll(el.getStatements());
      }
    }
  }

  public List<String> getStatements() {
    return statements;
  }

  public boolean canUseHashPartitioning() {
    return dialectExtension.supportsHashPartitioning();
  }

  public boolean canUseListPartitioning() {
    return dialectExtension.supportsListPartitioning();
  }

  public boolean canAnalyze() {
    return dialectExtension.supportsAnalyze();
  }
}
