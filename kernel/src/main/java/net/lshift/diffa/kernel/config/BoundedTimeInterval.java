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
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Represents either a definite interval (bounded at both ends), or a half-bounded interval.
 */
public class BoundedTimeInterval extends TimeInterval {
	private static final DateTimeFormatter dateTimeParser = ISODateTimeFormat.dateOptionalTimeParser().withZoneUTC();
	private static final DateTimeFormatter dateFormatter = ISODateTimeFormat.date().withZoneUTC();
	private static final DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.dateTime().withZoneUTC();

	private String start;
	private String end;
	private Interval jodaInterval;

	@Override
	public String getStartAs(DateTimeType dataType) {
		if (isOpen(start)) {
			return openEnd;
		} else {
			String r = dateTimeParser.parseDateTime(start).toString(formatterForDataType(dataType));
			return r;
		}
	}

	@Override
	public String getEndAs(DateTimeType dataType) {
		if (isOpen(end)) {
			return openEnd;
		} else {
			return dateTimeParser.parseDateTime(end).toString(formatterForDataType(dataType));
		}
	}

	private DateTimeFormatter formatterForDataType(DateTimeType dataType) {
		if (dataType.equals(DateTimeType.DATE)) {
			return dateFormatter;
		} else {
			return dateTimeFormatter;
		}
	}

	@Override
	public TimeInterval overlap(TimeInterval other) {
		if (other instanceof BoundedTimeInterval) {
			return overlap(((BoundedTimeInterval) other).toJodaInterval());
		} else {
			return other.overlap(this);
		}
	}

	@Override
	public boolean overlaps(TimeInterval other) {
		if (other instanceof BoundedTimeInterval) {
			return overlaps(((BoundedTimeInterval) other).toJodaInterval());
		} else {
			return other.overlaps(this);
		}
	}

	@Override
	public TimeInterval overlap(Interval other) {
		Interval overlap = toJodaInterval().overlap(other);
		if (overlap != null) {
			return TimeIntervalFactory.fromJodaInterval(overlap);
		} else {
			return NullTimeInterval.getInstance();
		}
	}

	@Override
	public boolean overlaps(Interval other) {
		return toJodaInterval().overlaps(other);
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof BoundedTimeInterval) {
			BoundedTimeInterval otherInterval = (BoundedTimeInterval) other;

			return getStartAs(DateTimeType.DATETIME).equals(otherInterval.getStartAs(DateTimeType.DATETIME))
					&& getEndAs(DateTimeType.DATETIME).equals(otherInterval.getEndAs(DateTimeType.DATETIME));
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return 31 * start.hashCode() + end.hashCode();
	}

	@Override
	public String toString() {
		return String.format("BoundedTimeInterval(%s, %s)", start, end);
	}

	public BoundedTimeInterval(String start, String end) {
		this.start = start == null ? openEnd : start;
		this.end = end == null ? openEnd : end;
	}

	public Interval toJodaInterval() {
		if (jodaInterval == null) {
			String iStart = isOpen(start) ? String.valueOf(TimeInterval.MIN_YEAR) : start;
			String iEnd = isOpen(end) ? String.valueOf(TimeInterval.MAX_YEAR) : end;

			jodaInterval = new Interval(parseDateTime(iStart), parseDateTime(iEnd));
		}

		return jodaInterval;
	}

	private DateTime parseDateTime(String expression) {
		return dateTimeParser.parseDateTime(expression);
	}

	private boolean isOpen(String bound) {
		return bound.equals(openEnd);
	}

	@Override
	public PeriodUnit maximumCoveredPeriodUnit() {
		return PeriodUnit.maximumCoveredPeriod(jodaInterval);
	}
}
