package net.lshift.diffa.kernel.differencing

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

import org.junit.runner.RunWith
import org.junit.Assert._
import net.lshift.diffa.kernel.differencing.ZoomLevelsIndividualTileEdgeTest.Scenario
import ZoomLevels._
import org.junit.experimental.theories.{DataPoint, Theory, Theories}
import org.joda.time.{DateTimeZone, Interval, DateTime}

@RunWith(classOf[Theories])
class ZoomLevelsIndividualTileEdgeTest {

  @Theory
  def shouldContainTileGroup(s:Scenario) = {
    assertEquals(s.timestamps, ZoomLevels.individualTileEdges(s.interval, s.zoomLevel))
  }
}

object ZoomLevelsIndividualTileEdgeTest {

  // Aligned data points

  @DataPoint def daily = Scenario(DAILY,
                                  new Interval(new DateTime(2004,3,6,0,0,0,0, DateTimeZone.UTC),
                                               new DateTime(2004,3,8,0,0,0,0, DateTimeZone.UTC)),
                                  new DateTime(2004,3,6,0,0,0,0, DateTimeZone.UTC),
                                  new DateTime(2004,3,7,0,0,0,0, DateTimeZone.UTC),
                                  new DateTime(2004,3,8,0,0,0,0, DateTimeZone.UTC))

  @DataPoint def eightHourly = Scenario(EIGHT_HOURLY,
                                        new Interval(new DateTime(1996,10,5,0,0,0,0, DateTimeZone.UTC),
                                                    new DateTime(1996,10,5,8,0,0,0, DateTimeZone.UTC)),
                                        new DateTime(1996,10,5,0,0,0,0, DateTimeZone.UTC),
                                        new DateTime(1996,10,5,8,0,0,0, DateTimeZone.UTC))

  @DataPoint def fourHourly = Scenario(FOUR_HOURLY,
                                       new Interval(new DateTime(1987,1,24,20,0,0,0, DateTimeZone.UTC),
                                                    new DateTime(1987,1,25,0,0,0,0, DateTimeZone.UTC)),
                                       new DateTime(1987,1,24,20,0,0,0, DateTimeZone.UTC),
                                       new DateTime(1987,1,25,0,0,0,0, DateTimeZone.UTC))

  @DataPoint def twoHourly = Scenario(TWO_HOURLY,
                                      new Interval(new DateTime(2033,12,12,2,0,0,0, DateTimeZone.UTC),
                                                   new DateTime(2033,12,12,4,0,0,0, DateTimeZone.UTC)),
                                      new DateTime(2033,12,12,2,0,0,0, DateTimeZone.UTC),
                                      new DateTime(2033,12,12,4,0,0,0, DateTimeZone.UTC))

  @DataPoint def hourly = Scenario(HOURLY,
                                   new Interval(new DateTime(2008,2,28,19,0,0,0, DateTimeZone.UTC),
                                                new DateTime(2008,2,28,21,0,0,0, DateTimeZone.UTC)),
                                   new DateTime(2008,2,28,19,0,0,0, DateTimeZone.UTC),
                                   new DateTime(2008,2,28,20,0,0,0, DateTimeZone.UTC),
                                   new DateTime(2008,2,28,21,0,0,0, DateTimeZone.UTC))

  @DataPoint def halfHourly = Scenario(HALF_HOURLY,
                                       new Interval(new DateTime(1999,11,30,9,30,0,0, DateTimeZone.UTC),
                                                    new DateTime(1999,11,30,10,30,0,0, DateTimeZone.UTC)),
                                       new DateTime(1999,11,30,9,30,0,0, DateTimeZone.UTC),
                                       new DateTime(1999,11,30,10,0,0,0, DateTimeZone.UTC),
                                       new DateTime(1999,11,30,10,30,0,0, DateTimeZone.UTC))

  @DataPoint def quarterHourly = Scenario(QUARTER_HOURLY,
                                          new Interval(new DateTime(2007,8,18,23,45,0,0, DateTimeZone.UTC),
                                                       new DateTime(2007,8,19,0,0,0,0, DateTimeZone.UTC)),
                                          new DateTime(2007,8,18,23,45,0,0, DateTimeZone.UTC),
                                          new DateTime(2007,8,19,0,0,0,0, DateTimeZone.UTC))

  // Unaligned data points

  @DataPoint def unalignedDaily = Scenario(DAILY,
                                       new Interval(new DateTime(2011,9,1,17,0,0,0, DateTimeZone.UTC),
                                                    new DateTime(2011,9,3,17,0,0,0, DateTimeZone.UTC)),
                                       new DateTime(2011,9,1,0,0,0,0, DateTimeZone.UTC),
                                       new DateTime(2011,9,2,0,0,0,0, DateTimeZone.UTC),
                                       new DateTime(2011,9,3,0,0,0,0, DateTimeZone.UTC))

  @DataPoint def unalignedHalfHourly = Scenario(HALF_HOURLY,
                                       new Interval(new DateTime(1999,11,30,9,30,13,0, DateTimeZone.UTC),
                                                    new DateTime(1999,11,30,10,30,0,667, DateTimeZone.UTC)),
                                       new DateTime(1999,11,30,9,30,0,0, DateTimeZone.UTC),
                                       new DateTime(1999,11,30,10,0,0,0, DateTimeZone.UTC),
                                       new DateTime(1999,11,30,10,30,0,0, DateTimeZone.UTC))

  @DataPoint def unalignedQuarterHourly = Scenario(QUARTER_HOURLY,
                                          new Interval(new DateTime(2007,8,18,23,46,0,0, DateTimeZone.UTC),
                                                       new DateTime(2007,8,19,0,2,0,0, DateTimeZone.UTC)),
                                          new DateTime(2007,8,18,23,45,0,0, DateTimeZone.UTC),
                                          new DateTime(2007,8,19,0,0,0,0, DateTimeZone.UTC))

  case class Scenario (zoomLevel:Int, interval:Interval, timestamps:DateTime*)

}