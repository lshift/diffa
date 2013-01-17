package net.lshift.diffa.versioning.plumbing;

public class EntityNotFoundException extends RuntimeException{

  public EntityNotFoundException(String message) {
    super(message);
  }
}
