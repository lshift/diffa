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

import net.lshift.hibernate.migrations.HibernateHelper;
import net.lshift.hibernate.migrations.MigrationBuilder;
import org.junit.Test;

import java.sql.Types;

import static net.lshift.hibernate.migrations.VerificationUtil.verifyMigrationBuilder;

/**
 * Validates support for the oracle dialect.
 */
public class OracleDialectSupportTest {

  @Test
  public void shouldAlterColumnUsingModifySyntax() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration(HibernateHelper.ORACLE_DIALECT));
    mb.alterTable("foo").alterColumn("bar", Types.VARCHAR, 1024, true, null);
    verifyMigrationBuilder(mb, "alter table foo modify (bar varchar2(1024 char))");
  }

  @Test
  public void shouldAddColumnUsingWithoutColumnKeyword() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration(HibernateHelper.ORACLE_DIALECT));
    mb.alterTable("foo").addColumn("bar", Types.BIT, 1, true, 0);
    verifyMigrationBuilder(mb, "alter table foo add bar number(1,0) default 0");
  }
}
