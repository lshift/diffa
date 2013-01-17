package net.lshift.diffa.versioning.plumbing;

import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.adapter.scanning.StringPrefixAggregation;

public class EntityIdBucketing {

  public static final String name = "entityId";
  /*
  public static final int INITIAL_LENGTH = 0;
  public static final int MAX_LENGTH = 3;
  public static final int STEP = 1;
  */

  public static ScanAggregation getEquivalentAggregation(String bucket) {
    int length = bucket.replace(".", "").length() + 1;
    return new StringPrefixAggregation(name, null , "2", "4", "6");
  }


}
