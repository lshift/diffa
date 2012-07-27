package net.lshift.diffa.kernel.frontend

import org.junit.Test

/**
 * Verify that PairReportDef constraints are enforced.
 */
class PairReportDefValidationTest extends DefValidationTestBase {
  @Test
  def shouldRejectReportWithNameThatIsTooLong {
    validateExceedsMaxKeyLength("config/report[name=%s]: name",
      name => PairReportDef(name = name, reportType = "differences",
        target="http://someapp.com/handle_report"))
  }

  @Test
  def shouldAcceptReportWithValidReportType() {
    val reportDef = PairReportDef(name = "Process Differences", reportType = "differences",
      target = "http://someapp.com/handle_report")
    reportDef.validate("config")
  }

  @Test
  def shouldRejectReportWithInvalidReportType() {
    validateError(
      PairReportDef(name = "Process Differences", reportType = "blah-blah",
        target = "http://someapp.com/handle_report"),
      "config/report[name=Process Differences]: Invalid report type: blah-blah"
    )
  }

  @Test
  def shouldRejectReportWithMissingTarget() {
    validateError(
      PairReportDef(name = "Process Differences", reportType = "differences"),
      "config/report[name=Process Differences]: Missing target"
    )
  }

  @Test
  def shouldRejectReportWithInvalidTarget() {
    validateError(
      PairReportDef(name = "Process Differences", reportType = "differences",
        target = "random-target"),
      "config/report[name=Process Differences]: Invalid target (not a URL): random-target"
    )
  }
}
