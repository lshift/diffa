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

package net.lshift.diffa.config;

/**
 * A IntervalRefinement has two responsibilities:
 * 1. Determine whether one time interval is a refinement of another;
 * 2. Apply a refinement of one time interval to another.
 */
public interface IntervalRefinement {
  public boolean isRefinementOf(String start, String end);
  public TimeInterval refineInterval(String start, String end);
}
