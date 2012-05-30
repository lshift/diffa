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

import net.lshift.hibernate.migrations.dialects.DefaultDialectExtension;
import net.lshift.hibernate.migrations.dialects.DialectExtension;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.HSQLDialect;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Types;
import java.util.HashMap;

import static net.lshift.hibernate.migrations.HibernateHelper.mockExecutablePreparedStatement;
import static org.easymock.EasyMock.*;

public class TransplantTableBuilderTest {

  @Test
  public void shouldCopyIntoTemporaryTableWithRenames() throws Exception {
    Configuration config = HibernateHelper.configuration();
    Dialect dialect = new HSQLDialect();
    DialectExtension extension = new DefaultDialectExtension();

    MigrationBuilder mb = new MigrationBuilder(config);

    CreateTableBuilder tempTable = new CreateTableBuilder(dialect, extension, "temp_table", "new_foo");
    tempTable.column("new_foo", Types.INTEGER, false);
    tempTable.column("bar", Types.VARCHAR, 255, false);

    HashMap renames = new HashMap();
    renames.put("new_foo", "old_foo");

    mb.alterTableViaTemporaryTable("source_table", tempTable, renames);

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("create table temp_table (new_foo integer not null, bar varchar(255) not null, primary key (new_foo))")).andReturn(mockExecutablePreparedStatement());
    expect(conn.prepareStatement("insert into temp_table(new_foo,bar) select old_foo,bar from source_table")).andReturn(mockExecutablePreparedStatement());
    expect(conn.prepareStatement("drop table source_table")).andReturn(mockExecutablePreparedStatement());
    expect(conn.prepareStatement("alter table temp_table rename to source_table")).andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldCopyIntoTemporaryTableWithoutRenames() throws Exception {
    Configuration config = HibernateHelper.configuration();
    Dialect dialect = new HSQLDialect();
    DialectExtension extension = new DefaultDialectExtension();

    MigrationBuilder mb = new MigrationBuilder(config);

    CreateTableBuilder tempTable = new CreateTableBuilder(dialect, extension, "temp_table", "foo");
    tempTable.column("foo", Types.INTEGER, false);
    tempTable.column("bar", Types.VARCHAR, 255, false);

    mb.alterTableViaTemporaryTable("source_table", tempTable);

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("create table temp_table (foo integer not null, bar varchar(255) not null, primary key (foo))")).andReturn(mockExecutablePreparedStatement());
    expect(conn.prepareStatement("insert into temp_table(foo,bar) select foo,bar from source_table")).andReturn(mockExecutablePreparedStatement());
    expect(conn.prepareStatement("drop table source_table")).andReturn(mockExecutablePreparedStatement());
    expect(conn.prepareStatement("alter table temp_table rename to source_table")).andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

}
