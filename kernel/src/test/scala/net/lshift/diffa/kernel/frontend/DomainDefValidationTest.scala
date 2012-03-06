package net.lshift.diffa.kernel.frontend

import org.junit.Test

/**
 * Verify that DomainDef constraints are enforced.
 */
class DomainDefValidationTest extends DefValidationTestBase {
  @Test
  def shouldRejectDomainNameThatIsTooLong {
    validateExceedsMaxKeyLength("config/domain[name=%s]: name",
      domain => DomainDef(name = domain))
  }
}
