package net.lshift.diffa.interview;

import java.util.Iterator;

public class NoFurtherQuestions implements Iterable<Question> {

  public static final Iterable<Question> NO_FURTHER_QUESTIONS = new NoFurtherQuestions();

  public static Iterable<Question> get() {
    return NO_FURTHER_QUESTIONS;
  }

  @Override
  public Iterator<Question> iterator() {

    return new Iterator<Question>() {
      @Override
      public boolean hasNext() {
        return false;
      }

      @Override
      public Question next() {
        return null;
      }

      @Override
      public void remove() {}
    };
  }
}
