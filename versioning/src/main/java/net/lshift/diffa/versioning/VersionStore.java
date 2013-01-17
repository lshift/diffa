package net.lshift.diffa.versioning;

import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.adapter.scanning.ScanConstraint;
import net.lshift.diffa.adapter.scanning.ScanRequest;
import net.lshift.diffa.adapter.scanning.ScanResultEntry;
import net.lshift.diffa.events.ChangeEventHandler;
import net.lshift.diffa.interview.Answer;
import net.lshift.diffa.interview.Question;

import java.util.List;
import java.util.Set;

public interface VersionStore extends ChangeEventHandler {

  /**
   * Kick off the deltafication - this is a mutitative call into the version store to materialize differences between endpoints
   */
  void deltify(PairProjection view);

  /**
   * Find out what differences exist for a given pairing
   */
  TreeLevelRollup getDeltaDigest(PairProjection view);

  /**
   * Materialize all diffs for the given
   */
  List<EntityDifference> getOutrightDifferences(PairProjection view, String bucket);

  /**
   * This is the interview process that remote applications initiate when they
   * need to sync themselves with the version store
   */
  Iterable<Question> continueInterview(Long endpoint,
                                       Set<ScanConstraint> constraints,
                                       Set<ScanAggregation> aggregations,
                                       Iterable<Answer> entries);


  void setMaxSliceSize(Long endpoint, int size);
}