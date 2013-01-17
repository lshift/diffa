package net.lshift.diffa.system;

public class SystemConfigurationClient implements SystemConfiguration {
  @Override
  public Endpoint getEndpoint(String space, String name) {
    return new Endpoint();
  }
}
