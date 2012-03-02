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

/**
 * Test cases for the alter table builder.
 */
public class AlterTableBuilderTest {
  @Test
  public void shouldDropColumnUsingCapitalizedName() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("some_table").dropColumn("some_col");

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("alter table some_table drop column \"SOME_COL\"")).andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldGenerateExceptionWhenAddingColumnThatIsNonNullableAndHasNoDefaultValue() {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("some_table").addColumn("some_col", Types.VARCHAR, 255, false, null);
  }

  @Test
  public void shouldGenerateAddColumn() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").addColumn("bar", Types.VARCHAR, 255, false, "baz");
    VerificationUtil.verifyMigrationBuilder(mb, "alter table foo add column bar varchar(255) default 'baz' not null");
  }

  @Test
  public void shouldGenerateAlterColumn() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").alterColumn("bar", Types.VARCHAR, 1024, false, "baz");
    VerificationUtil.verifyMigrationBuilder(mb, "alter table foo alter column bar varchar(1024) default 'baz' not null");
  }
  
  @Test
  public void shouldSetColumnNullable() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").setColumnNullable("bar", Types.VARCHAR, 255, true);
    VerificationUtil.verifyMigrationBuilder(mb, "alter table foo alter column bar set null");
  }
  
  @Test
  public void shouldSetColumnNotNull() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").setColumnNullable("bar", Types.VARCHAR, 255, false);
    VerificationUtil.verifyMigrationBuilder(mb, "alter table foo alter column bar set not null");
  }

  @Test
  public void shouldGenerateForeignKeyConstraint() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").addForeignKey("FK80C74EA1C3C204DC", "bar", "baz", "name");

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("alter table foo  add constraint FK80C74EA1C3C204DC foreign key (bar) references baz")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldGenerateSingleUniqueKeyConstraint() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").addUniqueConstraint("blaz");

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("alter table foo add unique (blaz)")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldGenerateMultipleUniqueKeyConstraint() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").addUniqueConstraint("U123123", "blaz", "boz");

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("alter table foo  add constraint U123123 unique (blaz, boz)")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldGenerateDropConstraint() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").dropConstraint("FK80C74EA1C3C204DC");

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("alter table foo drop constraint FK80C74EA1C3C204DC")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldGenerateDropForeignKey() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").dropConstraint("FK80C74EA1C3C204DC");

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("alter table foo drop constraint FK80C74EA1C3C204DC")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldGenerateDropPrimaryKey() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").dropPrimaryKey();

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("alter table foo drop primary key")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldGenerateAddPrimaryKey() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").addPrimaryKey("bar", "baz");

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("alter table foo add primary key (bar, baz)")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }
  
  @Test
  public void shouldGenerateReplacePrimaryKey() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").replacePrimaryKey("bar", "baz");
    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("alter table foo drop primary key")).
        andReturn(mockExecutablePreparedStatement());
    expect(conn.prepareStatement("alter table foo add primary key (bar, baz)")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);
    
    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldGenerateAddPartitionForHashing() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").addPartition();

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("alter table foo add partition")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }

  @Test
  public void shouldRenameTable() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());
    mb.alterTable("foo").renameTo("bar");

    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement("alter table foo rename to bar")).
        andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }
}
