package net.lshift.diffa.interview;

import org.joda.time.DateTime;

import java.util.Map;

public interface IndividualAnswer extends Answer {
  String getId();
  Map<String,String> getAttributes();
  DateTime getLastUpdate();
}
