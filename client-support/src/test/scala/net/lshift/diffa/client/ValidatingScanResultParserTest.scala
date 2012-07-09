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

import org.junit.Test
import org.hamcrest.Matchers._
import org.junit.Assert._
import java.io.{InputStreamReader, BufferedReader, InputStream, ByteArrayInputStream}
import net.lshift.diffa.participant.scanning.{OutOfOrderException, AsciiCollation, Collation, ScanResultEntry}
import org.joda.time.{DateTimeZone, DateTime}
import scala.collection.JavaConversions._
import net.lshift.diffa.participant.common.ScanEntityValidator
import org.easymock.EasyMock._
import net.lshift.diffa.kernel.config.{DiffaPairRef, PairServiceLimitsView}
import net.lshift.diffa.schema.servicelimits.ScanResponseSizeLimit
import net.lshift.diffa.kernel.differencing.ScanLimitBreachedException

class ValidatingScanResultParserTest {

  def streamFor(s: String) = new ByteArrayInputStream(s.getBytes("utf-8"))

  lazy val validator = createMock(classOf[ScanEntityValidator])
  lazy val parser = new ValidatingScanResultParser(validator)
  lazy val singleEntityStream: ByteArrayInputStream = streamFor("[%s]".format(singleEntityStr))

  @Test
  def shouldParseEmptyJson {
    assertThat(parser.parse(streamFor("[]")),
      is(equalTo(Seq[ScanResultEntry]())))
  }
  val singleEntityStr = "{\"id\":\"id1\",\"attributes\":{\"a1\":\"a1v1\"},\"version\":\"v1\",\"lastUpdated\":\"2011-06-05T15:03:00.000Z\"}"
  lazy val singleEntity = ScanResultEntry.forEntity("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC), Map("a1" -> "a1v1"))


  @Test
  def shouldParseJsonToEntities {
    assertThat(parser.parse(singleEntityStream),
      is(equalTo(Seq(singleEntity))))
  }

  @Test
  def shouldValidateEachEntity {
    expect(validator.process(singleEntity))
    replay(validator)
    parser.parse(singleEntityStream)
    verify(validator)
  }

}

class LengthCheckingParserTest { self =>

  lazy val serviceLimitsView = createMock(classOf[PairServiceLimitsView])
  val pairRef = DiffaPairRef("key", "domain")

  lazy val canary = ScanResultEntry.forEntity("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC), Map("a1" -> "a1v1"))

  class DummyParser extends JsonScanResultParser {
    val serviceLimitsView = self.serviceLimitsView
    val pair = self.pairRef
    var passedStream: Option[String] = None

    override def parse(s: InputStream) = {
      val reader = new BufferedReader(new InputStreamReader(s, "utf8"))
      this.passedStream = Some(reader.readLine())
      Seq(canary)
    }

  }
  val checkingParser = new DummyParser with LengthCheckingParser

  val emptyResponseContent = "[" + (" " * 40) + "]"
  lazy val emptyResponse = new ByteArrayInputStream(emptyResponseContent.getBytes("UTF8"))

  @Test
  def shouldQueryForCorrectLength {
    withExpectedResponseSizeLimit(emptyResponseContent.size)
    checkingParser.parse(emptyResponse)
    verify(serviceLimitsView)
  }
  @Test(expected=classOf[Test.None])
  def shouldPassEntitiesBelowTheScanResponseSizeLimitToWrappedParser {
    withExpectedResponseSizeLimit(emptyResponseContent.size)

    checkingParser.parse(emptyResponse)
    assertThat(checkingParser.passedStream, is(Some(emptyResponseContent):Option[String]))
  }

  @Test(expected = classOf[ScanLimitBreachedException])
  def shouldRejectEntitiesLongerThanScanResponseSizeLimit {
    withExpectedResponseSizeLimit(emptyResponseContent.size-1)

    checkingParser.parse(emptyResponse)
  }

  @Test
  def shouldReturnTheInnerParserResult {
    withExpectedResponseSizeLimit(emptyResponseContent.size)

    assertThat(checkingParser.parse(emptyResponse), is(Seq(canary)))
  }

  def withExpectedResponseSizeLimit(size: Int) {
    expect(serviceLimitsView.getEffectiveLimitByNameForPair(
      pairRef.domain, pairRef.key, ScanResponseSizeLimit)).andReturn(size)
    replay(serviceLimitsView)
  }

}