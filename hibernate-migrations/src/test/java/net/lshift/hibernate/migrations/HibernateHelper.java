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

import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.easymock.EasyMock.*;

/**
 * Helper for various hibernate-edges within the test cases.
 */
public class HibernateHelper {
  private static Configuration config;

  public static Configuration configuration() {
    if (config == null) {
      config = new Configuration().
        setProperty("hibernate.dialect", "org.hibernate.dialect.HSQLDialect").
        setProperty("hibernate.connection.url", "jdbc:hsqldb:mem").
        setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbc.JDBCDriver");
    }

    return config;
  }

  public static PreparedStatement mockExecutablePreparedStatement() throws SQLException {
    PreparedStatement stmtMock = createStrictMock(PreparedStatement.class);
    expect(stmtMock.execute()).andReturn(true);
    stmtMock.close(); expectLastCall();
    replay(stmtMock);

    return stmtMock;
  }
}
