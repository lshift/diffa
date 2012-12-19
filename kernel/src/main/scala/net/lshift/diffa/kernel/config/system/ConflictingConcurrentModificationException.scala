/*
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

package net.lshift.diffa.kernel.config.system

/**
 * A configuration item change has been concurrently modified in a way that
 * conflicts with the requested change.  This should only be thrown if retrying
 * the action without change has a reasonable chance of succeeding.
 */
class ConflictingConcurrentModificationException(val modifiedObject: String)
  extends RuntimeException("A concurrent modification to the object %s invalidated the requested change.  Retry the operation.".format(modifiedObject))
