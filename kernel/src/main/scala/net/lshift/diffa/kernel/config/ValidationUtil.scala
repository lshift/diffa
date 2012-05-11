/**
 * Copyright (C) 2010-2011 LShift Ltd.
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

/**
 * Utility for helping with validation Diffa configs.
 */
object ValidationUtil {
  /**
   * Build a path out of a parent and child component pair. The parent may be null, in which case only the child
   * component will be used.
   */
  def buildPath(parent:String, child:String):String = if (parent == null) {
      child
    } else {
     parent + "/" + child
    }

  /**
   * Build a path out of a parent and child component pair. The child attributes are used to distinguish a given child
   * that may have multiple instances (for example, a pair). The parent may be null, in which case only the child
   * component will be used.
   */
  def buildPath(parent:String, child:String, childAttrs:Map[String, String]):String = {
    val childAttrStr = childAttrs.map { case (k, v) => k + "=" + v }.reduceLeft(_ + ", " + _)
    buildPath(parent, child + "[" + childAttrStr + "]")
  }

  /**
   * Validates a field that is required to be present and not empty.
   */
  def requiredAndNotEmpty(path:String, name:String, value:String) {
    if (value == null || value.isEmpty) {
      throw new ConfigValidationException(path, name + " cannot be null or empty")
    }
  }

  /**
   * Validates that the list of values contains only unique values. Raises an error for the first
   * non-unique field.
   */
  def ensureUniqueChildren(path:String, child:String, keyName:String, values:Seq[String]) {
    for (i <- 0 until (values.length-1)) {
      val current = values(i)

      if (values.slice(i+1, values.length).contains(current)) {
        val childPath = buildPath(path, child, Map(keyName -> current))

        throw new ConfigValidationException(childPath, "'" + current + "' is not a unique " + keyName)
      }
    }
  }

  /**
   * Verifies that the given value is a member of a permissible set.
   */
  def ensureMembership(path:String, name:String, value:String, group:Set[String]) = {
    if (value == null || !group.contains(value)) {
      throw new ConfigValidationException(path,
        "%s is invalid. %s is not a member of the set %s".format(name, value, group))
    }
  }

  /**
   * Validates that a given field does not exceed a length limit.
   */
  def ensureLengthLimit(path:String, name:String, value:String, limit:Int) {
    if (value != null && value.length() > limit) {
      throw new ConfigValidationException(path,
        "%s is too long. Limit is %s, value %s is %s".format(name, limit, value, value.length))
    }
  }

  /**
   * Checks a settings URL to confirm it is in the correct format.
   *
   * If the URL is not null and invalid, throws ConfigValidationException, otherwise returns true.
   */
  def ensureSettingsURLFormat(path: String,  url: String): Boolean = {
    if (url == null) { return true } // nullable

    if (!url.matches("(amqp|https?)://.*")) {
      throw new ConfigValidationException(path, "incorrect settings URL format: %s".format(url))
    }

    return true
  }

  /**
   * Turns an empty string into a null string. This prevents issues whereby empty strings provided by the web
   * interface look like incorrect values instead of missing ones.
   */
  def maybeNullify(s:String) = if (s == null || s.isEmpty) null else s

  /**
   * Turns an empty or null string into a default value.
   */
  def maybeDefault(s:String, default:String) = if (s == null || s.isEmpty) default else s

  /**
   * Turns an empty boolean into a default value.
   */
  def maybeDefault(b:java.lang.Boolean, default:Boolean):java.lang.Boolean = if (b == null) default else b
}