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

package net.lshift.diffa.kernel.frontend

import reflect.BeanProperty
import java.security.MessageDigest
import org.apache.commons.codec.binary.Hex
import net.lshift.diffa.kernel.config.ValidationUtil


/**
 * Serializable representation of a user within the context of a domain.
 */
case class UserDef(@BeanProperty var name: String = null,
                   @BeanProperty var email: String = null,
                   @BeanProperty var superuser: Boolean = false,
                   @BeanProperty var password: String = null,
                   @BeanProperty var external: Boolean = false) {

  private val encodedPasswordPrefix = "sha256:"

  def this() = this(name = null)

  def validate(path:String = null) {
    val userPath = ValidationUtil.buildPath(path, "user", Map("name" -> name))

    ValidationUtil.requiredAndNotEmpty(userPath, "name", name)
    ValidationUtil.requiredAndNotEmpty(userPath, "email", email)

    if (!external) {
      ValidationUtil.requiredAndNotEmpty(userPath, "password", password)
    }
  }

  /**
   * Returns the encoded password. If the password field starts with a prefix indicating that it is already encoded,
   * then we just strip the prefix. If the prefix isn't present, then we'll SHA-256 encode the password.
   */
  def passwordEnc:String = if (password == null) {
    ""    // Return an empty password string if the user didn't specify one. Since the password is SHA-256 encoded,
          // an empty string is unachievable, so this makes the account unable to be authenticated with via a password.
  } else if (password.startsWith(encodedPasswordPrefix)) {
    password.substring(encodedPasswordPrefix.length())
  } else {
    sha256Encode(password)
  }
  def passwordEnc_=(s:String) {
    if (s == null || s == "") {
      password = null
      external = true
    } else {
      password = encodedPasswordPrefix + s
    }
  }

  private def sha256Encode(s:String) = {
    val md = MessageDigest.getInstance("SHA-256")
    new String(Hex.encodeHex(md.digest(s.getBytes("UTF-8")), true))
  }
}