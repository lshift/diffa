package net.lshift.diffa.versioning.partitioning;

import net.lshift.diffa.events.ChangeEvent;

public class InvalidEventException extends RuntimeException {

  public InvalidEventException(ChangeEvent event, String problem) {
    super(problem + " with " + event);
  }

}
