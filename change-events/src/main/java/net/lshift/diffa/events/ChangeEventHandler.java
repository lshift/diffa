package net.lshift.diffa.events;

public interface ChangeEventHandler {
  void onEvent(Long endpoint, ChangeEvent event);
}
