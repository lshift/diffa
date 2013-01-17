package net.lshift.diffa.railyard.plumbing;

import org.codehaus.jackson.JsonGenerator;

import java.io.IOException;

public interface EventWriter<T> {
  void writeEvent(T event, JsonGenerator generator) throws IOException;
}
