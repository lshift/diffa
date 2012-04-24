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
import net.lshift.diffa.kernel.differencing.ZoomLevelsContainingGroupIntervalTest.Scenario
import ZoomLevels._
import org.junit.experimental.theories.{DataPoint, Theory, Theories}
import org.joda.time.{DateTimeZone, Interval, DateTime}

@RunWith(classOf[Theories])
class ZoomLevelsContainingGroupIntervalTest {

  @Theory
  def shouldContain(s:Scenario) = {
    assertEquals(s.interval, ZoomLevels.containingTileGroupInterval(s.timestamp, s.zoomLevel))
  }
}

object ZoomLevelsContainingGroupIntervalTest {

  @DataPoint def daily = Scenario(new DateTime(2004,3,6,17,16,58,888, DateTimeZone.UTC), DAILY,
                                  new Interval(new DateTime(2004,3,6,0,0,0,0, DateTimeZone.UTC),
                                               new DateTime(2004,3,7,0,0,0,0, DateTimeZone.UTC)))

  @DataPoint def eightHourly = Scenario(new DateTime(1996,10,5,4,14,18,745, DateTimeZone.UTC), EIGHT_HOURLY,
                                  new Interval(new DateTime(1996,10,5,0,0,0,0, DateTimeZone.UTC),
                                               new DateTime(1996,10,6,0,0,0,0, DateTimeZone.UTC)))

  @DataPoint def fourHourly = Scenario(new DateTime(1987,1,24,21,23,34,632, DateTimeZone.UTC), FOUR_HOURLY,
                                  new Interval(new DateTime(1987,1,24,0,0,0,0, DateTimeZone.UTC),
                                               new DateTime(1987,1,25,0,0,0,0, DateTimeZone.UTC)))

  @DataPoint def twoHourly = Scenario(new DateTime(2033,12,12,2,19,45,125, DateTimeZone.UTC), TWO_HOURLY,
                                  new Interval(new DateTime(2033,12,12,0,0,0,0, DateTimeZone.UTC),
                                               new DateTime(2033,12,13,0,0,0,0, DateTimeZone.UTC)))

  @DataPoint def hourly = Scenario(new DateTime(2008,2,28,19,7,6,198, DateTimeZone.UTC), HOURLY,
                                  new Interval(new DateTime(2008,2,28,0,0,0,0, DateTimeZone.UTC),
                                               new DateTime(2008,2,29,0,0,0,0, DateTimeZone.UTC)))

  @DataPoint def halfHourlyAfter12pm = Scenario(new DateTime(1999,11,30,19,51,52,392, DateTimeZone.UTC), HALF_HOURLY,
                                    new Interval(new DateTime(1999,11,30,12,0,0,0, DateTimeZone.UTC),
                                                 new DateTime(1999,12,1,0,0,0,0, DateTimeZone.UTC)))

  @DataPoint def halfHourlyBefore12pm = Scenario(new DateTime(1999,11,30,9,51,52,392, DateTimeZone.UTC), HALF_HOURLY,
                                  new Interval(new DateTime(1999,11,30,0,0,0,0, DateTimeZone.UTC),
                                               new DateTime(1999,11,30,12,0,0,0, DateTimeZone.UTC)))

  @DataPoint def quarterHourlyBefore8am = Scenario(new DateTime(2007,8,18,3,59,58,234, DateTimeZone.UTC), QUARTER_HOURLY,
                                  new Interval(new DateTime(2007,8,18,0,0,0,0, DateTimeZone.UTC),
                                               new DateTime(2007,8,18,8,0,0,0, DateTimeZone.UTC)))

  @DataPoint def quarterHourlyBetween8amAnd4pm = Scenario(new DateTime(2007,8,18,13,59,58,234, DateTimeZone.UTC), QUARTER_HOURLY,
                                  new Interval(new DateTime(2007,8,18,8,0,0,0, DateTimeZone.UTC),
                                               new DateTime(2007,8,18,16,0,0,0, DateTimeZone.UTC)))

  @DataPoint def quarterHourlyAfter4pm = Scenario(new DateTime(2007,8,18,23,59,58,234, DateTimeZone.UTC), QUARTER_HOURLY,
                                  new Interval(new DateTime(2007,8,18,16,0,0,0, DateTimeZone.UTC),
                                               new DateTime(2007,8,19,0,0,0,0, DateTimeZone.UTC)))

  case class Scenario (timestamp:DateTime, zoomLevel:Int, interval:Interval)

}