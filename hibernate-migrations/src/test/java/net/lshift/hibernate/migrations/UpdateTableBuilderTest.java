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


import org.junit.Test;

import java.sql.Connection;

import static net.lshift.hibernate.migrations.HibernateHelper.mockExecutablePreparedStatement;
import static org.easymock.EasyMock.*;

public class UpdateTableBuilderTest {

  @Test
  public void shouldUpdateTableWithoutPredicateWithNullValue() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.updateTable("some_table").updateColumn("foo").withNullValue();

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("update some_table set foo = null")).andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldUpdateTableWithoutPredicate() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.updateTable("some_table").updateColumn("foo").withValue("bar");

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("update some_table set foo = 'bar'")).andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldUpdateTableWithPredicate() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.updateTable("some_table")
      .updateColumn("foo")
      .withValue("bar")
      .predicate("first_column", "first_value")
      .predicate("second_column", "second_value");

    Connection conn = createStrictMock(Connection.class);
    String sql = "update some_table set foo = 'bar' where first_column = 'first_value' and second_column = 'second_value'";
    expect(conn.prepareStatement(sql)).andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldUpdateTableWithPredicateWithNullValue() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.updateTable("some_table")
        .updateColumn("foo")
        .withNullValue()
        .predicate("first_column", "first_value")
        .predicate("second_column", "second_value");

    Connection conn = createStrictMock(Connection.class);
    String sql = "update some_table set foo = null where first_column = 'first_value' and second_column = 'second_value'";
    expect(conn.prepareStatement(sql)).andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldUpdateTableWithSelectValue() throws Exception {
    SelectBuilder sb = new SelectBuilder().select("third_column")
                                          .from("another_table")
                                          .where("fourth_column")
                                          .is("fourth_value");
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.updateTable("some_table")
      .updateColumn("foo")
      .withSelect(sb)
      .predicate("first_column", "first_value")
      .predicate("second_column", "second_value");

    Connection conn = createStrictMock(Connection.class);
    String sql = "update some_table set foo = ( select third_column from another_table where fourth_column = 'fourth_value' ) where first_column = 'first_value' and second_column = 'second_value'";
    expect(conn.prepareStatement(sql)).andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldGenerateExceptionWhenYouForgetToSetColumnToUpdate() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.updateTable("some_table");

    Connection conn = createStrictMock(Connection.class);
    mb.apply(conn);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldGenerateExceptionWhenYouSupplyNullValueToUpdate() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.updateTable("some_table").withValue(null);

    Connection conn = createStrictMock(Connection.class);
    mb.apply(conn);
  }
}
