package net.lshift.diffa.kernel.frontend

import org.junit.Assert._
import net.lshift.diffa.kernel.config.ConfigValidationException

/**
 * Common type and validation utilities for testing the validation constraints
 * of the various *Def classes.
 */
trait DefValidationTestBase {
  type Validatable = {
    def validate(path: String): Unit
  }

  def validateError(v:Validatable, msg:String) {
    try {
      v.validate("config")
      fail("Should have thrown ConfigValidationException")
    } catch {
      case e:ConfigValidationException =>
        assertEquals(msg, e.getMessage)
    }
  }

  def validateAcceptsAll(values:Seq[String], fn: String => Validatable) {
    values.foreach(v => fn(v).validate("config"))
  }

  def validateExceedsMaxKeyLength(msg: String, fn: String => Validatable) {
    validateExceedsMaxColLength(DefaultLimits.KEY_LENGTH_LIMIT, msg, fn)
  }

  def validateExceedsMaxUrlLength(msg: String, fn: String => Validatable) {
    validateExceedsMaxColLength(DefaultLimits.URL_LENGTH_LIMIT, msg, fn)
  }

  def validateExceedsMaxColLength(maxLength: Int, msg: String, fn: String => Validatable) {
    val len = maxLength + 1
    val name = "a" * len
    validateError(
      fn(name), msg.format(name) + " is too long. Limit is %d, value %s is %d".format(
        maxLength, name, len))
  }

  def assertIsValid(v: Validatable) {
    try {
      v.validate("config")
    } catch {
      case e:ConfigValidationException => fail("Validation of %s failed with message: %s".format(v, e.getMessage))
    }
  }
}
