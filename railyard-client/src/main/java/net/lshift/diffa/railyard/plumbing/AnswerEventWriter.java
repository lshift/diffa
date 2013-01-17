package net.lshift.diffa.railyard.plumbing;

import net.lshift.diffa.interview.Answer;
import org.codehaus.jackson.JsonGenerator;

import java.io.IOException;

public class AnswerEventWriter implements EventWriter<Answer> {
  @Override
  public void writeEvent(Answer event, JsonGenerator generator) throws IOException {
    //To change body of implemented methods use File | Settings | File Templates.
  }
}
