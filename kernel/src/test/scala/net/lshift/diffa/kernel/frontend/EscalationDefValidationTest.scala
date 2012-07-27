package net.lshift.diffa.kernel.frontend

import org.junit.Test

/**
 * Verify that EscalationDef constraints are enforced.
 */

class EscalationDefValidationTest extends DefValidationTestBase {
  @Test
  def shouldRejectNameThatIsTooLong {
    validateExceedsMaxKeyLength("config/escalation[name=%s]: name",
      name => EscalationDef(name = name, actionType = "report", event = "scan-completed"))
  }

  @Test
  def shouldRejectActionThatIsTooLong {
    validateExceedsMaxKeyLength("config/escalation[name=a]: action",
      action => EscalationDef(name = "a", action = action, actionType = "report", event = "scan-completed"))
  }
}
