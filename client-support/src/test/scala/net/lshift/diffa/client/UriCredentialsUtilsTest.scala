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

package net.lshift.diffa.client

import org.junit.runner.RunWith
import junit.framework.Test
import com.sun.jersey.core.util.MultivaluedMapImpl
import net.lshift.diffa.kernel.config.QueryParameterCredentials
import org.junit.experimental.theories.{DataPoint, Theory, Theories}
import org.junit.Assert._

@RunWith(classOf[Theories])
class UriCredentialsUtilsTest {
  import UriCredentialsUtilsTest._
  val uri = "/foo?bar"

  @Theory
  def testUriConstruction(scenario: Scenario) = {
    assertEquals(scenario.expectedResult,
      UriCredentialsUtil.buildQueryUri(scenario.baseUri, scenario.query, None))
  }
}

object UriCredentialsUtilsTest {

  case class Scenario(baseUri: String,
                      query: MultivaluedMapImpl,
                      expectedResult: String)

  val nullQueryParameters = new MultivaluedMapImpl
  val basicQueryParameters = new MultivaluedMapImpl
  basicQueryParameters.add("query", "value")

  @DataPoint def nullExample = Scenario("/test", nullQueryParameters, "/test")
  @DataPoint def singleQueryParam = Scenario("/test", basicQueryParameters, "/test?query=value")
  @DataPoint def queryInBase = Scenario("/test?foo=bar", basicQueryParameters, "/test?foo=bar&query=value")
}