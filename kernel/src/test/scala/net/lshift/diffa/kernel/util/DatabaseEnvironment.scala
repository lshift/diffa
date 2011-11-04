/**
 * Copyright (C) 2010-2011 LShift Ltd.
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

package net.lshift.diffa.kernel.util

/**
 * Re-useable way of access the DB env
 */
object DatabaseEnvironment {

  val URL = System.getProperty("diffa.jdbc.url", "jdbc:hsqldb:mem")
  val DIALECT = System.getProperty("diffa.hibernate.dialect", "org.hibernate.dialect.HSQLDialect")
  val DRIVER = System.getProperty("diffa.jdbc.driver", "org.hsqldb.jdbc.JDBCDriver")
  val USERNAME = System.getProperty("diffa.jdbc.username", "SA")
  val PASSWORD = System.getProperty("diffa.jdbc.password", "")

  /**
   * The motivation behind this URL builder is because ATM, unless you run mvn clean, the tests
   * leave data behind which gets picked up by subsequent test runs.
   *
   * TODO Fix this behavior.
   */
  def substitutableURL(path:String) = {
    val url = System.getProperty("diffa.jdbc.url", "jdbc:hsqldb:mem:%s")
    if (url.contains("%s")) {
      url.format(path)
    }
    else {
      url
    }
  }
}