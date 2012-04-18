package net.lshift.diffa.participant.scanning;

import java.util.Set;

/**
 * Describe a request to perform a specific scan.
 */
public class ScanRequest {
  private final Set<ScanConstraint> constraints;
  private final Set<ScanAggregation> aggregations;

  public ScanRequest(Set<ScanConstraint> constraints, Set<ScanAggregation> aggregations) {
    this.constraints = constraints;
    this.aggregations = aggregations;
  }

  public Set<ScanConstraint> getConstraints() {
    return constraints;
  }

  public Set<ScanAggregation> getAggregations() {
    return aggregations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ScanRequest that = (ScanRequest) o;

    if (aggregations != null ? !aggregations.equals(that.aggregations) : that.aggregations != null) return false;
    if (constraints != null ? !constraints.equals(that.constraints) : that.constraints != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = constraints != null ? constraints.hashCode() : 0;
    result = 31 * result + (aggregations != null ? aggregations.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ScanRequest{" +
      "constraints=" + constraints +
      ", aggregations=" + aggregations +
      '}';
  }
}
