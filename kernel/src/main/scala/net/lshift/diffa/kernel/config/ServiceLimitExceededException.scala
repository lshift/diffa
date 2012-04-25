package net.lshift.diffa.kernel.config

/**
 * Indicates that a service limit has been exceeded.
 */
class ServiceLimitExceededException(message: String) extends Exception(message)