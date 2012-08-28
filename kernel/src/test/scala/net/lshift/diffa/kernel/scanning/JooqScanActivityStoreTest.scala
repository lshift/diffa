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
package net.lshift.diffa.kernel.scanning

import net.lshift.diffa.schema.environment.TestDatabaseEnvironments
import net.lshift.diffa.kernel.StoreReferenceContainer
import org.junit.{Test, AfterClass}
import org.junit.Assert._
import com.eaio.uuid.UUID
import org.joda.time.DateTime
import net.lshift.diffa.kernel.config.PairRef
import org.apache.commons.lang.RandomStringUtils

class JooqScanActivityStoreTest {

  private val storeReferences = JooqScanActivityStoreTest.storeReferences
  private val scanActivityStore = storeReferences.scanActivityStore
  private val systemConfigStore = storeReferences.systemConfigStore

  @Test
  def shouldBeAbleToReadInitialStatementAndThenUpdateIt {

    // Note the use of withMillisOfSecond(0) to avoid timestamp truncation in MySQL

    val domain = RandomStringUtils.randomAlphanumeric(10)

    val space = systemConfigStore.createOrUpdateSpace(domain)

    val pair = RandomStringUtils.randomAlphanumeric(10)
    val id = System.currentTimeMillis()

    val ref = PairRef(name = pair, space = space.id)

    // TODO It not strictly necessary to create this domain since there is no foreign key from the SCAN_STATEMENTS table
    // to the SPACES table, but generally speaking, we will only create new SCAN_STATEMENTS records when we have a valid
    // space, hence we do a lookup of the SPACE id when creating the statement record. If we don't create this domain
    // as part of this test, that lookup will fail and the test will fail.

    val originalStatement = ScanStatement(
      space = space.id,
      id = id,
      pair = pair,
      initiatedBy = Some("some-user"),
      startTime =  new DateTime().withMillisOfSecond(0)
    )

    scanActivityStore.createOrUpdateStatement(originalStatement)

    val firstRetrievedStatement = scanActivityStore.getStatement(ref, id)
    assertEquals(originalStatement, firstRetrievedStatement)

    val updatedStatement = originalStatement.copy(endTime = Some(new DateTime().withMillisOfSecond(0)), state = "COMPLETED")

    scanActivityStore.createOrUpdateStatement(updatedStatement)

    val secondRetrievedStatement = scanActivityStore.getStatement(ref, id)
    assertEquals(updatedStatement, secondRetrievedStatement)
  }

}

object JooqScanActivityStoreTest {
  private[JooqScanActivityStoreTest] val env =
    TestDatabaseEnvironments.uniqueEnvironment("target/domainConfigStore")

  private[JooqScanActivityStoreTest] val storeReferences =
    StoreReferenceContainer.withCleanDatabaseEnvironment(env)

  @AfterClass
  def tearDown {
    storeReferences.tearDown
  }
}
