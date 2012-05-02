package net.lshift.diffa.kernel.util

/**
 * Lazily evaluate the block passed in the constructor.
 *
 * Example:
 * val lazy = new Lazy({expensiveOperation(param)})
 * val result = lazy() // valueFn block is executed here.
 */
class Lazy[T](valueFn: => T) extends (() => T) {
  def apply() = value
  lazy val value = valueFn
}