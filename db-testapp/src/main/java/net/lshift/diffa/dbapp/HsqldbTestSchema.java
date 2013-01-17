/*
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

package net.lshift.diffa.dbapp;

import org.apache.commons.lang3.RandomStringUtils;
import org.jooq.SQLDialect;

/**
 */
public class HsqldbTestSchema extends TestSchema {
  private final String jdbcUrl;

  public HsqldbTestSchema(String jdbcUrl) {
    if (jdbcUrl == null) {
      this.jdbcUrl = "jdbc:hsqldb:mem:" + RandomStringUtils.randomAlphabetic(5);
    } else {
      this.jdbcUrl = jdbcUrl;
    }
  }

  @Override
  public SQLDialect dialect() {
    return SQLDialect.HSQLDB;
  }

  @Override
  public String driverClass() {
    return "org.hsqldb.jdbc.JDBCDriver";
  }

  @Override
  public String getJdbcUrl() {
    return jdbcUrl;
  }

  @Override
  protected String tableOfThingsDDL() {
    return "create table things (id varchar(32) primary key, version varchar(32) not null, entry_date date)";
  }

  @Override
  protected String md5FunctionDDL() {
    return "create function md5(v varchar(32672)) returns varchar(32)" +
        "  language java deterministic no sql" +
        "  external name 'CLASSPATH:org.apache.commons.codec.digest.DigestUtils.md5Hex'";
  }

  @Override
  public String dbUsername() {
    return "sa";
  }

  @Override
  public String dbPassword() {
    return "";
  }
}
