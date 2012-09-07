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
package net.lshift.diffa.kernel.naming;

import net.lshift.diffa.schema.tables.Diffs;

/**
 * This a is canonical list of names used to identify caches.
 */
public enum CacheName {

  USER_DOMAIN_MEMBERS,

  DOMAIN_CONFIG_OPTIONS_MAP,
  DOMAIN_CONFIG_OPTIONS,
  DOMAIN_PAIR_BREAKERS,

  SPACE_PATHS,
  SPACE_IDS,

  DIFFS,

  EXTENT_PAIRS

}
