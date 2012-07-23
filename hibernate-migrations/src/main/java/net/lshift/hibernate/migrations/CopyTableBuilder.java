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

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class CopyTableBuilder extends TraceableMigrationElement {

  private Logger log = LoggerFactory.getLogger(CopyTableBuilder.class);

  private String sourceTable;
  private String destinationTable;

  private Iterable<String> sourceCols;
  private Iterable<String> destCols;

  private List<JoinSpecification> joins = new ArrayList<JoinSpecification>();

  private Map<String,String> sourcePredicate;
  private Map<String,String> constants = new TreeMap<String, String>();

  public CopyTableBuilder(String source, String destination, Iterable<String> sourceCols, Iterable<String> destCols) {
    this.sourceTable = source;
    this.destinationTable = destination;
    this.sourceCols = sourceCols;
    this.destCols = destCols;
  }

  public CopyTableBuilder(String source, String destination, Iterable<String> destCols) {
    this.sourceTable = source;
    this.destinationTable = destination;
    this.sourceCols = new ArrayList<String>();
    this.destCols = destCols;
  }

  public CopyTableBuilder join(String table, String joinColumn, String joinRefColumn, Iterable<String> columnsToJoin) {
    JoinSpecification spec = new JoinSpecification(table, joinColumn,joinRefColumn, columnsToJoin);
    joins.add(spec);
    return this;
  }

  public CopyTableBuilder withConstant(String name, String value) {
    this.constants.put(name, "'" + value + "'");
    return this;
  }

  public CopyTableBuilder whereSource(Map<String,String> predicate) {
    this.sourcePredicate = predicate;
    return this;
  }

  @Override
  public void apply(Connection conn) throws SQLException {
    String sql = getSQL();
    logStatement(sql);

    PreparedStatement stmt = conn.prepareStatement(sql);
    int rows = stmt.executeUpdate();
    stmt.close();
    log.info(String.format("Inserted %s rows for statement: %s", rows, sql));
  }


  public String getSQL() {
    Joiner joiner = Joiner.on(",").skipNulls();
    String destColumnNames = joiner.join(destCols);

    StringBuilder finalColumnNames = new StringBuilder(destColumnNames);

    for (Map.Entry<String, String> entry : constants.entrySet()) {
      finalColumnNames.append("," + entry.getKey());
    }

    return String.format("insert into %s(%s) %s", destinationTable, finalColumnNames, buildSelect());
  }

  @Override
  public String toString() {
    return getSQL();
  }

  private void verifyColumnNames() {
    List<String> projectedColumns = new ArrayList<String>();
    for (String sourceColumn : sourceCols) {
      projectedColumns.add(sourceColumn);
    }
    for (JoinSpecification join : joins) {
      for (String joinColumn : join.getColumnsToJoin()) {
        projectedColumns.add(joinColumn);
      }
    }

    List<String> targetColumns = new ArrayList<String>();
    for (String targetColumn : destCols) {
      targetColumns.add(targetColumn);
    }

    if (! ( projectedColumns.size() == (targetColumns.size()) ) ) {
      String template = "Targeting columns (%s) with wrong number of projected columns (%s)";
      throw new IllegalArgumentException(String.format(template, targetColumns, projectedColumns));
    }

  }

  private String buildSelect()  {
    verifyColumnNames();
    Joiner joiner = Joiner.on(",").skipNulls();

    if (!joins.isEmpty()) {

      String whereClause = "";

      List<String> columnsToJoin = new ArrayList<String>();

      // Aaaah, good old C :-)
      for (int i = 0; i < joins.size(); i++) {
        JoinSpecification join = joins.get(i);
        String joinedColumnNames = joiner.join(prefixColumns(join.getColumnsToJoin(), String.format("j%s.", i)));
        columnsToJoin.add(joinedColumnNames);
      }

      // Put the first join into the where clause

      int index = 0;
      JoinSpecification firstJoin = joins.get(index);
      StringBuffer whereClauseBuffer = new StringBuffer();
      String fragment = String.format("where j%s.%s = s.%s", index, firstJoin.getJoinColumn(), firstJoin.getJoinRefColumn());
      whereClauseBuffer.append(fragment);

      // Append the rest of the join specifications as and clauses

      for (int i = index + 1; i < joins.size(); i++) {
        JoinSpecification join = joins.get(i);
        String andFragment = String.format(" and j%s.%s = s.%s", i, join.getJoinColumn(), join.getJoinRefColumn());
        whereClauseBuffer.append(andFragment);
      }

      if (sourcePredicate != null && !sourcePredicate.isEmpty()) {

        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, String> entry : sourcePredicate.entrySet()) {
          String partial = String.format(" and s.%s = '%s'", entry.getKey(), entry.getValue());
          builder.append(partial);
        }

        whereClause = whereClauseBuffer.toString() + builder.toString();
      }

      String sourceColumnNames = joiner.join(prefixColumns(sourceCols, "s."));
      String joinedColumnNames = joiner.join(columnsToJoin);


      List<String> joinedJoinTableNames = new ArrayList<String>();

      for (int i = 0; i < joins.size(); i++) {
        JoinSpecification join = joins.get(i);
        String columnFragment = String.format("%s j%s", join.getJoinTable(), i);
        joinedJoinTableNames.add(columnFragment);
      }

      String joinedJoinTableNameClause = joiner.join(joinedJoinTableNames);

      String constantValuesClause = joiner.join(constants.values());
      if (constants.size() > 0) {
        constantValuesClause = "," + constantValuesClause;
      }

      if (sourceColumnNames.length() > 0) {
        return String.format("select %s,%s%s from %s s, %s %s", sourceColumnNames, joinedColumnNames, constantValuesClause, sourceTable, joinedJoinTableNameClause, whereClause);
      }
      else {
        return String.format("select %s%s from %s s, %s %s", joinedColumnNames, constantValuesClause, sourceTable, joinedJoinTableNameClause, whereClause);
      }

    }
    else {
      String sourceColumnNames = joiner.join(sourceCols);

      if (sourcePredicate != null && !sourcePredicate.isEmpty()) {

        List<String> predicates = new ArrayList<String>();

        for (Map.Entry<String, String> entry : sourcePredicate.entrySet()) {
          predicates.add(String.format("%s = '%s'", entry.getKey(), entry.getValue()));
        }

        String predicateClause = Joiner.on(" and ").skipNulls().join(predicates);

        return String.format("select %s from %s where %s", sourceColumnNames, sourceTable, predicateClause);

      } else {

        return String.format("select %s from %s", sourceColumnNames, sourceTable);

      }

    }

  }

  private Iterable<String> prefixColumns(Iterable<String> cols, String prefix) {
    List<String> prefixed = new ArrayList<String>();
    for (String col : cols) {
      prefixed.add(prefix + col);
    }
    return prefixed;
  }

  class JoinSpecification {

    private String joinTable;
    private String joinColumn;
    private String joinRefColumn;
    private Iterable<String> columnsToJoin;

    public String getJoinTable() {
      return joinTable;
    }

    public String getJoinColumn() {
      return joinColumn;
    }

    public String getJoinRefColumn() {
      return joinRefColumn;
    }

    public Iterable<String> getColumnsToJoin() {
      return columnsToJoin;
    }

    JoinSpecification(String joinTable, String joinColumn, String joinRefColumn, Iterable<String> columnsToJoin) {
      this.joinTable = joinTable;
      this.joinColumn = joinColumn;
      this.joinRefColumn = joinRefColumn;
      this.columnsToJoin = columnsToJoin;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      JoinSpecification that = (JoinSpecification) o;

      if (!joinTable.equals(that.joinTable)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return joinTable.hashCode();
    }
  }
}
