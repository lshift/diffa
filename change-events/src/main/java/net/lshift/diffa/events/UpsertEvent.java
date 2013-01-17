package net.lshift.diffa.events;

import org.joda.time.DateTime;

public interface UpsertEvent extends ChangeEvent {

  String getVersion();
  DateTime getLastUpdated();
}
