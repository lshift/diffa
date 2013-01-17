package net.lshift.diffa.config;

public class InvalidAggregationConfigurationException extends ConfigValidationException {
  public InvalidAggregationConfigurationException(String path) {
    super(path, "A strict collation order is required when aggregation is enabled.");
  }
}
