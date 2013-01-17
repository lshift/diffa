package net.lshift.diffa.system;

public interface SystemConfiguration {

  Endpoint getEndpoint(String space, String name);
}
