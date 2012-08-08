package net.lshift.diffa.kernel.frontend

import org.junit.Test

/**
 * Verify that EscalationDef constraints are enforced.
 */

class EscalationDefValidationTest extends DefValidationTestBase {
  @Test
  def shouldRejectNameThatIsTooLong {
    validateExceedsMaxKeyLength("config/escalation[name=%s]: name",
      name => EscalationDef(name = name, actionType = "report", rule = "scan-completed"))
  }

  @Test
  def shouldRejectActionThatIsTooLong {
    validateExceedsMaxKeyLength("config/escalation[name=a]: action",
      action => EscalationDef(name = "a", action = action, actionType = "report", rule = "scan-completed"))
  }

  @Test
  def shouldRejectRuleThatIsTooLong {
    validateExceedsMaxUrlLength("config/escalation[name=a]: rule",
      rule => EscalationDef(name = "a", action = "", actionType = "ignore", rule = rule))
  }

  @Test
  def validateRejectsInvalidRuleDefinition {
    validateError(EscalationDef(name = "a", actionType = "ignore", rule = "blah"),
      "config/escalation[name=a]: invalid rule 'blah': Unable to create getter: blah"
    )
  }

  @Test
  def validateAcceptsScanCompletedRuleForReportActionType {
    EscalationDef(name = "a", actionType = "report", action = "r1", rule = "scan-completed").validate()
  }

  @Test
  def validateAcceptsScanFailedRuleForReportActionType {
    EscalationDef(name = "a", actionType = "report", action = "r1", rule = "scan-failed").validate()
  }

  @Test
  def validateAcceptsRuleForUpstreamVsn {
    validateValidEscalationRule("upstream is null")
  }

  @Test
  def validateAcceptsRuleForDownstreamVsn {
    validateValidEscalationRule("downstream is null")
  }

  @Test
  def validateAcceptsRuleForId {
    validateValidEscalationRule("id like 'a*'")
  }

  @Test
  def validateAcceptsRuleForMismatch {
    validateValidEscalationRule("mismatch")
  }

  @Test
  def validateAcceptsRuleForUpstreamMissing {
    validateValidEscalationRule("upstreamMissing")
  }

  @Test
  def validateAcceptsRuleForDownstreamMissing {
    validateValidEscalationRule("downstreamMissing")
  }

  @Test
  def validateAcceptsRuleForHasUpstream {
    validateValidEscalationRule("hasUpstream")
  }

  @Test
  def validateAcceptsRuleForHasDownstream {
    validateValidEscalationRule("hasDownstream")
  }

  @Test
  def validateAcceptsNullRule {
    validateValidEscalationRule(null)
  }

  @Test
  def validateAcceptsEmptyRule {
    validateValidEscalationRule("")
  }

  private def validateValidEscalationRule(rule:String) {
    EscalationDef(name = "a", actionType = "ignore", rule = rule).validate()
  }
}
