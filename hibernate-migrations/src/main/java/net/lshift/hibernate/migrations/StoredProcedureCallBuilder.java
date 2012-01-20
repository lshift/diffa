/**
 * Copyright (C) 2012 LShift Ltd.
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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes a stored procedure.
 */
public class StoredProcedureCallBuilder implements MigrationElement {
  private List<String> statements = new ArrayList<String>();
  private String name;

  public StoredProcedureCallBuilder(String name) {
    this.name = name;
  }

  @Override
  public void apply(Connection conn) throws SQLException {
    CallableStatement callable = conn.prepareCall("{call " + name + "}");

    statements.add("{call " + name + "}");
    callable.execute();
    callable.close();
  }

  @Override
  public List<String> getStatements() {
    return statements;
  }
}
