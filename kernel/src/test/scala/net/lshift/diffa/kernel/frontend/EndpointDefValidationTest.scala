package net.lshift.diffa.kernel.frontend

import org.junit.Test
import scala.collection.JavaConversions._
import org.junit.Assert
import org.junit.Assert.assertEquals
import net.lshift.diffa.kernel.config
import config.{UnicodeCollationOrdering, AsciiCollationOrdering, UnorderedCollationOrdering}
import config.{CategoryDescriptor, RangeCategoryDescriptor, PrefixCategoryDescriptor, SetCategoryDescriptor}
import org.junit.runner.RunWith
import org.junit.experimental.theories.{Theory, DataPoint, Theories}

/**
 * Verify that EndpointDef constraints are enforced.
 */
@RunWith(classOf[Theories])
class EndpointDefValidationTest extends DefValidationTestBase {
  import EndpointDefValidationTest.Scenario

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

  @Test
  def shouldRejectEndpointWithUnnamedCategory() {
    validateError(
      new EndpointDef(name = "e1", categories = Map("" -> new RangeCategoryDescriptor())),
      "config/endpoint[name=e1]/category[name=]: name cannot be null or empty")
  }

  @Test
  def shouldRejectEndpointWithInvalidCategoryDescriptor() {
    validateError(
      new EndpointDef(name = "e1", categories = Map("cat1" -> new RangeCategoryDescriptor())),
      "config/endpoint[name=e1]/category[name=cat1]: dataType cannot be null or empty")
  }

  @Test
  def shouldDefaultToAsciiOrdering() = {
    val endpoint = EndpointDef(name="dummy")
    assertEquals(AsciiCollationOrdering.name, endpoint.collation)
  }

  def shouldAcceptEndpointWithAsciiCollation {
    val endpoint = EndpointDef(name="dummy", collation="ascii")
    assertIsValid(endpoint)

    assertEquals(AsciiCollationOrdering.name, endpoint.collation)
  }

  @Test
  def shouldAcceptEndpointWithUnicodeCollation {
    val endpoint = EndpointDef(name="dummy", collation="unicode")
    assertIsValid(endpoint)
    assertEquals(UnicodeCollationOrdering.name, endpoint.collation)
  }

  @Test
  def shouldRejectInvalidCollation {
    val endpoint = EndpointDef(name="dummy", collation="dummy")
    validateError(endpoint, "config/endpoint[name=dummy]: collation is invalid. dummy is not a member of the set Set(unordered, unicode, ascii)")
  }

  @Theory
  def shouldRejectNoOrderingWithAggregation(scenario: Scenario) {
    val endpoint = EndpointDef(name = "ep", collation = UnorderedCollationOrdering.name, categories = Map("c" -> scenario.aggregatingCategory))
    validateError(endpoint, "config/endpoint[name=ep]/category[name=c]: A strict collation order is required when aggregation is enabled.")
  }

  @Test
  def shouldAcceptAsciiOrderingWithAggregation {
    val endpoint = EndpointDef(name="aggregated-ascii", collation=AsciiCollationOrdering.name,
      categories = Map("agg" -> new RangeCategoryDescriptor("int")))
    assertIsValid(endpoint)
    Assert.assertNotSame("individual", endpoint.categories.get("agg").asInstanceOf[RangeCategoryDescriptor].getMaxGranularity)
    assertEquals(AsciiCollationOrdering.name, endpoint.collation)
  }

  @Test
  def shouldAcceptUnicodeOrderingWithAggregation {
    val endpoint = EndpointDef(name="aggregated-unicode", collation=UnicodeCollationOrdering.name,
      categories = Map("agg" -> new RangeCategoryDescriptor("int")))
    assertIsValid(endpoint)
    Assert.assertNotSame("individual", endpoint.categories.get("agg").asInstanceOf[RangeCategoryDescriptor].getMaxGranularity)
    assertEquals(UnicodeCollationOrdering.name, endpoint.collation)
  }

  @Test
  def shouldAcceptNoOrderingWithoutAggregationWithRangeCategory {
    val endpoint = EndpointDef(name="unaggregated-unordered", collation = UnorderedCollationOrdering.name,
      categories = Map("ind" -> new RangeCategoryDescriptor("date", "2012-01-01", "2012-01-02", "individual")))
    assertIsValid(endpoint)
    assertEquals("individual", endpoint.categories.get("ind").asInstanceOf[RangeCategoryDescriptor].getMaxGranularity)
    assertEquals(UnorderedCollationOrdering.name, endpoint.collation)
  }
}

object EndpointDefValidationTest {
  case class Scenario(aggregatingCategory: CategoryDescriptor)

  @DataPoint def yearly = Scenario(new RangeCategoryDescriptor("date", "2012-01-01", "2013-01-01", "yearly"))
  @DataPoint def monthly = Scenario(new RangeCategoryDescriptor("date", "2012-01-01", "2012-02-01", "monthly"))
  @DataPoint def daily = Scenario(new RangeCategoryDescriptor("date", "2012-01-01", "2012-01-02", "daily"))
  @DataPoint def setCat = Scenario(new SetCategoryDescriptor(Set("a")))
  @DataPoint def prefixCat = Scenario(new PrefixCategoryDescriptor(1, 2, 1))
}
