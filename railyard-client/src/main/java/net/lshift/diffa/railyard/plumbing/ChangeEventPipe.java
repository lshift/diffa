package net.lshift.diffa.railyard.plumbing;

import net.lshift.diffa.events.ChangeEvent;
import org.codehaus.jackson.JsonFactory;

import java.util.concurrent.ExecutorService;

public class ChangeEventPipe extends EventPipe<ChangeEvent> {

  public ChangeEventPipe(Iterable<ChangeEvent> events, ExecutorService executorService, JsonFactory factory) {
    super(events, new ChangeEventWriter(), executorService, factory);
  }
}
