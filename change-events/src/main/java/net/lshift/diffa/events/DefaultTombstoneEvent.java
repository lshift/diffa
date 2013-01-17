package net.lshift.diffa.events;


public class DefaultTombstoneEvent implements TombstoneEvent {

  private final String id;

  public DefaultTombstoneEvent(String id) {
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }
}
