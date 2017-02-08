/**
 * Copyright (C) 2010 - 2012 LShift Ltd.
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

import org.junit.Test;
import org.junit.Ignore;

import java.sql.Connection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static net.lshift.hibernate.migrations.HibernateHelper.mockExecutablePreparedStatement;
import static net.lshift.hibernate.migrations.HibernateHelper.mockExecutablePreparedStatementForUpdate;
import static org.easymock.EasyMock.*;

public class CopyTableBuilderTest {
  
  @Test
  public void shouldCopyColumns() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());

    Iterable<String> sourceCols = Arrays.asList("foo", "bar", "baz");
    Iterable<String> destCols = Arrays.asList("foo", "bar2", "baz");
    mb.copyTableContents("src", "dest", sourceCols, destCols);

    Connection conn = createStrictMock(Connection.class);

    expect(conn.prepareStatement("insert into dest(foo,bar2,baz) select foo,bar,baz from src")).andReturn(mockExecutablePreparedStatementForUpdate(1));
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldCopyColumnsWithPredicate() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());

    Iterable<String> sourceCols = Arrays.asList("foo", "bar", "baz");
    Iterable<String> destCols = Arrays.asList("foo", "bar2", "baz");
    Map<String,String> predicates = new TreeMap<String, String>();
    predicates.put("bar", "b");
    predicates.put("foo", "a");

    mb.copyTableContents("src", "dest", sourceCols, destCols).whereSource(predicates);

    Connection conn = createStrictMock(Connection.class);

    String sql = "insert into dest(foo,bar2,baz) select foo,bar,baz from src where bar = 'b' and foo = 'a'";

    expect(conn.prepareStatement(sql)).andReturn(mockExecutablePreparedStatementForUpdate(1));
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldCopyColumnsUsingSingleJoin() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());

    Iterable<String> sourceCols = Arrays.asList("id", "bar", "baz");
    Iterable<String> destCols = Arrays.asList("foo", "bar2", "baz", "c1", "c2");
    Iterable<String> nnCols = Arrays.asList("nn1", "nn2");

    Map<String,String> predicate = new HashMap<String,String>();
    predicate.put("predicate_column","predicate_value");

    mb.copyTableContents("src", "dest", sourceCols, destCols).
       join("join_table", "src_id", "id", Arrays.asList("kol1", "kol2")).
       whereSource(predicate).
       notNull(nnCols);

    Connection conn = createStrictMock(Connection.class);

    String sql =  "insert into dest(foo,bar2,baz,c1,c2) " +
                  "select s.id,s.bar,s.baz,j0.kol1,j0.kol2 from src s, join_table j0 "+
                  "where j0.src_id = s.id "+
                  "and nn1 is not null "+
                  "and nn2 is not null "+
                  "and s.predicate_column = 'predicate_value'";

    expect(conn.prepareStatement(sql)).andReturn(mockExecutablePreparedStatementForUpdate(1));
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  private void verifySqlMatches(String sql, MigrationBuilder mb) throws Exception {
    Connection conn = createStrictMock(Connection.class);

    expect(conn.prepareStatement(sql)).andReturn(mockExecutablePreparedStatementForUpdate(1));
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldFilterOutNullsAsDirected() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());

    Iterable<String> cols = Arrays.asList("id");
    Iterable<String> notNulls = Arrays.asList("nn1", "nn2");
    mb.copyTableContents("src", "dest", cols, cols).notNull(notNulls);

    String sql = "insert into dest(id) " +
        "select id from src " +
        "where nn1 is not null " +
        "and nn2 is not null";

    verifySqlMatches(sql, mb);
  }

  @Test
  public void shouldCopyColumnsUsingSingleJoinWithConstants() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());

    Iterable<String> sourceCols = Arrays.asList("id", "bar", "baz");
    Iterable<String> destCols = Arrays.asList("foo", "bar2", "baz", "c1", "c2");

    Map<String,String> predicate = new HashMap<String,String>();
    predicate.put("predicate_column","predicate_value");

    mb.copyTableContents("src", "dest", sourceCols, destCols).
        join("join_table", "src_id", "id", Arrays.asList("kol1", "kol2")).
        whereSource(predicate).
        withConstant("constant_column", "constant_value");

    Connection conn = createStrictMock(Connection.class);

    String sql =  "insert into dest(foo,bar2,baz,c1,c2,constant_column) " +
        "select s.id,s.bar,s.baz,j0.kol1,j0.kol2,'constant_value' from src s, join_table j0 "+
        "where j0.src_id = s.id and s.predicate_column = 'predicate_value'";

    expect(conn.prepareStatement(sql)).andReturn(mockExecutablePreparedStatementForUpdate(1));
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldDetectWhenWrongNumberOfColumnsAreSpecified() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());

    // "foo", "bar", "baz" come from the source table
    // "kol1", "kol2" come from the join table
    // The destination table only specifies "foo", "bar", "baz", therefore it should blow up

    Iterable<String> sourceCols = Arrays.asList("foo", "bar", "baz");
    Iterable<String> destCols = Arrays.asList("bar2", "baz", "c1", "c2");

    Map<String,String> predicate = new HashMap<String,String>();
    predicate.put("predicate_column","predicate_value");

    mb.copyTableContents("src", "dest", sourceCols, destCols).
        join("join_table", "src_id", "id", Arrays.asList("kol1", "kol2")).
        whereSource(predicate);

    Connection conn = createStrictMock(Connection.class);

    String sql =  "insert into dest(foo,bar2,baz,c1,c2) " +
        "select s.id,s.bar,s.baz,j0.kol1,j0.kol2 from src s, join_table j0 "+
        "where j0.src_id = s.id and s.predicate_column = 'predicate_value'";

    expect(conn.prepareStatement(sql)).andReturn(mockExecutablePreparedStatementForUpdate(1));
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }


  @Test
  public void shouldCopyColumnsUsingMulitpleJoins() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());

    Iterable<String> sourceCols = Arrays.asList("id", "bar", "baz");
    Iterable<String> destCols = Arrays.asList("foo", "bar2", "baz", "c1", "c2", "c3", "c4");

    Map<String,String> predicate = new HashMap<String,String>();
    predicate.put("predicate_column","predicate_value");

    mb.copyTableContents("src", "dest", sourceCols, destCols).
        join("join_table_0", "0_src_id", "id", Arrays.asList("kol1", "kol2")).
        join("join_table_1", "1_src_id", "id", Arrays.asList("kol3", "kol4")).
        whereSource(predicate);

    Connection conn = createStrictMock(Connection.class);

    String sql = "insert into dest(foo,bar2,baz,c1,c2,c3,c4) " +
                 "select s.id,s.bar,s.baz,j0.kol1,j0.kol2,j1.kol3,j1.kol4 "+
                 "from src s, join_table_0 j0,join_table_1 j1 " +
                 "where j0.0_src_id = s.id " +
                 "and j1.1_src_id = s.id " +
                 "and s.predicate_column = 'predicate_value'";

    expect(conn.prepareStatement(sql)).andReturn(mockExecutablePreparedStatementForUpdate(1));
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldCopyColumnsUsingMulitpleJoinsWithoutUsingSourceColumnsInResultSet() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());

    Iterable<String> sourceCols = Arrays.asList();
    Iterable<String> destCols = Arrays.asList("c1", "c2", "c3", "c4");

    Map<String,String> predicate = new HashMap<String,String>();
    predicate.put("predicate_column","predicate_value");

    mb.copyTableContents("src", "dest", sourceCols, destCols).
        join("join_table_0", "0_src_id", "id", Arrays.asList("kol1", "kol2")).
        join("join_table_1", "1_src_id", "id", Arrays.asList("kol3", "kol4")).
        whereSource(predicate);

    Connection conn = createStrictMock(Connection.class);

    String sql = "insert into dest(c1,c2,c3,c4) " +
        "select j0.kol1,j0.kol2,j1.kol3,j1.kol4 " +
        "from src s, join_table_0 j0,join_table_1 j1 " +
        "where j0.0_src_id = s.id " +
        "and j1.1_src_id = s.id " +
        "and s.predicate_column = 'predicate_value'";

    expect(conn.prepareStatement(sql)).andReturn(mockExecutablePreparedStatementForUpdate(1));
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }
}
