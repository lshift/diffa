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

package net.lshift.diffa.kernel.util;

import net.lshift.diffa.snowflake.MachineIdAssigner;
import net.lshift.diffa.snowflake.RandomIdentityGenerator;
import net.lshift.diffa.snowflake.SnowflakeIdProvider;

/**
 * An IdProvider that uses ZooKeeper to co-ordinate node identifiers which are generated randomly.
 */
public class DiffaIdProvider extends SnowflakeIdProvider {
  public DiffaIdProvider(String zookeeperCluster) throws Exception {
    super(MachineIdAssigner.getInstance(zookeeperCluster).assign(RandomIdentityGenerator.unseeded()));
  }
}
