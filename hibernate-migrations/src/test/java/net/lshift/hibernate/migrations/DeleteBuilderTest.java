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

import org.junit.Test;

import java.sql.Connection;
import java.sql.Types;

import static net.lshift.hibernate.migrations.HibernateHelper.mockExecutablePreparedStatement;
import static org.easymock.EasyMock.*;

public class DeleteBuilderTest {

  @Test
  public void shouldDeleteFromTableWithWhereClause() throws Exception {

    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.delete("some_table").where("some_column").is("foo");

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("delete from some_table where some_column = 'foo'")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldDeleteFromTableWithWithoutClause() throws Exception {

    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.delete("some_table");

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("delete from some_table")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldGenerateExceptionWhenForgettingWhereColumn() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.delete("some_table").is("foo");
    Connection conn = createStrictMock(Connection.class);
    mb.apply(conn);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldGenerateExceptionWhenForgettingWhereValue() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.delete("some_table").where("foo");
    Connection conn = createStrictMock(Connection.class);
    mb.apply(conn);
  }
}
