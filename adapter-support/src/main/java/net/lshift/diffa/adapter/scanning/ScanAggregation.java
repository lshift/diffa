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

/**
 * Describes an aggregation function being applied to a scan query.
 */
public interface ScanAggregation {
  /**
   * Retrieves the name of the attribute that is being aggregated on.
   * @return the name of the aggregated attribute.
   */
  String getAttributeName();

  String getParent();

  /**
   * Retrieves the name of the bucket that the given attribute value should be applied to.
   * @param attributeVal the attribute value
   * @return the name of the bucket that this attribute belongs to.
   */
  String bucket(String attributeVal);
}
