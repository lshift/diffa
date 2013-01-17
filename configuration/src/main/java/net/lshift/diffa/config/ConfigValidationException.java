package net.lshift.diffa.config;

public class ConfigValidationException extends RuntimeException {
  public ConfigValidationException(String path, String s) {
    super(path + ": " + s);
  }
}
