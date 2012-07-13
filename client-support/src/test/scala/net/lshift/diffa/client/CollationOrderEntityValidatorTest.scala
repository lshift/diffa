/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.client

import net.lshift.diffa.participant.scanning.{OutOfOrderException, AsciiCollation, ScanResultEntry}
import org.joda.time.{DateTimeZone, DateTime}
import java.io.{ByteArrayInputStream, InputStream}
import org.junit.Test
import org.hamcrest.Matchers._
import org.junit.Assert._
import scala.collection.JavaConversions._
import net.lshift.diffa.participant.common.ScanEntityValidator
import org.easymock.EasyMock._

class CollationOrderEntityValidatorTest { self =>
  val entity1 = ScanResultEntry.forEntity("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC), Map("a1" -> "a1v1"))
  val entity2 = ScanResultEntry.forEntity("id2", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC), Map("a1" -> "a1v1"))
  val entity3 = ScanResultEntry.forEntity("id3", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC), Map("a1" -> "a1v1"))

  val aggregates = Seq(ScanResultEntry.forAggregate("version0", Map[String, String]()),
                        ScanResultEntry.forAggregate("Another version", Map[String, String]()))

  // A dummy collation that sorts backwards for verification purposes. You
  // would only ever use something like this for testing, AFAICS. --CS
  lazy val reversedAsciiCollation = new AsciiCollation {
    override def sortsBefore(a: String, b:String) = super.sortsBefore(b, a)
  }

  val nextValidator = createMock(classOf[ScanEntityValidator])

  val emptyResponseContent = "[" + (" " * 40) + "]"
  lazy val emptyResponse = new ByteArrayInputStream(emptyResponseContent.getBytes("UTF8"))

  val reversedEntities = Seq(entity3, entity2, entity1)
  val misorderedEntities = Seq(entity3, entity1, entity2)

  @Test
  def shouldReturnWrappedParserResponseWhenCorrectlyOrdered {
    val validator = new CollationOrderEntityValidator(reversedAsciiCollation)
    reversedEntities.foreach(validator.process _)
  }


  @Test(expected=classOf[OutOfOrderException])
  def shouldRaiseErrorWhenWronglyOrdered {
    val validator = new CollationOrderEntityValidator(reversedAsciiCollation)
    misorderedEntities.foreach(validator.process _)
  }

  @Test
  def shouldPassEntityToChainedValidator {
    val validator = new CollationOrderEntityValidator(reversedAsciiCollation, nextValidator)
    reversedEntities.foreach { e:ScanResultEntry => expect(nextValidator.process(e)) }
    replay(nextValidator)
    reversedEntities.foreach(validator.process _)
    verify(nextValidator)
  }

  @Test
  def shouldPassAggregateResultsThroughToChainedValidator {
    val validator = new CollationOrderEntityValidator(reversedAsciiCollation, nextValidator)
    aggregates.foreach { e:ScanResultEntry => expect(nextValidator.process(e)) }
    replay(nextValidator)
    aggregates.foreach(validator.process _)
    verify(nextValidator)

  }
}