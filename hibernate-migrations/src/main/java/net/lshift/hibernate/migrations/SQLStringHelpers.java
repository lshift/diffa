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
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;

import java.sql.Types;

/**
 * Helpers for various string manipulation operations.
 */
public class SQLStringHelpers {

  /**
   * Generates an identity column string
   * @throws UnsupportedOperationException If the underlying dialect does not support identity columns*
  */
  public static String generateIdentityColumnString(Dialect dialect, Column col) {

    if (!dialect.supportsIdentityColumns()) {
      String dialectName = Environment.getProperties().getProperty( Environment.DIALECT );
      throw new UnsupportedOperationException(dialectName + " does not support identity columns");
    }

    StringBuilder buffer = new StringBuilder();

    buffer.append(col.getQuotedName(dialect)).append(" ");

    // to support dialects that have their own identity data type
    if (dialect.hasDataTypeInIdentityColumn()) {
      buffer.append(getTypeName(dialect, col));
    }
    buffer.append(' ').append(dialect.getIdentityColumnString(col.getSqlTypeCode()));

    return buffer.toString();
  }

  public static String generateNonIdentityColumnString(Dialect dialect, Column col) {

    StringBuilder buffer = new StringBuilder();

    buffer.append(col.getQuotedName(dialect)).append(" ");
    buffer.append(getTypeName(dialect, col));

    buffer.append(" not null");

    return buffer.toString();
  }

  public static String generateColumnString(Dialect dialect, Column col, boolean newTable) {
    StringBuilder buffer = new StringBuilder();

    buffer.append(col.getQuotedName(dialect)).append(" ");
    buffer.append(getTypeName(dialect, col));

    if (!newTable && col.getDefaultValue() == null && !col.isNullable()) {
      throw new IllegalArgumentException("Cannot have a null default value for a non-nullable column when altering a table: " + col);
    }
    if (col.getDefaultValue() != null) {
      String defaultQuote;

      buffer.append(" default ");
      if (col.getSqlTypeCode() == Types.VARCHAR)
        defaultQuote = "'";
      else
        defaultQuote = "";

      buffer.append(defaultQuote);
      buffer.append(col.getDefaultValue());
      buffer.append(defaultQuote);
    }

    // HSQL Doesn't like the not null coming before the default stanza
    if (!col.isNullable()) {
      buffer.append(" not null");
    }

    return buffer.toString();
  }

  private static String getTypeName(Dialect dialect, Column col) {
    return dialect.getTypeName(col.getSqlTypeCode(), col.getLength(), col.getPrecision(), col.getScale());
  }

  public static String qualifyName(Configuration config, Dialect dialect, String table) {
    String defaultCatalog = config.getProperties().getProperty(Environment.DEFAULT_CATALOG);
    String defaultSchema = config.getProperties().getProperty(Environment.DEFAULT_SCHEMA);
    return new Table(table).getQualifiedName(dialect,defaultCatalog, defaultSchema);
  }
}
