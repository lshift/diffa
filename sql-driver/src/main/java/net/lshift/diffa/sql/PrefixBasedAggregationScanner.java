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

import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.adapter.scanning.StringPrefixAggregation;
import net.lshift.diffa.interview.Answer;
import net.lshift.diffa.interview.SimpleGroupedAnswer;

import org.jooq.*;
import org.jooq.impl.Factory;
import org.jooq.impl.SQLDataType;

import java.util.*;

/**
 */
public class PrefixBasedAggregationScanner extends AggregatingScanner<StringPrefixAggregation> {
  private SortedMap<Integer, Field<String>> preficesByLength = new TreeMap<Integer, Field<String>>();
  private SortedMap<Integer, Field<String>> prefixAliases = new TreeMap<Integer, Field<String>>();
  private StringPrefixAggregation aggregation;

  public PrefixBasedAggregationScanner(Factory db, PartitionMetadata config, int maxSliceSize) {
    super(db, config, maxSliceSize);
  }

  @Override
  protected Cursor<Record> runScan() {
    int shortestPrefix = 1;
    if (aggregation != null) {
      shortestPrefix = aggregation.getOffsets().first();
    }
    SelectLimitStep query = getQueryForPrefixLength(shortestPrefix);
    String sql = query.toString();
    return db.fetchLazy(sql);
  }

  private SelectLimitStep getQueryForPrefixLength(int prefixLength) {
    Integer longestPrefix = aggregation.getOffsets().last();
    if (prefixLength >= longestPrefix) {
      return rollupToLengthMax(longestPrefix);
    } else {
      return rollupToLength(prefixLength);
    }
  }

  @Override
  protected Answer recordToAnswer(Record record) {
    String idComponent = (String) record.getValue(0);
    String digestValue = record.getValueAsString(digest);

    return new SimpleGroupedAnswer(idComponent, digestValue);
  }

  @Override
  protected void setAggregation(Set<ScanAggregation> aggregations) {
    if (aggregation == null) {
      NavigableSet<Integer> defaultPrefixLengths = new TreeSet<Integer>();
      Collections.addAll(defaultPrefixLengths, 1, 2, 3);
      aggregation = new StringPrefixAggregation(this.partitionColumn.getName(), null, defaultPrefixLengths);

      if (aggregations != null && !aggregations.isEmpty()) {
        for (ScanAggregation agg : aggregations) {
          if (agg instanceof StringPrefixAggregation) {
            aggregation = (StringPrefixAggregation) agg;
          }
        }
      }
    }
  }

  @Override
  protected void configurePartitions() {
    Field<?> underlyingPartition = this.partitionColumn;
    Field<?> A_PARTITION = A.getField(underlyingPartition);

    if (aggregation == null) {
      preficesByLength.put(1, columnPrefix((Field<String>) A_PARTITION, 1));
    } else {
      Integer longestPrefix;
      NavigableSet<Integer> prefixLengths = aggregation.getOffsets();
      longestPrefix = prefixLengths.last();
      preficesByLength.put(longestPrefix, columnPrefix((Field<String>) A_PARTITION, longestPrefix));
      prefixAliases.put(longestPrefix, aliasForPrefixField(longestPrefix));
      while ((longestPrefix = prefixLengths.lower(longestPrefix)) != null) {
        preficesByLength.put(longestPrefix,
            columnPrefix(aliasForPrefixField(prefixLengths.higher(longestPrefix)), longestPrefix));
        prefixAliases.put(longestPrefix, aliasForPrefixField(longestPrefix));
      }
    }
  }

  @Override
  protected StringPrefixAggregation getAggregation(Set<ScanAggregation> aggregations) {
    setAggregation(aggregations);
    return aggregation;
  }

  private Field<String> aliasForPrefixField(int prefixLength) {
    return Factory.field("P" + prefixLength, SQLDataType.VARCHAR);
  }

  private Field<String> columnPrefix(Field<String> column, int length) {
    return Factory.substring(column, 1, length); // substring offset index is 1-based.
  }

  private <T,U>SelectLimitStep step(Field<T> f1, Field<U> f1a, Field<Object> digest, Field<U> f2a, SelectLimitStep next) {
    return db.select(f1.as(f1a.getName()), md5(digest, f2a)).
        from(next).
        groupBy(f1).
        orderBy(f1a);
  }

  private Integer getNextPrefixLength(int prefixLength) {
    return aggregation.getOffsets().higher(prefixLength);
  }

  private SelectLimitStep rollupToLength(int prefixLength) {
    SelectLimitStep nextRollup;
    Integer nextPrefixLength = getNextPrefixLength(prefixLength);
    if (nextPrefixLength == null || getNextPrefixLength(nextPrefixLength) == null) {
      nextRollup = rollupToLengthMax(nextPrefixLength != null ? nextPrefixLength : aggregation.getOffsets().last());
    } else {
      nextRollup = rollupToLength(nextPrefixLength);
    }

    return step(preficesByLength.get(prefixLength), prefixAliases.get(prefixLength), digest,
        prefixAliases.get(nextPrefixLength), nextRollup);
  }

  private SelectLimitStep rollupToLengthMax(int prefixLength) {
    return db.select(prefixAliases.get(prefixLength), md5(digest, bucket)).
        from(sliced(prefixLength)).
        groupBy(prefixAliases.get(prefixLength)).
        orderBy(prefixAliases.get(prefixLength));
  }

  private SelectLimitStep sliced(int prefixLength) {
    return db.select(prefixAliases.get(prefixLength), bucket, md5(version, id)).
        from(sliceAssignedEntities(prefixLength)).
        groupBy(prefixAliases.get(prefixLength), bucket).
        orderBy(prefixAliases.get(prefixLength), bucket);
  }

  private SelectLimitStep sliceAssignedEntities(int prefixLength) {
    return db.select(
          preficesByLength.get(prefixLength).as(prefixAliases.get(prefixLength).getName()),
          A_ID.as(id.getName()),
          A_VERSION.as(version.getName()),
          bucketCount.as(bucket.getName())).
        from(A).join(B).on(A_ID.ge(B_ID)).
        where(filters).
        groupBy(preficesByLength.get(prefixLength), A_ID, A_VERSION).
        orderBy(prefixAliases.get(prefixLength), bucket);
  }
}
