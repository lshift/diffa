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
package net.lshift.diffa.kernel.config

import org.jooq.impl.Factory
import net.lshift.diffa.schema.tables.UniqueCategoryNames.UNIQUE_CATEGORY_NAMES
import net.lshift.diffa.schema.tables.UniqueCategoryViewNames.UNIQUE_CATEGORY_VIEW_NAMES
import net.lshift.diffa.schema.tables.PrefixCategories.PREFIX_CATEGORIES
import net.lshift.diffa.schema.tables.PrefixCategoryViews.PREFIX_CATEGORY_VIEWS
import net.lshift.diffa.schema.tables.SetCategories.SET_CATEGORIES
import net.lshift.diffa.schema.tables.SetCategoryViews.SET_CATEGORY_VIEWS
import net.lshift.diffa.schema.tables.RangeCategories.RANGE_CATEGORIES
import net.lshift.diffa.schema.tables.RangeCategoryViews.RANGE_CATEGORY_VIEWS
import scala.collection.JavaConversions._
import org.jooq.{Record, Result}
import net.lshift.diffa.schema.tables.Escalations.ESCALATIONS
import net.lshift.diffa.schema.tables.PairReports.PAIR_REPORTS
import net.lshift.diffa.schema.tables.RepairActions.REPAIR_ACTIONS
import net.lshift.diffa.schema.tables.Pair.PAIR
import net.lshift.diffa.kernel.util.MissingObjectException
import net.lshift.diffa.schema.tables.UserItemVisibility.USER_ITEM_VISIBILITY
import net.lshift.diffa.schema.tables.PairViews.PAIR_VIEWS
import net.lshift.diffa.schema.tables.StoreCheckpoints.STORE_CHECKPOINTS
import net.lshift.diffa.kernel.frontend._
import net.lshift.diffa.schema.jooq.DatabaseFacade
import net.lshift.diffa.schema.tables.Endpoint._
import net.lshift.diffa.schema.tables.EndpointViews._
import net.lshift.diffa.kernel.frontend.DomainEndpointDef
import net.lshift.diffa.kernel.frontend.RepairActionDef
import net.lshift.diffa.kernel.frontend.EscalationDef
import net.lshift.diffa.kernel.frontend.PairReportDef
import collection.mutable
import org.slf4j.LoggerFactory
import org.jooq.exception.DataAccessException
import java.sql.SQLIntegrityConstraintViolationException
import net.lshift.diffa.kernel.util.AlertCodes._

/**
 * This object is a workaround for the fact that Scala is so slow
 */
object JooqConfigStoreCompanion {

  val log = LoggerFactory.getLogger(getClass)

  /**
   * A UNIQUE_CATEGORY_NAME can either refer to an endpoint or an endpoint view.
   * These two enums signify those two legal values.
   */
  val ENDPOINT_TARGET_TYPE = "endpoint"
  val ENDPOINT_VIEW_TARGET_TYPE = "endpoint_view"

  /**
   * Common name for the name of the view across both halves of the union query to list endpoints.
   * In the top half of the union, this column will be null, since that half only deals with endpoints.
   * In the bottom half of the union, this column will contain the name of the endpoint view.
   */
  val VIEW_NAME_COLUMN = UNIQUE_CATEGORY_VIEW_NAMES.VIEW_NAME.getName

  /**
   * Due to the fact that we need to order the grand union rather than just the individual subselects,
   * we need to select from the grand union. When doing so, the column names called NAME will clash,
   * so we alias the UNIQUE_CATEGORY_NAMES.NAME and UNIQUE_CATEGORY_VIEW_NAMES.NAME fields to something other than NAME.
   */
  val UNIQUE_CATEGORY_ALIAS = "unique_category_alias"

  def listEndpoints(jooq:DatabaseFacade, domain:Option[String] = None, endpoint:Option[String] = None) : java.util.List[DomainEndpointDef] = {
    jooq.execute(t => {
      val topHalf =     t.select(UNIQUE_CATEGORY_NAMES.NAME.as(UNIQUE_CATEGORY_ALIAS)).
        select(ENDPOINT.getFields).
        select(Factory.field("null").as(VIEW_NAME_COLUMN)).
        select(RANGE_CATEGORIES.DATA_TYPE, RANGE_CATEGORIES.LOWER_BOUND, RANGE_CATEGORIES.UPPER_BOUND, RANGE_CATEGORIES.MAX_GRANULARITY).
        select(PREFIX_CATEGORIES.STEP, PREFIX_CATEGORIES.PREFIX_LENGTH, PREFIX_CATEGORIES.MAX_LENGTH).
        select(SET_CATEGORIES.VALUE).
        from(ENDPOINT).

        leftOuterJoin(UNIQUE_CATEGORY_NAMES).
          on(UNIQUE_CATEGORY_NAMES.DOMAIN.equal(ENDPOINT.DOMAIN)).
          and(UNIQUE_CATEGORY_NAMES.ENDPOINT.equal(ENDPOINT.NAME)).

        leftOuterJoin(RANGE_CATEGORIES).
          on(RANGE_CATEGORIES.DOMAIN.equal(ENDPOINT.DOMAIN)).
          and(RANGE_CATEGORIES.ENDPOINT.equal(ENDPOINT.NAME)).
          and(RANGE_CATEGORIES.NAME.equal(UNIQUE_CATEGORY_NAMES.NAME)).

        leftOuterJoin(PREFIX_CATEGORIES).
          on(PREFIX_CATEGORIES.DOMAIN.equal(ENDPOINT.DOMAIN)).
          and(PREFIX_CATEGORIES.ENDPOINT.equal(ENDPOINT.NAME)).
          and(PREFIX_CATEGORIES.NAME.equal(UNIQUE_CATEGORY_NAMES.NAME)).

        leftOuterJoin(SET_CATEGORIES).
          on(SET_CATEGORIES.DOMAIN.equal(ENDPOINT.DOMAIN)).
          and(SET_CATEGORIES.ENDPOINT.equal(ENDPOINT.NAME)).
          and(SET_CATEGORIES.NAME.equal(UNIQUE_CATEGORY_NAMES.NAME))

      val firstUnionPart = domain match {
        case None    => topHalf
        case Some(d) =>
          val maybeUnionPart = topHalf.where(ENDPOINT.DOMAIN.equal(d))
          endpoint match {
            case None    => maybeUnionPart
            case Some(e) => maybeUnionPart.and(ENDPOINT.NAME.equal(e))
          }
      }

      val bottomHalf =  t.select(UNIQUE_CATEGORY_VIEW_NAMES.NAME.as(UNIQUE_CATEGORY_ALIAS)).
        select(ENDPOINT.getFields).
        select(ENDPOINT_VIEWS.NAME.as(VIEW_NAME_COLUMN)).
        select(RANGE_CATEGORY_VIEWS.DATA_TYPE, RANGE_CATEGORY_VIEWS.LOWER_BOUND, RANGE_CATEGORY_VIEWS.UPPER_BOUND, RANGE_CATEGORY_VIEWS.MAX_GRANULARITY).
        select(PREFIX_CATEGORY_VIEWS.STEP, PREFIX_CATEGORY_VIEWS.PREFIX_LENGTH, PREFIX_CATEGORY_VIEWS.MAX_LENGTH).
        select(SET_CATEGORY_VIEWS.VALUE).
        from(ENDPOINT_VIEWS).

        join(ENDPOINT).
          on(ENDPOINT.DOMAIN.equal(ENDPOINT_VIEWS.DOMAIN)).
          and(ENDPOINT.NAME.equal(ENDPOINT_VIEWS.ENDPOINT)).

        leftOuterJoin(UNIQUE_CATEGORY_VIEW_NAMES).
          on(UNIQUE_CATEGORY_VIEW_NAMES.DOMAIN.equal(ENDPOINT_VIEWS.DOMAIN)).
          and(UNIQUE_CATEGORY_VIEW_NAMES.ENDPOINT.equal(ENDPOINT_VIEWS.ENDPOINT)).
          and(UNIQUE_CATEGORY_VIEW_NAMES.VIEW_NAME.equal(ENDPOINT_VIEWS.NAME)).

        leftOuterJoin(RANGE_CATEGORY_VIEWS).
          on(RANGE_CATEGORY_VIEWS.DOMAIN.equal(ENDPOINT_VIEWS.DOMAIN)).
          and(RANGE_CATEGORY_VIEWS.ENDPOINT.equal(ENDPOINT_VIEWS.ENDPOINT)).
          and(RANGE_CATEGORY_VIEWS.NAME.equal(UNIQUE_CATEGORY_VIEW_NAMES.NAME)).

        leftOuterJoin(PREFIX_CATEGORY_VIEWS).
          on(PREFIX_CATEGORY_VIEWS.DOMAIN.equal(ENDPOINT_VIEWS.DOMAIN)).
          and(PREFIX_CATEGORY_VIEWS.ENDPOINT.equal(ENDPOINT_VIEWS.ENDPOINT)).
          and(PREFIX_CATEGORY_VIEWS.NAME.equal(UNIQUE_CATEGORY_VIEW_NAMES.NAME)).

        leftOuterJoin(SET_CATEGORY_VIEWS).
          on(SET_CATEGORY_VIEWS.DOMAIN.equal(ENDPOINT_VIEWS.DOMAIN)).
          and(SET_CATEGORY_VIEWS.ENDPOINT.equal(ENDPOINT_VIEWS.ENDPOINT)).
          and(SET_CATEGORY_VIEWS.NAME.equal(UNIQUE_CATEGORY_VIEW_NAMES.NAME))

      val secondUnionPart = domain match {
        case None    => bottomHalf
        case Some(d) =>
          val maybeUnionPart = bottomHalf.where(ENDPOINT.DOMAIN.equal(d))
          endpoint match {
            case None    => maybeUnionPart
            case Some(e) => maybeUnionPart.and(ENDPOINT_VIEWS.ENDPOINT.equal(e))
          }
      }

      // Sort the grand union rather than the individual constituent subselects

      val grandUnion = firstUnionPart.union(secondUnionPart)

      val results = t.select(grandUnion.getFields).
                      from(grandUnion).
                      orderBy(
                        grandUnion.getField(ENDPOINT.DOMAIN),
                        grandUnion.getField(ENDPOINT.NAME),
                        Factory.field(UNIQUE_CATEGORY_ALIAS)
                      ).
                      fetch()

      val endpoints = new java.util.TreeMap[String,DomainEndpointDef]()

      results.iterator().foreach(record => {

        val currentEndpoint = DomainEndpointDef(
          name = record.getValue(ENDPOINT.NAME),
          scanUrl = record.getValue(ENDPOINT.SCAN_URL),
          contentRetrievalUrl = record.getValue(ENDPOINT.CONTENT_RETRIEVAL_URL),
          versionGenerationUrl = record.getValue(ENDPOINT.VERSION_GENERATION_URL),
          inboundUrl = record.getValue(ENDPOINT.INBOUND_URL),
          collation = record.getValue(ENDPOINT.COLLATION_TYPE)
        )

        val compressionKey = currentEndpoint.domain + "/" + currentEndpoint.name

        if (!endpoints.contains(compressionKey)) {
          endpoints.put(compressionKey, currentEndpoint);
        }

        val resolvedEndpoint = endpoints.get(compressionKey)

        // Check to see whether this row is for an endpoint view

        val viewName = record.getValueAsString(VIEW_NAME_COLUMN)
        val currentView = if (viewName != null) {
          resolvedEndpoint.views.find(v => v.name == viewName) match {
            case None =>
              // This view has not yet been attached to the endpoint, so attach it now
              val viewToAttach = EndpointViewDef(name = viewName)
              resolvedEndpoint.views.add(viewToAttach)
              Some(viewToAttach)
            case x    => x
          }
        }
        else {
          None
        }

        val categoryName = record.getValueAsString(UNIQUE_CATEGORY_ALIAS)

        def applyCategoryToEndpointOrView(descriptor:CategoryDescriptor) = {
          currentView match {
            case None    => resolvedEndpoint.categories.put(categoryName, descriptor)
            case Some(v) => v.categories.put(categoryName, descriptor)
          }
        }

        def applySetMemberToDescriptorMapForCurrentCategory(value:String, descriptors:java.util.Map[String,CategoryDescriptor]) = {
          var descriptor = descriptors.get(categoryName)
          if (descriptor == null) {
            val setDescriptor = new SetCategoryDescriptor()
            setDescriptor.addValue(value)
            descriptors.put(categoryName, setDescriptor)
          }
          else {
            descriptor.asInstanceOf[SetCategoryDescriptor].addValue(value)
          }
        }

        if (record.getValue(RANGE_CATEGORIES.DATA_TYPE) != null) {
          val dataType = record.getValue(RANGE_CATEGORIES.DATA_TYPE)
          val lowerBound = record.getValue(RANGE_CATEGORIES.LOWER_BOUND)
          val upperBound = record.getValue(RANGE_CATEGORIES.UPPER_BOUND)
          val maxGranularity = record.getValue(RANGE_CATEGORIES.MAX_GRANULARITY)
          val descriptor = new RangeCategoryDescriptor(dataType, lowerBound, upperBound, maxGranularity)
          applyCategoryToEndpointOrView(descriptor)

        }
        else if (record.getValue(PREFIX_CATEGORIES.PREFIX_LENGTH) != null) {
          val prefixLength = record.getValue(PREFIX_CATEGORIES.PREFIX_LENGTH)
          val maxLength = record.getValue(PREFIX_CATEGORIES.MAX_LENGTH)
          val step = record.getValue(PREFIX_CATEGORIES.STEP)
          val descriptor = new PrefixCategoryDescriptor(prefixLength, maxLength, step)
          applyCategoryToEndpointOrView(descriptor)
        }
        else if (record.getValue(SET_CATEGORIES.VALUE) != null) {

          // Set values are a little trickier, since the values for one descriptor are split up over multiple rows

          val setCategoryValue = record.getValue(SET_CATEGORIES.VALUE)
          currentView match {
            case None    =>
              applySetMemberToDescriptorMapForCurrentCategory(setCategoryValue, resolvedEndpoint.categories)
            case Some(v) =>
              applySetMemberToDescriptorMapForCurrentCategory(setCategoryValue, v.categories)
          }
        }

      })

      new java.util.ArrayList[DomainEndpointDef](endpoints.values())
    })
  }


  def listPairs(jooq:DatabaseFacade, domain:String, endpoint:Option[String] = None) : Seq[DomainPairDef] = jooq.execute(t => {

    val baseQuery = t.select(PAIR.getFields).
      select(PAIR_VIEWS.NAME, PAIR_VIEWS.SCAN_CRON_SPEC, PAIR_VIEWS.SCAN_CRON_ENABLED).
      from(PAIR).
      leftOuterJoin(PAIR_VIEWS).
      on(PAIR_VIEWS.PAIR.equal(PAIR.PAIR_KEY)).
      and(PAIR_VIEWS.DOMAIN.equal(PAIR.DOMAIN)).
      where(PAIR.DOMAIN.equal(domain))

    val query = endpoint match {
      case None       => baseQuery
      case Some(name) => baseQuery.and(PAIR.UPSTREAM.equal(name).or(PAIR.DOWNSTREAM.equal(name)))
    }

    val results = query.fetch()

    val compressed = new mutable.HashMap[String, DomainPairDef]()

    def compressionKey(pairKey:String) = domain + "/" + pairKey

    results.iterator().map(record => {
      val pairKey = record.getValue(PAIR.PAIR_KEY)
      val compressedKey = compressionKey(pairKey)
      val pair = compressed.getOrElseUpdate(compressedKey,
        DomainPairDef(
          domain = record.getValue(PAIR.DOMAIN),
          key = record.getValue(PAIR.PAIR_KEY),
          upstreamName = record.getValue(PAIR.UPSTREAM),
          downstreamName = record.getValue(PAIR.DOWNSTREAM),
          versionPolicyName = record.getValue(PAIR.VERSION_POLICY_NAME),
          scanCronSpec = record.getValue(PAIR.SCAN_CRON_SPEC),
          scanCronEnabled = record.getValue(PAIR.SCAN_CRON_ENABLED),
          matchingTimeout = record.getValue(PAIR.MATCHING_TIMEOUT),
          allowManualScans = record.getValue(PAIR.ALLOW_MANUAL_SCANS),
          views = new java.util.ArrayList[PairViewDef]()
        )
      )

      val viewName = record.getValue(PAIR_VIEWS.NAME)

      if (viewName != null) {
        pair.views.add(PairViewDef(
          name = viewName,
          scanCronSpec = record.getValue(PAIR_VIEWS.SCAN_CRON_SPEC),
          scanCronEnabled = record.getValue(PAIR_VIEWS.SCAN_CRON_ENABLED)
        ))
      }

      pair

    }).toList
  })

  def mapResultsToList[T](results:Result[Record], rowMapper:Record => T) = {
    val escalations = new java.util.ArrayList[T]()
    results.iterator().foreach(r => escalations.add(rowMapper(r)))
    escalations
  }

  def recordToEscalation(record:Record) : EscalationDef = {
    EscalationDef(
      pair = record.getValue(ESCALATIONS.PAIR_KEY),
      name = record.getValue(ESCALATIONS.NAME),
      action = record.getValue(ESCALATIONS.ACTION),
      actionType = record.getValue(ESCALATIONS.ACTION_TYPE),
      event = record.getValue(ESCALATIONS.EVENT),
      origin = record.getValue(ESCALATIONS.ORIGIN))
  }

  def recordToPairReport(record:Record) : PairReportDef = {
    PairReportDef(
      pair = record.getValue(PAIR_REPORTS.PAIR_KEY),
      name = record.getValue(PAIR_REPORTS.NAME),
      target = record.getValue(PAIR_REPORTS.TARGET),
      reportType = record.getValue(PAIR_REPORTS.REPORT_TYPE)
    )
  }

  def recordToRepairAction(record:Record) : RepairActionDef = {
    RepairActionDef(
      pair = record.getValue(REPAIR_ACTIONS.PAIR_KEY),
      name = record.getValue(REPAIR_ACTIONS.NAME),
      scope = record.getValue(REPAIR_ACTIONS.SCOPE),
      url = record.getValue(REPAIR_ACTIONS.URL)
    )
  }

  def deletePairWithDependencies(t:Factory, pair:DiffaPairRef) = {
    deleteRepairActionsByPair(t, pair)
    deleteEscalationsByPair(t, pair)
    deleteReportsByPair(t, pair)
    deletePairViewsByPair(t, pair)
    deleteStoreCheckpointsByPair(t, pair)
    deleteUserItemsByPair(t, pair)
    deletePairWithoutDependencies(t, pair)
  }

  private def deletePairWithoutDependencies(t:Factory, pair:DiffaPairRef) = {
    val deleted = t.delete(PAIR).
      where(PAIR.DOMAIN.equal(pair.domain)).
      and(PAIR.PAIR_KEY.equal(pair.key)).
      execute()

    if (deleted == 0) {
      throw new MissingObjectException(pair.identifier)
    }
  }

  def deleteUserItemsByPair(t:Factory, pair:DiffaPairRef) = {
    t.delete(USER_ITEM_VISIBILITY).
      where(USER_ITEM_VISIBILITY.DOMAIN.equal(pair.domain)).
      and(USER_ITEM_VISIBILITY.PAIR.equal(pair.key)).
      execute()
  }

  def deleteRepairActionsByPair(t:Factory, pair:DiffaPairRef) = {
    t.delete(REPAIR_ACTIONS).
      where(REPAIR_ACTIONS.DOMAIN.equal(pair.domain)).
      and(REPAIR_ACTIONS.PAIR_KEY.equal(pair.key)).
      execute()
  }

  def deleteEscalationsByPair(t:Factory, pair:DiffaPairRef) = {
    t.delete(ESCALATIONS).
      where(ESCALATIONS.DOMAIN.equal(pair.domain)).
      and(ESCALATIONS.PAIR_KEY.equal(pair.key)).
      execute()
  }

  def deleteReportsByPair(t:Factory, pair:DiffaPairRef) = {
    t.delete(PAIR_REPORTS).
      where(PAIR_REPORTS.DOMAIN.equal(pair.domain)).
      and(PAIR_REPORTS.PAIR_KEY.equal(pair.key)).
      execute()
  }

  def deletePairViewsByPair(t:Factory, pair:DiffaPairRef) = {
    t.delete(PAIR_VIEWS).
      where(PAIR_VIEWS.DOMAIN.equal(pair.domain)).
      and(PAIR_VIEWS.PAIR.equal(pair.key)).
      execute()
  }

  def deleteStoreCheckpointsByPair(t:Factory, pair:DiffaPairRef) = {
    t.delete(STORE_CHECKPOINTS).
      where(STORE_CHECKPOINTS.DOMAIN.equal(pair.domain)).
      and(STORE_CHECKPOINTS.PAIR.equal(pair.key)).
      execute()
  }

  def insertCategories(t:Factory,
                       domain:String,
                       endpoint:EndpointDef) = {

    endpoint.categories.foreach { case (categoryName, descriptor) => {

      try {

        t.insertInto(UNIQUE_CATEGORY_NAMES).
            set(UNIQUE_CATEGORY_NAMES.DOMAIN, domain).
            set(UNIQUE_CATEGORY_NAMES.ENDPOINT, endpoint.name).
            set(UNIQUE_CATEGORY_NAMES.NAME, categoryName).
          execute()

        descriptor match {
          case r:RangeCategoryDescriptor  => insertRangeCategory(t, domain, endpoint.name, categoryName, r)
          case s:SetCategoryDescriptor    => insertSetCategory(t, domain, endpoint.name, categoryName, s)
          case p:PrefixCategoryDescriptor => insertPrefixCategory(t, domain, endpoint.name, categoryName, p)
        }
      }
      catch {
          case e:DataAccessException if e.getCause.isInstanceOf[SQLIntegrityConstraintViolationException] =>
            val msg = "Integrity constaint during insert into UNIQUE_CATEGORY_NAMES: domain = %s; endpoint = %s; categories = %s".
                      format(domain, endpoint, endpoint.categories)
            log.warn("%s %s".format(formatAlertCode(domain, INTEGRITY_CONSTRAINT_VIOLATED), msg))
            log.warn("%s %s".format(formatAlertCode(domain, INTEGRITY_CONSTRAINT_VIOLATED), e.getMessage))
            throw e
          case x =>
            log.error("%s Error inserting categories".format(formatAlertCode(domain, DB_EXECUTION_ERROR)), x)
            throw x
      }
    }}
  }

  def insertCategoriesForView(t:Factory,
                              domain:String,
                              endpoint:String,
                              view:EndpointViewDef) = {

    view.categories.foreach { case (categoryName, descriptor) => {

      try {

        t.insertInto(UNIQUE_CATEGORY_VIEW_NAMES).
            set(UNIQUE_CATEGORY_VIEW_NAMES.DOMAIN, domain).
            set(UNIQUE_CATEGORY_VIEW_NAMES.ENDPOINT, endpoint).
            set(UNIQUE_CATEGORY_VIEW_NAMES.VIEW_NAME, view.name).
            set(UNIQUE_CATEGORY_VIEW_NAMES.NAME, categoryName).
          execute()

        descriptor match {
          case r:RangeCategoryDescriptor  => insertRangeCategoryView(t, domain, endpoint, view.name, categoryName, r)
          case s:SetCategoryDescriptor    => insertSetCategoryView(t, domain, endpoint, view.name, categoryName, s)
          case p:PrefixCategoryDescriptor => insertPrefixCategoryView(t, domain, endpoint, view.name, categoryName, p)
        }
      }
      catch {
        case e:DataAccessException if e.getCause.isInstanceOf[SQLIntegrityConstraintViolationException] =>
          val msg = "Integrity constaint during insert into UNIQUE_CATEGORY_VIEW_NAMES: domain = %s; endpoint = %s; view = %s".
            format(domain, endpoint, view)
          log.warn("%s %s".format(formatAlertCode(domain, INTEGRITY_CONSTRAINT_VIOLATED), msg))
          log.warn("%s %s".format(formatAlertCode(domain, INTEGRITY_CONSTRAINT_VIOLATED), e.getMessage))
          throw e
        case x =>
          log.error("%s Error inserting view categories".format(formatAlertCode(domain, DB_EXECUTION_ERROR)), x)
          throw x
      }
    }}
  }

  def insertPrefixCategory(t:Factory,
                           domain:String,
                           endpoint:String,
                           categoryName:String,
                           descriptor:PrefixCategoryDescriptor) = {

    t.insertInto(PREFIX_CATEGORIES).
        set(PREFIX_CATEGORIES.DOMAIN, domain).
        set(PREFIX_CATEGORIES.ENDPOINT, endpoint).
        set(PREFIX_CATEGORIES.NAME, categoryName).
        set(PREFIX_CATEGORIES.STEP, Integer.valueOf(descriptor.step)).
        set(PREFIX_CATEGORIES.MAX_LENGTH, Integer.valueOf(descriptor.maxLength)).
        set(PREFIX_CATEGORIES.PREFIX_LENGTH, Integer.valueOf(descriptor.prefixLength)).
      execute()
  }

  def insertPrefixCategoryView(t:Factory,
                               domain:String,
                               endpoint:String,
                               view:String,
                               categoryName:String,
                               descriptor:PrefixCategoryDescriptor) = {

    t.insertInto(PREFIX_CATEGORY_VIEWS).
      set(PREFIX_CATEGORY_VIEWS.DOMAIN, domain).
      set(PREFIX_CATEGORY_VIEWS.ENDPOINT, endpoint).
      set(PREFIX_CATEGORY_VIEWS.VIEW_NAME, view).
      set(PREFIX_CATEGORY_VIEWS.NAME, categoryName).
      set(PREFIX_CATEGORY_VIEWS.STEP, Integer.valueOf(descriptor.step)).
      set(PREFIX_CATEGORY_VIEWS.MAX_LENGTH, Integer.valueOf(descriptor.maxLength)).
      set(PREFIX_CATEGORY_VIEWS.PREFIX_LENGTH, Integer.valueOf(descriptor.prefixLength)).
      execute()
  }

  def insertSetCategory(t:Factory,
                        domain:String,
                        endpoint:String,
                        categoryName:String,
                        descriptor:SetCategoryDescriptor) = {

    // TODO Is there a way to re-use the insert statement with a bind parameter?

    descriptor.values.foreach(value => {
      t.insertInto(SET_CATEGORIES).
        set(SET_CATEGORIES.DOMAIN, domain).
        set(SET_CATEGORIES.ENDPOINT, endpoint).
        set(SET_CATEGORIES.NAME, categoryName).
        set(SET_CATEGORIES.VALUE, value).
      execute()
    })
  }

  def insertSetCategoryView(t:Factory,
                            domain:String,
                            endpoint:String,
                            view:String,
                            categoryName:String,
                            descriptor:SetCategoryDescriptor) = {

    // TODO Is there a way to re-use the insert statement with a bind parameter?

    descriptor.values.foreach(value => {
      t.insertInto(SET_CATEGORY_VIEWS).
        set(SET_CATEGORY_VIEWS.DOMAIN, domain).
        set(SET_CATEGORY_VIEWS.ENDPOINT, endpoint).
        set(SET_CATEGORY_VIEWS.VIEW_NAME, view).
        set(SET_CATEGORY_VIEWS.NAME, categoryName).
        set(SET_CATEGORY_VIEWS.VALUE, value).
        execute()
    })
  }

  def insertRangeCategory(t:Factory,
                          domain:String,
                          endpoint:String,
                          categoryName:String,
                          descriptor:RangeCategoryDescriptor) = {
    t.insertInto(RANGE_CATEGORIES).
        set(RANGE_CATEGORIES.DOMAIN, domain).
        set(RANGE_CATEGORIES.ENDPOINT, endpoint).
        set(RANGE_CATEGORIES.NAME, categoryName).
        set(RANGE_CATEGORIES.DATA_TYPE, descriptor.dataType).
        set(RANGE_CATEGORIES.LOWER_BOUND, descriptor.lower).
        set(RANGE_CATEGORIES.UPPER_BOUND, descriptor.upper).
        set(RANGE_CATEGORIES.MAX_GRANULARITY, descriptor.maxGranularity).
      execute()
  }

  def insertRangeCategoryView(t:Factory,
                              domain:String,
                              endpoint:String,
                              view:String,
                              categoryName:String,
                              descriptor:RangeCategoryDescriptor) = {
    t.insertInto(RANGE_CATEGORY_VIEWS).
        set(RANGE_CATEGORY_VIEWS.DOMAIN, domain).
        set(RANGE_CATEGORY_VIEWS.ENDPOINT, endpoint).
        set(RANGE_CATEGORY_VIEWS.VIEW_NAME, view).
        set(RANGE_CATEGORY_VIEWS.NAME, categoryName).
        set(RANGE_CATEGORY_VIEWS.DATA_TYPE, descriptor.dataType).
        set(RANGE_CATEGORY_VIEWS.LOWER_BOUND, descriptor.lower).
        set(RANGE_CATEGORY_VIEWS.UPPER_BOUND, descriptor.upper).
        set(RANGE_CATEGORY_VIEWS.MAX_GRANULARITY, descriptor.maxGranularity).
      execute()
  }

  def deleteRangeCategories(t:Factory, domain:String, endpoint:String) = {
    t.delete(RANGE_CATEGORIES).
      where(RANGE_CATEGORIES.DOMAIN.equal(domain)).
        and(RANGE_CATEGORIES.ENDPOINT.equal(endpoint)).
      execute()
  }

  def deleteRangeCategoryViews(t:Factory, domain:String, endpoint:String) = {
    t.delete(RANGE_CATEGORY_VIEWS).
      where(RANGE_CATEGORY_VIEWS.DOMAIN.equal(domain)).
        and(RANGE_CATEGORY_VIEWS.ENDPOINT.equal(endpoint)).
      execute()
  }

  def deleteSetCategories(t:Factory, domain:String, endpoint:String) = {
    t.delete(SET_CATEGORIES).
      where(SET_CATEGORIES.DOMAIN.equal(domain)).
        and(SET_CATEGORIES.ENDPOINT.equal(endpoint)).
      execute()
  }

  def deleteSetCategoryViews(t:Factory, domain:String, endpoint:String) = {
    t.delete(SET_CATEGORY_VIEWS).
      where(SET_CATEGORY_VIEWS.DOMAIN.equal(domain)).
        and(SET_CATEGORY_VIEWS.ENDPOINT.equal(endpoint)).
      execute()
  }

  def deletePrefixCategories(t:Factory, domain:String, endpoint:String) = {
    t.delete(PREFIX_CATEGORIES).
      where(PREFIX_CATEGORIES.DOMAIN.equal(domain)).
        and(PREFIX_CATEGORIES.ENDPOINT.equal(endpoint)).
      execute()
  }

  def deletePrefixCategoryViews(t:Factory, domain:String, endpoint:String) = {
    t.delete(PREFIX_CATEGORY_VIEWS).
      where(PREFIX_CATEGORY_VIEWS.DOMAIN.equal(domain)).
        and(PREFIX_CATEGORY_VIEWS.ENDPOINT.equal(endpoint)).
      execute()
  }

  def deleteCategories(t:Factory, domain:String, endpoint:String) = {
    deletePrefixCategories(t, domain, endpoint)
    deletePrefixCategoryViews(t, domain, endpoint)

    deleteSetCategories(t, domain, endpoint)
    deleteSetCategoryViews(t, domain, endpoint)

    deleteRangeCategories(t, domain, endpoint)
    deleteRangeCategoryViews(t, domain, endpoint)

    t.delete(UNIQUE_CATEGORY_NAMES).
      where(UNIQUE_CATEGORY_NAMES.DOMAIN.equal(domain)).
        and(UNIQUE_CATEGORY_NAMES.ENDPOINT.equal(endpoint)).
      execute()

    t.delete(UNIQUE_CATEGORY_VIEW_NAMES).
      where(UNIQUE_CATEGORY_VIEW_NAMES.DOMAIN.equal(domain)).
        and(UNIQUE_CATEGORY_VIEW_NAMES.ENDPOINT.equal(endpoint)).
      execute()
  }

}
