package net.lshift.diffa.interview;

public class SimpleGroupedAnswer implements GroupedAnswer {

  private final SerializableGroupedAnswer answer;

  public SimpleGroupedAnswer(SerializableGroupedAnswer answer) {
    this.answer = answer;
  }

  public SimpleGroupedAnswer(String group, String digest) {
    this.answer = SerializableGroupedAnswer.newBuilder().setDigest(digest).setGroup(group).build();
  }

  @Override
  public String getGroup() {
    return answer.getGroup();
  }

  @Override
  public String getDigest() {
    return answer.getDigest();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof SimpleGroupedAnswer) {
      SimpleGroupedAnswer that = (SimpleGroupedAnswer) o;
      return that.getGroup().equals(this.getGroup()) && that.getDigest().equals(this.getDigest());
    }
    else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return 31 * getDigest().hashCode() + getGroup().hashCode();
  }

  @Override
  public String toString() {
    return "SimpleGroupedAnswer{digest = " + getDigest() +", group = " + getGroup() + "}";
  }
}
