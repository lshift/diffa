package net.lshift.diffa.events;

import org.joda.time.DateTime;

public class SimpleUpsertEvent implements UpsertEvent {

  protected final String id;
  protected final String version;
  protected final DateTime lastUpdated;

  public SimpleUpsertEvent(String id, String version, DateTime lastUpdated) {
    this.lastUpdated = lastUpdated;
    this.version = version;
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }

  public String getVersion() {
    return version;
  }

  public DateTime getLastUpdated() {
    return lastUpdated;
  }
}
