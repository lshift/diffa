/**
 * Copyright (C) 2010 - 2012 LShift Ltd.
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
import java.util.Arrays;

import static net.lshift.hibernate.migrations.HibernateHelper.mockExecutablePreparedStatement;
import static org.easymock.EasyMock.*;

public class CopyTableBuilderTest {
  
  @Test
  public void shouldCopyColumns() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());

    Iterable<String> sourceCols = Arrays.asList("foo", "bar", "baz");
    Iterable<String> destCols = Arrays.asList("foo", "bar2", "baz");
    mb.copyTableContents("src", "dest", sourceCols, destCols);

    Connection conn = createStrictMock(Connection.class);

    expect(conn.prepareStatement("insert into dest(foo,bar2,baz) select foo,bar,baz from src")).andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }
}
