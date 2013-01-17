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

package net.lshift.diffa.config;

import java.util.ArrayList;

import org.joda.time.DateTime;
import org.joda.time.Interval;

/**
 * A PeriodUnit is a single unit of a given granularity of the Gregorian calendar,
 * such as one day, one month or one year.
 *
 * This class provides the means to determine:
 * 1) whether a given period unit is completely covered by an interval;
 * 2) what is the greatest period unit covered for a given interval.
 *
 * If there is no defined unit of time that is covered by an interval, then
 * the covered unit is 'individual'.
 */
public abstract class PeriodUnit {
	public static PeriodUnit INDIVIDUAL = new IndividualPeriodUnit();
	public static PeriodUnit DAILY = new DailyPeriodUnit();
	public static PeriodUnit MONTHLY = new MonthlyPeriodUnit();
	public static PeriodUnit YEARLY = new YearlyPeriodUnit();
	private static ArrayList<PeriodUnit> periods = new ArrayList<PeriodUnit>();

	static {
		periods.add(YEARLY);
		periods.add(MONTHLY);
		periods.add(DAILY);
		periods.add(INDIVIDUAL);
	}

	private String name;

	PeriodUnit(String name) {
		this.name = name;
	}

	public static PeriodUnit maximumCoveredPeriod(Interval interval) {
		for (PeriodUnit u: periods) {
			if (u.isCovering(interval)) {
				return u;
			}
		}
		return PeriodUnit.INDIVIDUAL;
	}

	public boolean isCovering(Interval interval) {
		return interval != null && subtractOneUnit(floor(interval.getEnd())).compareTo(ceiling(interval.getStart())) >= 0;
	}

  /**
   * Since the Joda time API doesn't provide a parameterised minus method that takes a unit of time to subtract,
   * subclasses must implement this unit-specific subtraction function.
   *
   * @param from  The instant to subtract a single time unit from.
   * @return The instant that is one unit of time earlier than the given instant.
   */
	protected abstract DateTime subtractOneUnit(DateTime from);

	protected abstract DateTime floor(DateTime instant);

	protected abstract DateTime ceiling(DateTime instant);

	@Override
	public String toString() {
		return name;
	}
}
