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

import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;
import org.apache.commons.lang3.RandomStringUtils;
import org.jooq.SQLDialect;
import org.jooq.impl.Factory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 */
public class OracleTestSchema extends TestSchema {
  private String adminUsername;
  private String adminPassword;
  private String jdbcUrl;

  public OracleTestSchema(String adminUsername, String adminPassword, String jdbcUrl) {
    this.adminUsername = adminUsername;
    this.adminPassword = adminPassword;
    this.jdbcUrl = jdbcUrl;
  }

  @Override
  public void create() throws SQLException {
    BoneCPConfig config = new BoneCPConfig();
    config.setJdbcUrl(System.getProperty("jdbcUrl"));
    config.setUsername(adminUsername);
    config.setPassword(adminPassword);

    BoneCPDataSource ds = new BoneCPDataSource(config);
    ds.setDriverClass(System.getProperty("jdbcDriverClass"));

    Connection connection = ds.getConnection();
    Factory factory = new Factory(connection, dialect());
    factory.execute(String.format("create user %s identified by %s", dbUsername(), dbPassword()));
    factory.execute(String.format("grant dba to %s", dbUsername()));
    connection.commit();
    connection.close();
    ds.close();
  }

  @Override
  public SQLDialect dialect() {
    return SQLDialect.ORACLE;
  }

  @Override
  public String driverClass() {
    return "oracle.jdbc.OracleDriver";
  }

  @Override
  public String getJdbcUrl() {
    return jdbcUrl;
  }

  private final String username = "SQLDRV" + RandomStringUtils.randomAlphabetic(5);
  @Override
  public String dbUsername() {
    return username;
  }

  @Override
  public String dbPassword() {
    return "sqldriverit";
  }

  @Override
  protected String tableOfThingsDDL() {
    return "create table things (id varchar2(32) primary key, version varchar2(32) not null, entry_date date)";
  }

  @Override
  protected String md5FunctionDDL() {
    return "create or replace function md5(input_string varchar2) " +
        "return varchar2 " +
        "is " +
        "begin " +
        "        declare " +
        "                h_string varchar2(255); " +
        "        begin " +
        "                dbms_obfuscation_toolkit.md5(input_string => input_string, checksum_string => h_string); " +
        "                return lower(rawtohex(utl_raw.cast_to_raw(h_string))); " +
        "        end; " +
        "end; ";
  }
}
