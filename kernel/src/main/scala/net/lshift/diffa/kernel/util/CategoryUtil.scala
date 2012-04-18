/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.lshift.diffa.kernel.util

import net.lshift.diffa.kernel.participants.StringPrefixCategoryFunction._
import scala.Option._
import net.lshift.diffa.kernel.config._
import scala.collection.JavaConversions._
import collection.immutable.Map
import net.lshift.diffa.participant.scanning._
import net.lshift.diffa.kernel.participants.{IntegerCategoryFunction, ByNameCategoryFunction, StringPrefixCategoryFunction, CategoryFunction}

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
   * Creates Category Functions for the given aggregations.
   */
  def categoryFunctionsFor(aggregations:Seq[ScanAggregation], categories: Iterable[(String,CategoryDescriptor)]): Seq[CategoryFunction] = {
    val mappedCategories = categories.toMap

    aggregations.map {
      case bn:ByNameAggregation       =>
        ByNameCategoryFunction(bn.getAttributeName)
      case sp:StringPrefixAggregation =>
        val cat = mappedCategories(sp.getAttributeName).asInstanceOf[PrefixCategoryDescriptor]
        StringPrefixCategoryFunction(sp.getAttributeName, sp.getLength, cat.maxLength, cat.step)
      case i:IntegerAggregation =>
        IntegerCategoryFunction(i.getAttributeName, i.getGranularity, 10)   // TODO: Allow the factor to be configured via the descriptor
      case d:DateAggregation =>
        val cat = mappedCategories(d.getAttributeName).asInstanceOf[RangeCategoryDescriptor]
        RangeTypeRegistry.categoryFunctionFor(d.getAttributeName, d.getGranularity, cat.getDataType)
    }
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

  /**
   * Configures a ConstraintBuilder based on the given category descriptors.
   */
  def buildConstraints(builder:ConstraintsBuilder, descriptors:Map[String, CategoryDescriptor]) {
    descriptors.foreach {
      case (name, _:SetCategoryDescriptor)    => builder.maybeAddSetConstraint(name)
      case (name, _:PrefixCategoryDescriptor) => builder.maybeAddStringPrefixConstraint(name)
      case (name, r:RangeCategoryDescriptor)  => RangeTypeRegistry.buildConstraint(builder, name, r)
    }
  }

  /**
   * Configures an AggregationBuilder based on the given category descriptors.
   */
  def buildAggregations(builder: AggregationBuilder, descriptors: Map[String, CategoryDescriptor]) {
    descriptors.foreach {
      case (name, _:SetCategoryDescriptor)    => // Nothing to do
      case (name, _:PrefixCategoryDescriptor) => builder.maybeAddStringPrefixAggregation(name)
      case (name, r:RangeCategoryDescriptor)  => RangeTypeRegistry.buildAggregation(builder, name, r)
    }
  }

  /**
   * Merges a provided set of constraints with the initial constraints for the given category. Also ensures that the
   * provided constraints are valid based upon the category definitions.
   */
  def mergeAndValidateConstraints(categories:Map[String, CategoryDescriptor], constraints:Seq[ScanConstraint]) = {
    // Validate that the provided constraints are valid for the categories
    constraints.foreach(c => {
      val category:CategoryDescriptor = categories.get(c.getAttributeName) match {
        case None => throw new InvalidConstraintException(c.getAttributeName,  "No matching category")
        case Some(cat) => cat
      }

      category.validateConstraint(c)
    })

    // Merge with default constraints
    val coveredCategories = constraints.map(c => c.getAttributeName).toSet
    val defaultConstraints = initialConstraintsFor(
      categories.filter { case (name, _) => !coveredCategories.contains(name) })

    constraints ++ defaultConstraints
  }

  /**
   * Differences a set of existing categories against a set of updated categories, returning a list of category changes.
   * A category change is a mapping from category name to (before, after). before and after are both optional - a missing
   * before implies a new category, a missing after implies a removed category, both present indicates a category change.
   */
  def differenceCategories(existing: Map[String, CategoryDescriptor], updated: Map[String, CategoryDescriptor]):
      Seq[CategoryChange] = {
    val existingKeys = existing.keySet.toSet
    val updatedKeys = updated.keySet.toSet

    val removed:Seq[CategoryChange] = (existingKeys -- updatedKeys).
      map(k => CategoryChange(k, Some(existing(k)), None)).toSeq
    val added:Seq[CategoryChange] = (updatedKeys -- existingKeys).
      map(k => CategoryChange(k, None, Some(updated(k)))).toSeq
    val changed:Seq[CategoryChange] = (existingKeys intersect updatedKeys).
      filter(k => !existing(k).equals(updated(k))).
      map(k => CategoryChange(k, Some(existing(k)), Some(updated(k)))).toSeq

    removed ++ added ++ changed
  }
}

case class CategoryChange(name:String, before:Option[CategoryDescriptor], after:Option[CategoryDescriptor]) {
  def isRemoval = after.isEmpty
  def isAddition = before.isEmpty
  def isChange = before.isDefined && after.isDefined
}

