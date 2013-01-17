package net.lshift.diffa.versioning.plumbing;


public class BucketNotFoundException extends RuntimeException {

  public BucketNotFoundException(String message) {
    super(message);
  }
}
