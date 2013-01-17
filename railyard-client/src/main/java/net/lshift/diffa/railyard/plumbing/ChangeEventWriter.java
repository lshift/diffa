package net.lshift.diffa.railyard.plumbing;

import net.lshift.diffa.events.ChangeEvent;
import net.lshift.diffa.events.UpsertEvent;
import org.codehaus.jackson.JsonGenerator;

import java.io.IOException;

public class ChangeEventWriter implements EventWriter<ChangeEvent> {

  @Override
  public void writeEvent(ChangeEvent event, JsonGenerator generator) throws IOException {

    generator.writeStartObject();
    generator.writeStringField("id", event.getId());

    if (event instanceof UpsertEvent) {
      UpsertEvent upsert = (UpsertEvent) event;
      generator.writeStringField("version", upsert.getVersion());
    }

    generator.writeEndObject();
  }
}
