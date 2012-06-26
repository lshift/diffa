/**
 * Copyright (C) 2012 LShift Ltd.
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
package net.lshift.diffa.kernel.differencing

import org.junit.Test
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Assert._
import org.hamcrest.Matchers._
import org.junit.matchers.JUnitMatchers.containsString
import net.lshift.diffa.participant.scanning.ScanResultEntry
import scala.collection.JavaConversions._
import org.hamcrest.Matcher
import net.lshift.diffa.participant.common.InvalidEntityException
import net.lshift.diffa.participant.changes.ChangeEvent

class ScanResultEntityValidatorTest extends EntityValidatorTestChecks[ScanResultEntry] {
  val validator = EntityValidator
  val entityWithInvalidId = scanResultFor(id = INVALID_ID)
  val entityWithInvalidAttributes = scanResultFor(attributes = Map("property" -> INVALID_ID))
  val entityWithValidId = scanResultFor(id = VALID_ID)

  def process(e: ScanResultEntry) = {
    validator.process(e)
  }
  def scanResultFor(id: String = "id", attributes: Map[String, String] = Map()) = {
    new ScanResultEntry(id, null, null, attributes)
  }

  @Test def shouldBeValidWithNullId {
    val entity = scanResultFor(id = null)
    assertEquals("Handling entity %s".format(entity),
      None, exceptionOf(process(entity)))
  }

}


class ChangeEventValidatorTest extends EntityValidatorTestChecks[ChangeEvent] {
  val validator = EntityValidator
  val entityWithInvalidId = changeEventFor(id = INVALID_ID)
  val entityWithInvalidAttributes = changeEventFor(attributes = Map("property" -> INVALID_ID))
  val entityWithValidId = changeEventFor(id = VALID_ID)

  def process(e: ChangeEvent) = {
    validator.process(e)
  }
  def changeEventFor(id: String = "id", attributes: Map[String, String] = Map()) = {
    ChangeEvent.forChange(id, null, null, attributes)

  }


}



trait EntityValidatorTestChecks[T] {
  final val INVALID_ID: String = "\u26093"
  final val VALID_ID = "foo4_-,."

  val entityWithInvalidId: T
  val entityWithValidId: T
  val entityWithInvalidAttributes: T



  def exceptionForInvalidId = exceptionOf(process(entityWithInvalidId))

  def process(e: T): Unit

  @Test def shouldRaiseExceptionWithNonAsciiId {
      assertThatSome(exceptionForInvalidId, instanceOf(classOf[InvalidEntityException]))
  }

  def assertThatSome[T](exception: Option[T], matcher: Matcher[T]) =
    exception.map(assertThat(_, matcher)).getOrElse(fail("Recieved None when expecting Some(value)"))

  @Test def exceptionMessageShouldContainInvalidStringForId {

    assertThatSome(exceptionForInvalidId.map(_.getMessage), containsString(INVALID_ID))
  }

  @Test def shouldBeValidWithAlphaNumericStringForId {
    assertEquals("Handling entity %s".format(entityWithValidId),
      None, exceptionOf(process(entityWithValidId)))
  }





  @Test
  def shouldBeInvalidWithNonPrintablesInAttributeValues {
    assertThatSome(exceptionOf(process(entityWithInvalidAttributes)),
      is(instanceOf(classOf[InvalidEntityException])))
  }

  @Test
  def shouldReportNonPrintablesInAttributeValuesInException {
    assertThatSome(exceptionOf(process(entityWithInvalidAttributes)).map(_.getMessage),
      containsString(INVALID_ID))
  }

  protected def exceptionOf(thunk: => Unit): Option[Throwable] =
    try { thunk; None } catch { case e => Some(e) }
}