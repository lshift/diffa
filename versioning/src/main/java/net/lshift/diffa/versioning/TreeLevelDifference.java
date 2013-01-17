package net.lshift.diffa.versioning;

import com.google.common.collect.MapDifference;

public class TreeLevelDifference {

  private MapDifference<String, BucketDigest> difference;
  private boolean isLeaf;

  public TreeLevelDifference(MapDifference<String, BucketDigest> difference, boolean leaf) {
    this.difference = difference;
    isLeaf = leaf;
  }

  public MapDifference<String, BucketDigest> getDifference() {
    return difference;
  }

  public boolean isLeaf() {
    return isLeaf;
  }
}
