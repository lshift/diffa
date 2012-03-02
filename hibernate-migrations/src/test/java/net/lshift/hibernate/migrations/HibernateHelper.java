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

import org.hibernate.cfg.Configuration;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.*;

/**
 * Helper for various hibernate-edges within the test cases.
 */
public class HibernateHelper {
  public static final String HSQL_DIALECT = "org.hibernate.dialect.HSQLDialect";
  public static final String ORACLE_DIALECT = "org.hibernate.dialect.Oracle10gDialect";
  public static final String MYSQL_DIALECT = "org.hibernate.dialect.MySQL5Dialect";

  private static Map<String, Configuration> config = new HashMap<String, Configuration>();

  public static Configuration configuration() {
    return configuration(HSQL_DIALECT);
  }
  public static Configuration configuration(String dialect) {
    if (config.get(dialect) == null) {
      config.put(dialect, new Configuration().
        setProperty("hibernate.dialect", dialect).
        setProperty("hibernate.connection.url", "jdbc:hsqldb:mem").
        setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbc.JDBCDriver"));
    }

    return config.get(dialect);
  }

  public static PreparedStatement mockExecutablePreparedStatement() throws SQLException {
    PreparedStatement stmtMock = createStrictMock(PreparedStatement.class);
    expect(stmtMock.execute()).andReturn(true);
    stmtMock.close(); expectLastCall();
    replay(stmtMock);

    return stmtMock;
  }

  public static CallableStatement mockExecutableCallableStatement() throws SQLException {
    CallableStatement stmtMock = createStrictMock(CallableStatement.class);
    expect(stmtMock.execute()).andReturn(true);
    stmtMock.close(); expectLastCall();
    replay(stmtMock);

    return stmtMock;
  }
}
