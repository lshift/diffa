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

package net.lshift.diffa.snowflake.itest

import org.scalatest.FlatSpec
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import net.lshift.diffa.snowflake.{RandomIdentityGenerator, MachineIdAssigner, IdentityGenerator}

@RunWith(classOf[JUnitRunner])
class MachineIdAssignerSpec extends FlatSpec with ShouldMatchers {
  info("As an Application Developer")
  info("I want to create machine-specific ID generators")
  info("so that records are unique across all nodes in a cluster")

  val sessionTimeout = 6000

  implicit def idToFixedGenerator(id: Int): IdentityGenerator = new IdentityGenerator {
    def generate = id
  }
  def createAssigner = MachineIdAssigner.withSessionTimeout("localhost:2181", sessionTimeout)

  def withAssigner(fn: MachineIdAssigner => Unit) {
    val assigner = createAssigner
    fn(assigner)
    assigner.releaseAll()
  }

  "MachineIdAssigner" should "not assign an identifier after 30 consecutive collisions" in {
    withAssigner(prepAssigner => {
      withAssigner(assigner => {
        var genCount = 0
        val fixedId: Int = 7001
        val idGenerator = new IdentityGenerator {
          def generate = {
            genCount += 1
            fixedId
          }
        }

        // assign the identifier we'll collide with
        prepAssigner.assign(fixedId) should be(fixedId)

        val id = assigner.assign(idGenerator)
        genCount should be(30)
        id should be(-1)
      })
    })
  }

  it should "assign an identifier within one second in a sparsely populated identifier space" in {
    withAssigner(assigner => {
      val idGenerator = RandomIdentityGenerator.seeded(1L)
      // Given that there are fewer than 20 identifiers currently assigned
      (1 to 19).foreach { i =>
        assigner.assign(idGenerator)
      }

      val start = System.currentTimeMillis()
      // When I request an identifier
      assigner.assign(idGenerator) should not be(-1)
      // Then I should receive one within 1 second (1000ms)
      (System.currentTimeMillis() - start) should be <= 1000L
    })
  }

  it should "assign a released identifier" in {
    val fixedId: Int = 1873
    val idGenerator: IdentityGenerator = fixedId

    withAssigner { assigner =>
      assigner.assign(idGenerator) should be(fixedId)
    }
  }

  it should "assign the first 20 identifiers within 2 seconds" in {
    var k = 6789
    val idGenerator = new IdentityGenerator { def generate() = k }

    withAssigner { assigner =>
      val start = System.currentTimeMillis()

      (1 to 20) foreach { i =>
        k += 1
        assigner.assign(idGenerator) should be(k)
      }

      (System.currentTimeMillis() - start) should be <= 2000L
    }
  }

  it should "not assign a currently assigned identifier" in {
    val idGenerator = 11001
    val assigner = createAssigner

    withAssigner { prepAssigner =>
      prepAssigner.assign(idGenerator)

      assigner.assign(idGenerator) should be(-1)
    }
  }
}
