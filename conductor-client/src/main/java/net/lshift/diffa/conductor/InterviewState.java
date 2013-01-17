package net.lshift.diffa.conductor;

public class InterviewState {

  private Long id;
  private String state;
  private String start, end;

  public InterviewState() {
  }

  public InterviewState(Long id, String state, String start) {
    this.id = id;
    this.state = state;
    this.start = start;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public void setStart(String start) {
    this.start = start;
  }

  public void setEnd(String end) {
    this.end = end;
  }

  public String getStart() {
    return start;
  }

  public String getEnd() {

    return end;
  }
}
