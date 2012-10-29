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

public class TimeIntervalFactory {
	private static final DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.dateTime().withZoneUTC();

	public static TimeInterval fromRange(String start, String end) {
		if (isEmpty(start) && isEmpty(end)) {
			return UnboundedTimeInterval.getInstance();
		} else {
			return new BoundedTimeInterval(start, end);
		}
	}

	public static TimeInterval fromJodaInterval(Interval interval) {
		DateTime iStart = interval.getStart();
		DateTime iEnd = interval.getEnd();

		String start;
		if (TimeInterval.isBeginningOfTime(iStart)) {
			start = "";
		} else {
			start = interval.getStart().toString(dateTimeFormatter);
		}
		String end;
		if (TimeInterval.isEndOfTime(iEnd)) {
			end = "";
		} else {
			end = interval.getEnd().toString(dateTimeFormatter);
		}

		return new BoundedTimeInterval(start, end);
	}

	private static boolean isEmpty(String maybeEmpty) {
		return maybeEmpty == null || maybeEmpty.equals("");
	}
}
