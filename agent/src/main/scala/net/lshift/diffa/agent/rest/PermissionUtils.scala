package net.lshift.diffa.agent.rest

import javax.ws.rs.WebApplicationException
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.core.context.SecurityContextHolder
import net.lshift.diffa.agent.auth.{TargetObject, Privilege}

/**
 * Utilities for ensuring that users have appropriate permissions.
 */
object PermissionUtils {
  def ensurePrivilege(permissionEvaluator:PermissionEvaluator, privilege:Privilege, targetObj:TargetObject) {
    if (!hasPrivilege(permissionEvaluator, privilege, targetObj)) {
      throw new WebApplicationException(403)
    }
  }

  def hasPrivilege(permissionEvaluator:PermissionEvaluator, privilege:Privilege, targetObj:TargetObject) = {
    val authentication = SecurityContextHolder.getContext.getAuthentication
    permissionEvaluator.hasPermission(authentication, targetObj, privilege)
  }
}