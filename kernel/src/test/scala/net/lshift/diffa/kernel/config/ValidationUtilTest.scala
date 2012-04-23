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

package net.lshift.diffa.kernel.config

import org.junit.Test
import net.lshift.diffa.kernel.config.ConfigValidationException

/**
 * Tests for ValidationUtil.
 */
class ValidationUtilTest {
  def urlValidOrInvalid(input: String): Boolean = {
    var result = try {
      ValidationUtil.ensureSettingsURLFormat("ValidationUtilTest", input)
    } catch {
      case cve: ConfigValidationException => false
    }

    return result
  }

  def testURLisValid(input: String) {
    assert(urlValidOrInvalid(input) == true)
  }

  def testURLisInvalid(input: String) {
    assert(urlValidOrInvalid(input) == false)
  }
  
  @Test
  def httpURLShouldValidate() {
    testURLisValid("http://www.google.com/")
  }
  
  @Test
  def httpsURLShouldValidate() {
    testURLisValid("https://www.google.com/")
  }
  
  @Test
  def amqpURLShouldValidate() {
    testURLisValid("amqp://foo/bar")
  }

  @Test
  def invalidURLsShouldNotValidate() {
    val urls = Array("foo://bar", "ftp://")
    urls.foreach(testURLisInvalid(_))
  }
}
