package net.lshift.diffa.scanning.plumbing;

import net.lshift.diffa.interview.Answer;
import net.lshift.diffa.scanning.PruningHandler;

import java.util.*;

/**
 * By default this sorts by insertion order of the scan result, which is generally the DB order.
 *
 * If you need a different sort algorithm, you need to provide a suitable comparator.
 */
public class BufferedPruningHandler implements PruningHandler {

  private final Set<Answer> entries;

  public BufferedPruningHandler() {

    this.entries = new LinkedHashSet<Answer>();
  }

  public BufferedPruningHandler(Comparator<Answer> comparator) {
    this.entries = new TreeSet<Answer>(comparator);
  }

  @Override
  public void onPrune(Answer entry) {
    entries.add(entry);
  }

  @Override
  public void onCompletion() {}

  public Set<Answer> getAnswers() {
    return entries;
  }
}
