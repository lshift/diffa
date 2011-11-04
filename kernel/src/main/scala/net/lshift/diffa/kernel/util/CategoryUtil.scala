package net.lshift.diffa.kernel.util

import net.lshift.diffa.kernel.participants.StringPrefixCategoryFunction._
import scala.Option._
import net.lshift.diffa.kernel.config._
import net.lshift.diffa.kernel.participants.{StringPrefixCategoryFunction, CategoryFunction}
import scala.collection.JavaConversions._
import net.lshift.diffa.participant.scanning.{SetConstraint, ScanConstraint}

/**
 * Utility for transforming categories into
 */

object CategoryUtil {
  /**
   * Takes a base set of endpoint categories, a list of views, and maybe a view name, and returns the fused set of
   * categories for that view. These categories can then be used in other functions to determine bucketing or
   * constraints.
   */
  def fuseViewCategories(categories: Map[String,CategoryDescriptor], views:Iterable[EndpointView], view:Option[String]):Map[String,CategoryDescriptor] = {
    view match {
      case None           =>
        categories
      case Some(viewName) =>
        val viewCategories = views.find(v => v.name == viewName).
          getOrElse(throw new RuntimeException("Unknown view " + viewName)).
          categories

        categories ++ viewCategories.map { case (k, cat) => k -> categories(k).applyRefinement(cat) }
    }
  }

  /**
   * For a set of categories, returns the initial bucketing options.
   */
  def initialBucketingFor(categories: Iterable[(String,CategoryDescriptor)]) : Seq[CategoryFunction] = {
    categories.flatMap {
      case (name, categoryType) => {
        categoryType match {
          // #203: By default, set elements should be sent out individually. The default behaviour for an
          // un-aggregated attribute is to handle it by name, so we don't need to return any bucketing for it.
          case s:SetCategoryDescriptor    => None
          case r:RangeCategoryDescriptor  => RangeTypeRegistry.defaultCategoryFunction(name, r)
          case p:PrefixCategoryDescriptor => Some(StringPrefixCategoryFunction(name, p.prefixLength, p.maxLength, p.step))
        }
      }
    }.toSeq
  }

  /**
   * Derives the result of the initialConstraintsFor call and returns the constraints grouped into batches that can
   * be submitted to a participant. This allows for the optimum number of calls to a participant to be executed.
   */
  def groupConstraints(categories: Iterable[(String,CategoryDescriptor)]) : Seq[Seq[ScanConstraint]] = {
    val constraints = initialConstraintsFor(categories).map {
      /**
       * #203: By default, set elements should be sent out individually - in the future, this may be configurable
       */
      case sc:SetConstraint =>
        sc.getValues.map(v => new SetConstraint(sc.getAttributeName, Set(v))).toSeq
      case c                =>
        Seq(c)
    }
    if (constraints.length > 0) {
      constraints.map(_.map(Seq(_))).reduceLeft((acc, nextConstraints) => for {a <- acc; c <- nextConstraints} yield a ++ c)
    } else {
      Seq()
    }
  }

  /**
   * Returns the initial constraints that should be used when running a set of queries against a participant, based on
   * any bounds defined with the categories.
   */
  def initialConstraintsFor(categories:Iterable[(String,CategoryDescriptor)]) : Seq[ScanConstraint] =
    categories.flatMap({
      case (name, categoryType) => {
        categoryType match {
          case s:SetCategoryDescriptor   =>
            Some(new SetConstraint(name, s.values))
          case r:RangeCategoryDescriptor => {
            if (r.lower == null && r.upper == null) {
              None
            }
            else {
              Some(r.toConstraint(name))
            }
          }
          case p:PrefixCategoryDescriptor =>
            None
        }
      }
    }).toList
}