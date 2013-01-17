package net.lshift.diffa.config;


public class Validations {

  /**
   * Validates a field that is required to be present and not empty.
   */
  public static void requiredAndNotEmpty(String path, String name, String value) {
    if (value == null || value.isEmpty()) {
      throw new ConfigValidationException(path, name + " cannot be null or empty");
    }
  }
}
