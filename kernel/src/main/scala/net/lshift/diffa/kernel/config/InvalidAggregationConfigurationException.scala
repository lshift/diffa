package net.lshift.diffa.kernel.config

class InvalidAggregationConfigurationException(path: String)
  extends ConfigValidationException(path, "A strict collation order is required when aggregation is enabled.")
