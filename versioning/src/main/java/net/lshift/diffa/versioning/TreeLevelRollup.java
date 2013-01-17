package net.lshift.diffa.versioning;

import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.adapter.scanning.ScanConstraint;
import net.lshift.diffa.adapter.scanning.StringPrefixAggregation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TreeLevelRollup {

  private String context, bucket;
  private Map<String,BucketDigest> members;
  private boolean isLeaf = false;
  private int maxSliceSize;

  public TreeLevelRollup(String context, String bucket, int maxSliceSize, Map<String, BucketDigest> members, boolean leaf) {
    this.context = context;
    this.bucket = bucket;
    this.members = members;
    this.maxSliceSize = maxSliceSize;
    this.isLeaf = leaf;
  }

  public Map<String, BucketDigest> getMembers() {
    return members;
  }

  public int getMaxSliceSize() {
    return maxSliceSize;
  }

  public boolean isLeaf() {
    return isLeaf;
  }

  public Set<ScanAggregation> getEquivalentAggregations(String context) {
    Set<ScanAggregation> aggregations = new HashSet<ScanAggregation>();


    return aggregations;
  }

  public Set<ScanConstraint> getEquivalentConstraints() {
    Set<ScanConstraint> constraints = new HashSet<ScanConstraint>();

    return constraints;
  }

  @Override
  public String toString() {
    return "TreeLevelRollup{" +
        "bucket='" + bucket + '\'' +
        ", members=" + members +
        ", isLeaf=" + isLeaf +
        '}';
  }

  public boolean isEmpty() {
    return members == null || members.isEmpty();
  }

  public String getBucket() {
    return bucket;
  }

  public String getContext() {
    return context;
  }
}
