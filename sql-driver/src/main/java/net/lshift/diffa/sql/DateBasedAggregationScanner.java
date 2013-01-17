/*
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

package net.lshift.diffa.sql;

import net.lshift.diffa.adapter.scanning.DateAggregation;
import net.lshift.diffa.adapter.scanning.DateGranularityEnum;
import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.interview.Answer;
import net.lshift.diffa.interview.SimpleGroupedAnswer;
import org.joda.time.DateTime;
import org.jooq.*;
import org.jooq.impl.Factory;
import org.jooq.impl.SQLDataType;

import java.sql.Date;
import java.util.*;

/**
 */
public class DateBasedAggregationScanner extends AggregatingScanner<DateAggregation> {
  private static final Field<Object> day = Factory.field("DAY");
  private static final Field<Object> month = Factory.field("MONTH");
  private static final Field<Object> year = Factory.field("YEAR");

  private DateAggregation aggregation;

  private Field<Date> truncDay;
  private Field<Date> truncMonth;
  private Field<Date> truncYear;

  public DateBasedAggregationScanner(Factory db, PartitionMetadata config, int maxSliceSize) {
    super(db, config, maxSliceSize);
  }

  @Override
  protected Cursor<Record> runScan() {
    return queryByGranularity(aggregation.getGranularity()).fetchLazy();
  }

  private SelectFinalStep queryByGranularity(DateGranularityEnum granularity) {
    switch (granularity) {
      case Yearly: return yearly();
      case Monthly: return monthly();
      default: return daily();
    }
  }

  @Override
  protected Answer recordToAnswer(Record record) {
    String dateComponent = (new DateTime(record.getValueAsDate(year))).getYear() + "";
    String digestValue = record.getValueAsString(digest);

    return new SimpleGroupedAnswer(dateComponent, digestValue);
  }

  @Override
  protected void configurePartitions() {
    Field<?> underlyingPartition = this.partitionColumn;
    Field<?> A_PARTITION = A.getField(underlyingPartition);
    this.truncDay = truncDate(A_PARTITION, "DD");
    this.truncMonth = truncDate(day, "MM");
    this.truncYear = truncDate(month, "YY");
  }

  @Override
  protected void setAggregation(Set<ScanAggregation> aggregations) {
    if (aggregation == null) {
      aggregation = new DateAggregation(this.partitionColumn.getName(), DateGranularityEnum.Yearly);
      if (aggregations != null) {
        for (ScanAggregation agg : aggregations) {
          if (agg instanceof DateAggregation) {
            aggregation = (DateAggregation) agg;
          }
        }
      }
    }
  }

  @Override
  protected DateAggregation getAggregation(Set<ScanAggregation> aggregations) {
    setAggregation(aggregations);
    return aggregation;
  }

  private Field<Date> truncDate(Field<?> column, String granularity) {
    return Factory.field("trunc({0}, {1})", SQLDataType.DATE, column, Factory.inline(granularity));
  }

  private <T,U>SelectLimitStep step(Field<T> f1, Field<U> f1a, Field<Object> digest, Field<U> f2a, SelectLimitStep next) {
    return db.select(f1.as(f1a.getName()), md5(digest, f2a)).
        from(next).
        groupBy(f1).
        orderBy(f1a);
  }

  private SelectLimitStep yearly() {
    return step(truncYear, year, digest, month, monthly());
  }

  private SelectLimitStep monthly() {
    return step(truncMonth, month, digest, day, daily());
  }

  private SelectLimitStep daily() {
    return db.select(day, md5(digest, bucket)).
        from(dailyAndSliced()).
        groupBy(day).
        orderBy(day);
  }

  private SelectLimitStep dailyAndSliced() {
    return db.select(day, bucket, md5(version, id)).
        from(sliceAssignedEntities()).
        groupBy(day, bucket).
        orderBy(day, bucket); // must order or else the roll-up to daily could be wrong.
  }

  private SelectLimitStep sliceAssignedEntities() {
    return db.select(
          truncDay.as(day.getName()),
          A_ID.as(id.getName()),
          A_VERSION.as(version.getName()),
          bucketCount.as(bucket.getName())).
        from(A).join(B).on(A_ID.ge(B_ID)).
        where(filters).
        groupBy(truncDay, A_ID, A_VERSION).
        orderBy(truncDay, bucket);
  }
}
