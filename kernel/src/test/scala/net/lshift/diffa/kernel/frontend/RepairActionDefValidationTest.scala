package net.lshift.diffa.kernel.frontend

import org.junit.Test

/**
 * Verify that RepairActionDef constraints are enforced.
 */
class RepairActionDefValidationTest extends DefValidationTestBase {
  @Test
  def shouldRejectNameThatIsTooLong {
    validateExceedsMaxKeyLength("config/pair[key=p]/repair-action[name=%s]: name",
      name => RepairActionDef(name = name, scope = "pair", pair = "p"))
  }

  @Test
  def shouldRejectPairKeyThatIsTooLong {
    validateExceedsMaxKeyLength("config/pair[key=%s]/repair-action[name=a]: pair",
      key => RepairActionDef(name = "a", scope = "pair", pair = key))
  }
}
