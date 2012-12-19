/*
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

package net.lshift.diffa.kernel.config

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import net.lshift.diffa.kernel.StoreReferenceContainer
import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import net.lshift.diffa.kernel.frontend.EndpointDef
import system.SystemConfigStore
import java.lang.{Long => LONG}
import org.jooq.impl.Factory

@RunWith(classOf[JUnitRunner])
class EndpointCreationSpec extends FlatSpec with ShouldMatchers {
  info("In order to have a more reliable service in the event of faults")
  info("As a DaaS Operator")
  info("I want to run the Diffa service on a cluster of many nodes")

  val req1 = "When I create an endpoint, it should have a surrogate identifier capable of storing a 64-bit integer"
  val req2 = "When I create many endpoints, their surrogate identifiers should be unique"
  val req3 = "When I create two endpoints, the endpoint created last should have the greater valued identifier"
  val req4 = "When I create the same endpoint twice, the identifier should not change"

  import EndpointCreationSpec.configStore
  "Endpoint Provisioner" should "create an endpoint having a 64-bit surrogate identifier" in {
    val endpoint = "req1-1"

    CreateEndpointCommand(endpoint).executeWithConfigStore(configStore) should be > 0L
  }

  it should "provision unique endpoints with unique identifiers" in {
    val endpoints = ("req2-1", "req2-2")

    CreateEndpointCommand(endpoints._1).executeWithConfigStore(configStore) should not be(
      CreateEndpointCommand(endpoints._2).executeWithConfigStore(configStore))
  }

  it should "provision endpoints with successively greater identifier values" in {
    val endpoints = Seq("req3-1", "req3-2", "req3-3", "req3-z", "req3-a")
    var lastId = -1L

    endpoints foreach { e =>
      val nextId = CreateEndpointCommand(e).executeWithConfigStore(configStore)
      nextId should be > lastId
      lastId = nextId
    }
  }

  it should "not change the endpoint's identifier" in {
    val endpoint = "req4-1"
    val firstId = CreateEndpointCommand(endpoint).executeWithConfigStore(configStore)

    CreateEndpointCommand(endpoint).executeWithConfigStore(configStore) should be (firstId)
  }
}

object EndpointCreationSpec {
  private val spaceName = "EndpointCreationSpec"
  private val testEnv = TestDatabaseEnvironments.uniqueEnvironment("EndpointCreationSpec")
  private val storeRefs = StoreReferenceContainer.withCleanDatabaseEnvironment(testEnv)
  private val systemConfigStore: SystemConfigStore = storeRefs.systemConfigStore
  private[EndpointCreationSpec] val configStore: DomainConfigStore = storeRefs.domainConfigStore

  lazy val spaceId = systemConfigStore.createOrUpdateSpace(spaceName).id
  def executeWithFactory[T](fn: Factory => T) = storeRefs.executeWithFactory(fn)
}

case class CreateEndpointCommand(endpoint: String) {
  import EndpointCreationSpec.{spaceId, executeWithFactory}

  implicit def toEndpointDef(name: String): EndpointDef = EndpointDef(name = name)

  def executeWithConfigStore(configStore: DomainConfigStore): Long = {
    configStore.createOrUpdateEndpoint(spaceId, endpoint)
    executeWithFactory(factory =>
      JooqConfigStoreCompanion.endpointIdByName(factory, endpoint.name, spaceId))
  }
}
