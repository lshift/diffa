package net.lshift.diffa.railyard.plumbing;


import net.lshift.diffa.interview.Answer;
import org.codehaus.jackson.JsonFactory;

import java.util.concurrent.ExecutorService;

public class AnswerEventPipe extends EventPipe<Answer>{

  public AnswerEventPipe(Iterable<Answer> events, ExecutorService executorService, JsonFactory factory) {
    super(events, new AnswerEventWriter(), executorService, factory);
  }
}
