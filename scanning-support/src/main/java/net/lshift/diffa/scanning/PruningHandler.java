package net.lshift.diffa.scanning;

import net.lshift.diffa.interview.Answer;

public interface PruningHandler {

  void onPrune(Answer entry);

  void onCompletion();
}
