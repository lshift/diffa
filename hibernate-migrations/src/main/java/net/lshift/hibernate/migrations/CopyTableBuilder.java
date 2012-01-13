/**
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
package net.lshift.hibernate.migrations;

import com.google.common.base.Joiner;

public class CopyTableBuilder extends SingleStatementMigrationElement{

  private String sourceTable;
  private String destinationTable;
  private Iterable<String> columnsToCopy;

  public CopyTableBuilder(String source, String destination, Iterable<String> columns) {
    sourceTable = source;
    destinationTable = destination;
    columnsToCopy = columns;
  }
  
  @Override
  protected String getSQL() {
    Joiner joiner = Joiner.on(",").skipNulls();
    String columns = joiner.join(columnsToCopy);
    return String.format("insert into %s(%s) select %s from %s",
                         destinationTable, columns, columns, sourceTable);
  }
}
