package net.lshift.diffa.kernel.hooks

/**
 * Hook that can be registered to provide database-specific partitioning assistance when working with differences.
 */
trait DifferencePartitioningHook {
  /**
   * Informs the hook that a pair has been created. Will be called after the pair creation transaction has completed.
   */
  def pairCreated(space:Long, key:String)

  /**
   * Informs the hook that a pair has been removed. Will be called after the pair removal transaction has completed.
   */
  def pairRemoved(space:Long, key:String)

  /**
   * Requests that the hook provide an optimised method for removing all differences for a pair. The hook should return
   * true to indicate that it was able to assist, or false to indicate that a normal DELETE should be issued.
   */
  def removeAllPairDifferences(space:Long, key:String):Boolean

  /**
   * Queries whether difference partitioning is generally enabled by the hook. Note that even if this method returns
   * false, it is still safe to call the various other methods - they must be implemented as no-ops if not supported.
   * This method should generally be queried before performing any expensive work in order to use a hook method.
   */
  def isDifferencePartitioningEnabled:Boolean
}