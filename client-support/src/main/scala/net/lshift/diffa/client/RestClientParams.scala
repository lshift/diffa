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

package net.lshift.diffa.client

case class RestClientParams(username: Option[String] = None,
                            password: Option[String] = None,
                            connectTimeout: Option[java.lang.Integer] = None,
                            readTimeout: Option[java.lang.Integer] = None) {

  /**
   * Constructor for Java interop
   */
  def this(username: String, password: String, connectTimeout: java.lang.Integer, readTimeout: java.lang.Integer) =
    this(Option(username), Option(password), Option(connectTimeout), Option(readTimeout))

  def hasCredentials = username.isDefined && password.isDefined
}

object RestClientParams {

  val default = RestClientParams(username = Some("guest"), password = Some("guest"))

}