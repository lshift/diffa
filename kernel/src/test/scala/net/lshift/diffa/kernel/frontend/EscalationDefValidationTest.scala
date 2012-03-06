package net.lshift.diffa.kernel.frontend

import org.junit.Test

/**
 * Verify that EscalationDef constraints are enforced.
 */

class EscalationDefValidationTest extends DefValidationTestBase {
  @Test
  def shouldRejectNameThatIsTooLong {
    validateExceedsMaxKeyLength("config/pair[key=p]/escalation[name=%s]: name",
      name => EscalationDef(name = name, pair = "p", actionType = "report", event = "scan-completed"))
  }

  @Test
  def shouldRejectPairKeyThatIsTooLong {
    validateExceedsMaxKeyLength("config/pair[key=%s]/escalation[name=a]: pair",
      key => EscalationDef(name = "a", pair = key, actionType = "report", event = "scan-completed"))
  }

  @Test
  def shouldRejectActionThatIsTooLong {
    validateExceedsMaxKeyLength("config/pair[key=p]/escalation[name=a]: action",
      action => EscalationDef(name = "a", pair = "p", action = action, actionType = "report", event = "scan-completed"))
  }
}
