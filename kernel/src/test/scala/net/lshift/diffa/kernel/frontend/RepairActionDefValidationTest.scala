package net.lshift.diffa.kernel.frontend

import org.junit.Test

/**
 * Verify that RepairActionDef constraints are enforced.
 */
class RepairActionDefValidationTest extends DefValidationTestBase {
  @Test
  def shouldRejectNameThatIsTooLong {
    validateExceedsMaxKeyLength("config/repair-action[name=%s]: name",
      name => RepairActionDef(name = name, scope = "pair"))
  }
}
