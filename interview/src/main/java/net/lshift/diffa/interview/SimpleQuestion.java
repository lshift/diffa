package net.lshift.diffa.interview;

import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.adapter.scanning.ScanConstraint;

import java.util.Set;

public class SimpleQuestion implements Question {

  private Set<ScanConstraint> constraints;
  private Set<ScanAggregation> aggregations;
  private int maxSliceSize = 100;

  public SimpleQuestion() {

  }

  public SimpleQuestion(Set<ScanConstraint> constraints, Set<ScanAggregation> aggregations) {
    this.aggregations = aggregations;
    this.constraints = constraints;
  }

  @Override
  public Set<ScanConstraint> getConstraints() {
    return constraints;
  }

  @Override
  public Set<ScanAggregation> getAggregations() {
    return aggregations;
  }

  public void setConstraints(Set<ScanConstraint> constraints) {
    this.constraints = constraints;
  }

  public void setAggregations(Set<ScanAggregation> aggregations) {
    this.aggregations = aggregations;
  }

  public void setMaxSliceSize(int maxSliceSize) {
    this.maxSliceSize = maxSliceSize;
  }

  @Override
  public int getMaxSliceSize() {
    return maxSliceSize;

  }
}
