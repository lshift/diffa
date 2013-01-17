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

import org.jooq.SQLDialect;

public class TestDBProvider {
  public static TestSchema createSchema() {
    TestSchema schema = getSchema(
        getDialect(),
        System.getProperty("jdbcUsername"),
        System.getProperty("jdbcPass"),
        System.getProperty("jdbcUrl"));
    try {
      schema.create();
      schema.migrate();
    } catch (Exception e) {
      e.printStackTrace();
    }

    return schema;
  }

  private static TestSchema getSchema(SQLDialect dialect, String username, String password, String jdbcUrl) {
    switch (dialect) {
      case ORACLE: return new OracleTestSchema(username, password, jdbcUrl);
      default: return new HsqldbTestSchema(jdbcUrl);
    }
  }

  public static SQLDialect getDialect() {
    return SQLDialect.valueOf(System.getProperty("jooqDialect", "HSQLDB"));
  }
}
