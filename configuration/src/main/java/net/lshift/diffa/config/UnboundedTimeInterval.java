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

public class UnboundedTimeInterval extends TimeInterval {
	private static final UnboundedTimeInterval singleton = new UnboundedTimeInterval();

	private UnboundedTimeInterval() {
	}

	public static UnboundedTimeInterval getInstance() {
		return singleton;
	}

	@Override
	public String getStartAs(DateTimeType dataType) {
		return openEnd;
	}

	@Override
	public String getEndAs(DateTimeType dataType) {
		return openEnd;
	}

	@Override
	public TimeInterval overlap(TimeInterval other) {
		if (other instanceof UnboundedTimeInterval) {
			return this;
		} else {
			return other;
		}
	}

	@Override
	public boolean overlaps(TimeInterval other) {
		if (other instanceof NullTimeInterval) {
			return false;
		} else {
			return true;
		}
	}

	@Override
	public PeriodUnit maximumCoveredPeriodUnit() {
		return PeriodUnit.YEARLY;
	}
}
