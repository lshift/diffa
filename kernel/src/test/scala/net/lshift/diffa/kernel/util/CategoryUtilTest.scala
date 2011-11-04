package net.lshift.diffa.kernel.util

import org.junit.Test
import org.junit.Assert._
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.config.{EndpointView, RangeCategoryDescriptor, SetCategoryDescriptor}

class CategoryUtilTest {
  val baseStringCategory = new SetCategoryDescriptor(Set("a", "b"))
  val baseIntCategory = new RangeCategoryDescriptor("integer", "5", "12")
  val baseDateCategory = new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31", "individual")
  val stringOverrideCategory = new SetCategoryDescriptor(Set("a"))


  val endpointCategories =
    Map("someString" -> baseStringCategory, "someInt" -> baseIntCategory, "someDate" -> baseDateCategory)
  val stringOverrideCategories = Map("someString" -> stringOverrideCategory)
  val dateOverrideCategories = Map("someDate" -> new RangeCategoryDescriptor("date", "2011-05-05", "2011-06-01"))
  val dateOverrideWithGranCategories = Map("someDate" -> new RangeCategoryDescriptor("date", "2011-05-05", "2011-06-01", "monthly"))
  val views = Seq(
    EndpointView(name = "lessStrings", categories = stringOverrideCategories),
    EndpointView(name = "lessDates", categories = dateOverrideCategories),
    EndpointView(name = "lessDatesMoreGranularity", categories = dateOverrideWithGranCategories)
  )

  @Test
  def shouldReturnEndpointCategoriesWhenViewIsNone() {
    val fused = CategoryUtil.fuseViewCategories(endpointCategories, views, None)
    assertEquals(endpointCategories, fused)
  }

  @Test
  def shouldApplySetCategoryOverride() {
    val fused = CategoryUtil.fuseViewCategories(endpointCategories, views, Some("lessStrings"))
    assertEquals(
      Map("someString" -> stringOverrideCategory, "someInt" -> baseIntCategory, "someDate" -> baseDateCategory),
      fused)
  }

  @Test
  def shouldApplyDateCategoryOverrideAndInheritGranularitySettings() {
    val fused = CategoryUtil.fuseViewCategories(endpointCategories, views, Some("lessDates"))
    val fusedDateCategory = new RangeCategoryDescriptor("date", "2011-05-05", "2011-06-01", "individual")
    assertEquals(
      Map("someString" -> baseStringCategory, "someInt" -> baseIntCategory, "someDate" -> fusedDateCategory),
      fused)
  }

  @Test
  def shouldApplyDateCategoryOverrideAndOverrideGranularitySettings() {
    val fused = CategoryUtil.fuseViewCategories(endpointCategories, views, Some("lessDatesMoreGranularity"))
    val fusedDateCategory = new RangeCategoryDescriptor("date", "2011-05-05", "2011-06-01", "monthly")
    assertEquals(
      Map("someString" -> baseStringCategory, "someInt" -> baseIntCategory, "someDate" -> fusedDateCategory),
      fused)
  }

  @Test
  def shouldInferDateBoundsFromUpperToLower() {
    val unboundedUpperDateCategory = Map("someDate" -> new RangeCategoryDescriptor("date", "2011-01-01", null, "individual"))
    val unboundedLowerDateCategory = Map("someDate" -> new RangeCategoryDescriptor("date", null, "2011-12-31", "individual"))

    val undoundedLowerView = Seq(EndpointView(name = "dates", categories = unboundedLowerDateCategory))
    val undoundedUpperView = Seq(EndpointView(name = "dates", categories = unboundedUpperDateCategory))

    val fusedForwards = CategoryUtil.fuseViewCategories(unboundedUpperDateCategory, undoundedLowerView, Some("dates"))
    val fusedBackwards = CategoryUtil.fuseViewCategories(unboundedLowerDateCategory, undoundedUpperView, Some("dates"))

    assertEquals(
      Map("someDate" -> new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31", "individual")),
      fusedForwards)

    assertEquals(
      Map("someDate" -> new RangeCategoryDescriptor("date", "2011-01-01", "2011-12-31", "individual")),
      fusedBackwards)
  }

}