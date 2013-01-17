package net.lshift.diffa.conductor.plumbing;

import net.lshift.diffa.adapter.scanning.ScanResultEntry;
import net.lshift.diffa.interview.Answer;
import net.lshift.diffa.scanning.PruningHandler;

import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterviewResultHandler implements PruningHandler, Iterable<Answer>, Iterator<Answer> {

  private int bufferSize = 100;
  private final long firstReadTimeout = 30 * 1000L;

  private AtomicBoolean complete = new AtomicBoolean(false);
  private ArrayBlockingQueue<Answer> buffer = new ArrayBlockingQueue<Answer>(bufferSize);

  @Override
  public void onPrune(Answer answer) {

    try {

      buffer.put(answer);

    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public void onCompletion() {
    complete.set(true);
  }

  @Override
  public Iterator<Answer> iterator() {
    return this;
  }

  @Override
  public boolean hasNext() {

    if (complete.get()) {
      return buffer.size() > 0;
    }
    else {

      // TODO This case needs testing .......

      if (buffer.isEmpty()) {

        long timeToBlock = 1L;

        while (timeToBlock < firstReadTimeout) {

          try {
            Thread.sleep(timeToBlock);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }

          Answer answer = buffer.peek();

          if (answer == null) {
            timeToBlock *= 2;
          }
          else {
            return true;
          }

        }

        throw new RuntimeException("Timed out waiting for first element after " + firstReadTimeout + " ms");

      }
      else {
        return true;
      }

    }

  }

  @Override
  public Answer next() {

    if (complete.get() && buffer.isEmpty()) {

      return null;

    }
    else {

      try {

        return buffer.take();

      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

    }

  }

  @Override
  public void remove() {
    throw new RuntimeException("Remove not supported");
  }
}
