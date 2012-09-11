package net.lshift.diffa.agent.auth

import net.lshift.diffa.kernel.config.system.PolicyStatement

/**
 * Definitions of the various supported Diffa privileges.
 */
trait Privilege {
  /** The name of the privilege **/
  def name:String

  /** Determines whether the given statement provides access to the given target for this privilege */
  def isValidForTarget(space:Long, stmt:PolicyStatement, target:TargetObject):Boolean
}

case class UserPrivilege(val name:String) extends Privilege {
  // Target evaluation is handled specially for users
  def isValidForTarget(space:Long, stmt:PolicyStatement, target:TargetObject) = false
}

/**
 * A privilege that is applied against a space.
 */
class SpacePrivilege(val name:String) extends Privilege {
  def isValidForTarget(space:Long, stmt:PolicyStatement, target:TargetObject) = {
    target match {
      case st:SpaceTarget => st.space == space || st.parents.contains(space)
      case _              => false
    }
  }
}

/**
 * A privilege that is applied against a pair.
 */
class PairPrivilege(name:String) extends SpacePrivilege(name) {
  override def isValidForTarget(space:Long, stmt:PolicyStatement, target:TargetObject) = {
    super.isValidForTarget(space, stmt, target) && (target match {
      case pt:PairTarget => stmt.appliesTo("pair", pt.pair)
      case _             => true      // If the target wasn't a pair, then allow
    })
  }
}

/**
 * A privilege that is applied against an endpoint.
 */
class EndpointPrivilege(name:String) extends SpacePrivilege(name) {
  override def isValidForTarget(space:Long, stmt:PolicyStatement, target:TargetObject) = {
    super.isValidForTarget(space, stmt, target) && (target match {
      case et:EndpointTarget => stmt.appliesTo("endpoint", et.endpoint)
      case _                 => true      // If the target wasn't an endpoint, then allow
    })
  }
}

/**
 * A privilege that is applied against an action.
 */
class ActionPrivilege(name:String) extends PairPrivilege(name) {
  override def isValidForTarget(space:Long, stmt:PolicyStatement, target:TargetObject) = {
    super.isValidForTarget(space, stmt, target) && (target match {
      case at:ActionTarget => stmt.appliesTo("action", at.action)
      case _               => true      // If the target wasn't an action, then allow
    })
  }
}

/**
 * A privilege that is applied against a report.
 */
class ReportPrivilege(name:String) extends PairPrivilege(name) {
  override def isValidForTarget(space:Long, stmt:PolicyStatement, target:TargetObject) = {
    super.isValidForTarget(space, stmt, target) && (target match {
      case rt:ReportTarget => stmt.appliesTo("report", rt.report)
      case _               => true      // If the target wasn't a report, then allow
    })
  }
}

/** Catalogue of privileges supported by Diffa */
object Privileges {
  val SPACE_USER = new SpacePrivilege("space-user")
  val READ_DIFFS = new PairPrivilege("read-diffs")  // Ability to query for differences, access overviews, etc
  val CONFIGURE = new SpacePrivilege("configure")   // Ability to configure a space
  val INITIATE_SCAN = new PairPrivilege("initiate-scan")
  val CANCEL_SCAN = new PairPrivilege("cancel-scan")
  val POST_CHANGE_EVENT = new EndpointPrivilege("post-change-event")
  val POST_INVENTORY = new EndpointPrivilege("post-inventory")
  val SCAN_STATUS = new PairPrivilege("view-scan-status")
  val DIAGNOSTICS = new PairPrivilege("view-diagnostics")
  val INVOKE_ACTIONS = new ActionPrivilege("invoke-actions")
  val IGNORE_DIFFS = new PairPrivilege("ignore-diffs")
  val VIEW_EXPLANATIONS = new PairPrivilege("view-explanations")
  val EXECUTE_REPORT = new ReportPrivilege("execute-report")
  val VIEW_ACTIONS = new PairPrivilege("view-actions")
  val VIEW_REPORTS = new PairPrivilege("view-reports")
  val READ_EVENT_DETAILS = new PairPrivilege("read-event-details")

  val USER_PREFERENCES = new UserPrivilege("user-preferences")
}