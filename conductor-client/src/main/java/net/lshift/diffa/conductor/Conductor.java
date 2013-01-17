package net.lshift.diffa.conductor;

public interface Conductor {

  void registerDriver(String space, String endpoint, DriverConfiguration configuration);

  /**
   * For want of a better word ....
   */
  Long begin(String space, String endpoint);
  InterviewState getProgress(String space, Long id);
}
