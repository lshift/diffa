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
import net.lshift.diffa.adapter.scanning.ScanConstraint;
import net.lshift.diffa.adapter.scanning.SetConstraint;
import net.lshift.diffa.interview.Answer;
import net.lshift.diffa.scanning.PruningHandler;
import org.jooq.*;
import org.jooq.impl.Factory;
import org.jooq.impl.SQLDataType;

import java.util.ArrayList;
import java.util.Set;

import static org.jooq.impl.Factory.*;

/**
 * A template for executing aggregating queries and bringing the result back to the client in a format amenable
 * to use with other Diffa components.
 */
public abstract class AggregatingScanner<AggregationType> {
  protected static final Field<Object> bucket = Factory.field("BUCKET");
  protected static final Field<Object> version = Factory.field("VERSION");
  protected static final Field<Object> id = Factory.field("ID");
  protected static final Field<Object> digest = Factory.field("DIGEST");

  protected final Factory db;
  protected final Field<?> partitionColumn;
  protected final int maxSliceSize;

  protected Table A;
  protected Table B;
  protected Field<String> A_ID;
  protected Field<String> B_ID;
  protected Field<String> A_VERSION;
  protected Field<Integer> bucketCount;

  protected final ArrayList<Condition> filters = new ArrayList<Condition>();

  private final DynamicTable underlyingTable;
  private final Field<String> underlyingId;
  private final Field<String> underlyingVersion;

  public AggregatingScanner(Factory db, PartitionMetadata config, int maxSliceSize) {
    this.db = db;
    this.underlyingTable = config.getTable();
    this.underlyingId = (Field<String>) config.getId();
    this.underlyingVersion = (Field<String>) config.getVersion();
    this.partitionColumn = config.partitionBy();
    this.maxSliceSize = maxSliceSize;

    // There should be at least one condition - this is just a place-holder which doesn't filter.
    filters.add(trueCondition());

    this.bucketCount = cast(ceil(cast(count(), SQLDataType.REAL).div(maxSliceSize)), SQLDataType.INTEGER);
  }

  public void scan(Set<ScanConstraint> constraints, Set<ScanAggregation> aggregations, PruningHandler handler) {
    configureFields(constraints);
    setAggregation(aggregations);
    configurePartitions();
    setFilters(constraints);

    Cursor<Record> cursor = runScan();

    while (cursor.hasNext()) {
      Record record = cursor.fetchOne();
      Answer answer = recordToAnswer(record);
      handler.onPrune(answer);
    }
    cursor.close();
  }

  protected abstract AggregationType getAggregation(Set<ScanAggregation> aggregations);
  protected abstract void setAggregation(Set<ScanAggregation> aggregations);
  protected abstract Cursor<Record> runScan();
  protected abstract void configurePartitions();
  protected abstract Answer recordToAnswer(Record record);

  protected Field<String> md5(Field<Object> of, Field<?> orderBy) {
    return function("md5", String.class, groupConcat(of).orderBy(orderBy.asc()).separator("")).as(digest.getName());
  }

  /**
   * Make any changes to the underlying JOOQ table definition. Any columns to be referred to, such as in 'where'
   * or 'group by' clauses, must be added to the definition of the underlying JOOQ table here (or in a concrete
   * subclass).
   */
  protected void configureFields(Set<ScanConstraint> constraints) {
    if (constraints != null) {
      for (ScanConstraint constraint : constraints) {
        // Hope that this can be implicitly converted to the appropriate data type.
        underlyingTable.addField(constraint.getAttributeName(), SQLDataType.VARCHAR);
      }
    }

    this.A = underlyingTable.as("A");
    this.B = underlyingTable.as("B");
    this.A_ID = A.getField(underlyingId);
    this.B_ID = B.getField(underlyingId);
    this.A_VERSION = A.getField(underlyingVersion);
  }

  private void setFilters(Set<ScanConstraint> constraints) {
    if (constraints != null) {
      for (ScanConstraint constraint : constraints) {
        if (constraint instanceof SetConstraint) {
          SetConstraint setConstraint = (SetConstraint) constraint;
          Field<String> A_set = (Field<String>) A.getField(setConstraint.getAttributeName());
          Field<String> B_set = (Field<String>) B.getField(setConstraint.getAttributeName());
          for (String value : setConstraint.getValues()) {
            filters.add(A_set.eq(value));
            filters.add(B_set.eq(value));
          }
        }
      }
    }
  }
}
