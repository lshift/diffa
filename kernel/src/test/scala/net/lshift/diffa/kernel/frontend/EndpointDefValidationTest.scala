package net.lshift.diffa.kernel.frontend

import org.junit.Test

/**
 * Verify that EndpointDef constraints are enforced.
 */
class EndpointDefValidationTest extends DefValidationTestBase {
  @Test
  def shouldAcceptEndpointWithNameThatIsMaxLength {
    List(
      "a",
      "a" * DefaultLimits.KEY_LENGTH_LIMIT
    ) foreach {
      key =>
        EndpointDef(name = key).validate("config/endpoint")
    }
  }

  @Test
  def shouldRejectEndpointWithoutName {
    validateError(new EndpointDef(name = null), "config/endpoint[name=null]: name cannot be null or empty")
  }

  @Test
  def shouldRejectEndpointWithNameThatIsTooLong {
    validateExceedsMaxKeyLength("config/endpoint[name=%s]: name",
      name => EndpointDef(name = name))
  }

  @Test
  def shouldRejectEndpointWithScanUrlThatIsTooLong {
    validateExceedsMaxUrlLength("config/endpoint[name=a]: scanUrl",
      url => EndpointDef(name = "a", scanUrl = url))
  }

  @Test
  def shouldRejectEndpointWithContentRetrievalUrlThatIsTooLong {
    validateExceedsMaxUrlLength("config/endpoint[name=a]: contentRetrievalUrl",
      url => EndpointDef(name = "a", contentRetrievalUrl = url))
  }

  @Test
  def shouldRejectEndpointWithVersionGenerationUrlThatIsTooLong {
    validateExceedsMaxUrlLength("config/endpoint[name=a]: versionGenerationUrl",
      url => EndpointDef(name = "a", versionGenerationUrl = url))
  }

  @Test
  def shouldRejectEndpointWithInboundUrlThatIsTooLong {
    validateExceedsMaxUrlLength("config/endpoint[name=a]: inboundUrl",
      url => EndpointDef(name = "a", inboundUrl = url))
  }
}
