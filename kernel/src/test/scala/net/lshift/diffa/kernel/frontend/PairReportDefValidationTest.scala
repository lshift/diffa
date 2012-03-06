package net.lshift.diffa.kernel.frontend

import org.junit.Test

/**
 * Verify that PairReportDef constraints are enforced.
 */
class PairReportDefValidationTest extends DefValidationTestBase {
  @Test
  def shouldRejectReportWithNameThatIsTooLong {
    validateExceedsMaxKeyLength("config/pair[key=p]/report[name=%s]: name",
      name => PairReportDef(name = name, pair = "p", reportType = "differences",
        target="http://someapp.com/handle_report"))
  }

  @Test
  def shouldRejectPairKeyThatIsTooLong {
    validateExceedsMaxKeyLength("config/pair[key=%s]/report[name=a]: pair",
      key => PairReportDef(name = "a", pair = key, reportType = "differences",
        target="http://someapp.com/handle_report"))
  }

  @Test
  def shouldAcceptReportWithValidReportType() {
    val reportDef = PairReportDef(name = "Process Differences", pair ="p", reportType = "differences",
      target = "http://someapp.com/handle_report")
    reportDef.validate("config")
  }

  @Test
  def shouldRejectReportWithInvalidReportType() {
    validateError(
      PairReportDef(name = "Process Differences", pair ="p", reportType = "blah-blah",
        target = "http://someapp.com/handle_report"),
      "config/pair[key=p]/report[name=Process Differences]: Invalid report type: blah-blah"
    )
  }

  @Test
  def shouldRejectReportWithMissingTarget() {
    validateError(
      PairReportDef(name = "Process Differences", pair ="p", reportType = "differences"),
      "config/pair[key=p]/report[name=Process Differences]: Missing target"
    )
  }

  @Test
  def shouldRejectReportWithInvalidTarget() {
    validateError(
      PairReportDef(name = "Process Differences", pair ="p", reportType = "differences",
        target = "random-target"),
      "config/pair[key=p]/report[name=Process Differences]: Invalid target (not a URL): random-target"
    )
  }
}
