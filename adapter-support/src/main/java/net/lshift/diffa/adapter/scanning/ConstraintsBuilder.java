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
package net.lshift.diffa.adapter.scanning;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Helper for building constraints from a web request.
 */
public class ConstraintsBuilder {
  private final HttpRequestParameters req;
  private final List<ScanConstraint> result;

  public ConstraintsBuilder(HttpRequestParameters request) {
    this.req = request;
    this.result = new ArrayList<ScanConstraint>();
  }

  public ConstraintsBuilder(HttpServletRequest underlying) {
    this(new ServletRequestParametersWrapper(underlying));
  }

  /**
   * Transforms the builder into a list of constraints.
   * @return the constraint list.
   */
  public List<ScanConstraint> toList() {
    return result;
  }

  // TODO Potentially the underlying should be a set?
  public Set<ScanConstraint> toSet() {
    return new HashSet<ScanConstraint>(result);
  }

  /**
   * Attempt to add a date range constraint for the given attribute. The constraint will be added if
   * one or both of [attrName]-start, [attrName]-end are present in the request.
   * @param attrName the name of the attribute
   */
  public void maybeAddDateRangeConstraint(String attrName) {
    String startVal = req.getParameter(attrName + "-start");
    String endVal = req.getParameter(attrName + "-end");

    if (startVal != null || endVal != null) {
      result.add(new DateRangeConstraint(attrName, startVal, endVal));
    }
  }

  /**
   * Attempt to add a time range constraint for the given attribute. The constraint will be added if
   * one or both of [attrName]-start, [attrName]-end are present in the request.
   * @param attrName the name of the attribute
   */
  public void maybeAddTimeRangeConstraint(String attrName) {
    String startVal = req.getParameter(attrName + "-start");
    String endVal = req.getParameter(attrName + "-end");

    if (startVal != null || endVal != null) {
      result.add(new TimeRangeConstraint(attrName, startVal, endVal));
    }
  }

  /**
   * Attempt to add a set constraint for the given attribute. The constraint will be added if there are
   * any arguments in the request with the given attribute name.
   * @param attrName the name of the attribute
   */
  public void maybeAddSetConstraint(String attrName) {
    String[] values = req.getParameterValues(attrName);

    if (values != null && values.length > 0) {
      HashSet<String> set = new HashSet<String>();
      Collections.addAll(set,values);
      result.add(new SetConstraint(attrName,set));
    }
  }

  /**
   * Attempt to add a integer range constraint for the given attribute. The constraint will be added if
   * one or both of [attrName]-start, [attrName]-end are present in the request.
   * @param attrName the name of the attribute
   */
  public void maybeAddIntegerRangeConstraint(String attrName) {
    String startVal = req.getParameter(attrName + "-start");
    String endVal = req.getParameter(attrName + "-end");

    if (startVal != null || endVal != null) {
      result.add(new IntegerRangeConstraint(attrName, startVal, endVal));
    }
  }

  /**
   * Attempt to add a string prefix constraint for the given attribute. The constraint will be added if
   * [attrName]-prefix is present in the request.
   * @param attrName the name of the attribute
   */
  public void maybeAddStringPrefixConstraint(String attrName) {
    String prefix = req.getParameter(attrName + "-prefix");

    if (prefix != null) {
      result.add(new StringPrefixConstraint(attrName, prefix));
    }
  }
}
