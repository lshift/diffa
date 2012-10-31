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

public class NullTimeInterval extends TimeInterval {
	private static final NullTimeInterval singleton = new NullTimeInterval();

	private NullTimeInterval() {}

	public static NullTimeInterval getInstance() {
		return singleton;
	}

	@Override
	public String getStartAs(DateTimeType dataType) {
		throw new RuntimeException("Unsupported operation");
	}

	@Override
	public String getEndAs(DateTimeType dataType) {
		throw new RuntimeException("Unsupported operation");
	}

	@Override
	public boolean overlaps(TimeInterval other) {
		return false;
	}

	@Override
	public TimeInterval overlap(TimeInterval other) {
		return this;
	}

	@Override
	public PeriodUnit maximumCoveredPeriodUnit() {
		return PeriodUnit.INDIVIDUAL;
	}
}
