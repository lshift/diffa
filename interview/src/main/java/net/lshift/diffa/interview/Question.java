package net.lshift.diffa.interview;

import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.adapter.scanning.ScanConstraint;
import org.codehaus.jackson.annotate.JsonTypeInfo;

import java.util.Set;

@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include= JsonTypeInfo.As.PROPERTY, property="@class")
public interface Question {

  Set<ScanConstraint> getConstraints();

  Set<ScanAggregation> getAggregations();

  int getMaxSliceSize();
}
