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

package net.lshift.diffa.kernel.config;

import org.joda.time.DateTime;
import org.joda.time.Interval;

public class MonthlyPeriodUnit extends PeriodUnit {
	MonthlyPeriodUnit() {
		super("monthly");
	}

	@Override
	public boolean isCovering(Interval interval) {
		return interval != null && floor(interval.getEnd()).minusMonths(1).compareTo(ceiling(interval.getStart())) >= 0;
	}

	@Override
	protected DateTime floor(DateTime instant) {
		return instant.monthOfYear().roundFloorCopy();
	}

	@Override
	protected DateTime ceiling(DateTime instant) {
		return instant.monthOfYear().roundCeilingCopy();
	}

	@Override
	protected DateTime subtractOneUnit(DateTime from) {
		return from.minusMonths(1);
	}
}
