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
  private Iterable<String> sourceCols;
  private Iterable<String> destCols;

  public CopyTableBuilder(String source, String destination, Iterable<String> sourceCols, Iterable<String> destCols) {
    this.sourceTable = source;
    this.destinationTable = destination;
    this.sourceCols = sourceCols;
    this.destCols = destCols;
  }
  
  @Override
  protected String getSQL() {
    Joiner joiner = Joiner.on(",").skipNulls();
    String sourceColumnNames = joiner.join(sourceCols);
    String destColumnNames = joiner.join(destCols);
    return String.format("insert into %s(%s) select %s from %s",
                         destinationTable, destColumnNames, sourceColumnNames, sourceTable);
  }
}
