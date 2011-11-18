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

import java.sql.Connection;
import java.sql.SQLException;

import static net.lshift.hibernate.migrations.HibernateHelper.mockExecutablePreparedStatement;
import static org.easymock.EasyMock.*;

/**
 * Utility for verification of SQL statements.
 */
public class VerificationUtil {
  public static void verifyMigrationBuilder(MigrationBuilder mb, String sql) throws SQLException {
    Connection conn = createStrictMock(Connection.class);
    expect(conn.prepareStatement(sql)).andReturn(mockExecutablePreparedStatement());
    replay(conn);
    mb.apply(conn);
    verify(conn);
  }
}
