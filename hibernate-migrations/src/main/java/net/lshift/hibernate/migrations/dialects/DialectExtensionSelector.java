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
package net.lshift.hibernate.migrations.dialects;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MySQL5Dialect;
import org.hibernate.dialect.Oracle10gDialect;
import org.hibernate.dialect.Oracle8iDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for selecting an appropriate dialect extension for a given dialect.
 */
public class DialectExtensionSelector {

  static Logger log = LoggerFactory.getLogger(DialectExtensionSelector.class);

  public static DialectExtension select(Dialect hibernateDialect) {
    if (hibernateDialect instanceof Oracle8iDialect) {
      return new OracleDialectExtension();
    }

    if (hibernateDialect instanceof Oracle10gDialect) {
      return new OracleDialectExtension();
    }

    if (hibernateDialect instanceof MySQL5Dialect) {
        return new MySQL5DialectExtension();
    }

    return new DefaultDialectExtension();
  }

  public static DialectExtension select(String dialect) {

    log.info("Using dialect: " + dialect);

    if (dialect.equalsIgnoreCase("Oracle"))  {
      return new OracleDialectExtension();
    }

    if (dialect.equalsIgnoreCase("MySQL"))  {
      return new MySQL5DialectExtension();
    }

    return new DefaultDialectExtension();
  }
}
