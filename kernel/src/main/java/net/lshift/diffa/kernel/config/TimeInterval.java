package net.lshift.diffa.kernel.config;

import org.joda.time.DateTime;
import org.joda.time.Interval;

/**
 * A TimeInterval adds support for unbounded ranges to org.joda.time.Interval.
 */
public abstract class TimeInterval {
	// min and max dates are take from org.joda.time.chrono.GregorianChronology
	// (private)
	public static final int MIN_YEAR = -292275054;
	public static final int MAX_YEAR = 292278993;
	protected static final String openEnd = "";

	public static boolean isBeginningOfTime(DateTime moment) {
		return moment.getYear() <= MIN_YEAR;
	}

	public static boolean isEndOfTime(DateTime moment) {
		return moment.getYear() >= MAX_YEAR;
	}

	public abstract String getStartAs(DateTimeType dataType);

	public abstract String getEndAs(DateTimeType dataType);

	/**
	 * Does this interval overlap the other interval?
	 */
	public abstract boolean overlaps(TimeInterval other);

	/**
	 * What is the overlap of this interval with the other interval?
	 */
	public abstract TimeInterval overlap(TimeInterval other);

	/**
	 * Does this interval overlap the other definite interval?
	 */
	public boolean overlaps(Interval other) {
		return overlaps(TimeIntervalFactory.fromJodaInterval(other));
	}

	/**
	 * What is the overlap of this interval with the other definite interval?
	 */
	public TimeInterval overlap(Interval other) {
		return overlap(TimeIntervalFactory.fromJodaInterval(other));
	}
	
	public abstract PeriodUnit maximumCoveredPeriodUnit();
}
