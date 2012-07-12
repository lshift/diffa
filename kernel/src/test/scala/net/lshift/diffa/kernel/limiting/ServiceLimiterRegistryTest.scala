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
package net.lshift.diffa.kernel.limiting

import org.junit.Assert
import org.junit.runner.RunWith
import org.junit.experimental.theories.{Theories, DataPoints, Theory}
import net.lshift.diffa.schema.servicelimits.ServiceLimit

@RunWith(classOf[Theories])
class ServiceLimiterRegistryTest {
  import net.lshift.diffa.kernel.limiting.ServiceLimiterRegistryTest.Scenario

  @Theory
  def shouldReturnALimiterFromAnEmptyRegistryOnGet(scenario: Scenario) {
    ServiceLimiterRegistry.clear()
    val limiter = ServiceLimiterRegistry.get(scenario.key, scenario.limiterFn)
    Assert.assertFalse("The limiter created should respond to accept()", limiter.accept())
  }

  @Theory
  def shouldReturnTheRegisteredLimiterOnGet(scenario: Scenario) {

  }
}

object ServiceLimiterRegistryTest {
  case class Scenario(key: ServiceLimiterKey, limiterFn: () => Limiter)

  val dummyServiceLimit = new ServiceLimit {
    def key = "a-dummy-key"
    def description = "a dummy key"
    def defaultLimit = 5
    def hardLimit = 6
  }

  val dummyLimiter = new Limiter {
    def accept() = false
  }

  @DataPoints
  def limiters = Array(
    Scenario(ServiceLimiterKey(dummyServiceLimit, None, None), {() => dummyLimiter}),
    Scenario(ServiceLimiterKey(dummyServiceLimit, Some("dom1"), None), {() => dummyLimiter}),
    Scenario(ServiceLimiterKey(dummyServiceLimit, None, Some("pair1")), {() => dummyLimiter}),
    Scenario(ServiceLimiterKey(dummyServiceLimit, Some("dom1"), Some("pair1")), {() => dummyLimiter})
  )
}
