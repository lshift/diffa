package net.lshift.diffa.versioning.plumbing;

import net.lshift.diffa.interview.Answer;
import net.lshift.diffa.interview.IndividualAnswer;
import net.lshift.diffa.interview.GroupedAnswer;
import net.lshift.diffa.versioning.BucketDigest;

import java.util.*;

public class DifferencingUtils {

  public static Map<String,BucketDigest> convertAggregates(Iterable<Answer> aggregates, boolean isLeaf) {

    Map<String,BucketDigest> mapped = new HashMap<String, BucketDigest>();

    for (Answer entry : aggregates) {

      if (entry.getDigest() == null) {
        throw new RuntimeException("ScanResultEntry did not contain a version: " + entry);
      }

      if (entry instanceof GroupedAnswer) {

        GroupedAnswer grouped = (GroupedAnswer) entry;
        String group = grouped.getGroup();
        String version = grouped.getDigest();
        BucketDigest bucket = new BucketDigest(grouped.getGroup(), version, isLeaf);
        mapped.put(group, bucket);

      } else if (entry instanceof IndividualAnswer) {

        throw new RuntimeException("Answer should not be atomic: " + entry);

      } else {
        throw new RuntimeException("Received a strange sort of answer");
      }

    }

    return mapped;
  }

}
