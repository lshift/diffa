package net.lshift.diffa.interview;

import org.joda.time.DateTime;

import java.util.Map;

public class SimpleIndividualAnswer implements IndividualAnswer {

  private final SerializableIndividualAnswer answer;

  public SimpleIndividualAnswer(SerializableIndividualAnswer answer) {
    this.answer = answer;
  }


  @Override
  public String getId() {
    return answer.getId();
  }

  @Override
  public Map<String, String> getAttributes() {
    return answer.getAttributes();
  }

  @Override
  public DateTime getLastUpdate() {
    if (answer.getLastUpdated() != null) {
      return new DateTime(answer.getLastUpdated());
    }
    else {
      return null;
    }
  }

  @Override
  public String getDigest() {
    return answer.getDigest();
  }
}
