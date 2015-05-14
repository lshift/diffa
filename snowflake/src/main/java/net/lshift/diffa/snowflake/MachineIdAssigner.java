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

package net.lshift.diffa.snowflake;

import java.util.concurrent.TimeUnit;

import com.netflix.curator.RetryPolicy;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.recipes.locks.InterProcessLock;
import com.netflix.curator.framework.recipes.locks.InterProcessMutex;
import com.netflix.curator.retry.RetryUntilElapsed;

/**
 * Assign to a client an identifier that is unique amongst all clients using the
 * same Zookeeper cluster. The following guarantees are provided:<br/>
 * 1. no two clients can be assigned the same identifier at the same time.</br>
 * 2. the exclusive assignment of an identifier to a client is never cancelled
 * during the life of the client process.
 */
public class MachineIdAssigner {
	private static final String DEFAULT_CLUSTER = "localhost:2181";
	private static final int connectionTimeoutMs = 2000;
	private static final int maxElapsedTimeMs = 3000;
	private static final int sleepMsBetweenRetries = 100;
	private static final RetryPolicy retryPolicy = new RetryUntilElapsed(maxElapsedTimeMs, sleepMsBetweenRetries);
	private static final long acquireTimeout = 50L;
	private static final TimeUnit acquireTimeoutTimeUnit = TimeUnit.MILLISECONDS;
	private static final int COLLISION_MAX_RETRIES = 30;
	private static final int DEFAULT_SESSION_TIMEOUT = 60000; // 1 minute

	private static final int ERR_IDENTIFIER_IN_USE = -1;
	private static final int ERR_ZK_FAILURE = -2;

	public static MachineIdAssigner localDefault() throws Exception {
		return getInstance(DEFAULT_CLUSTER);
	}

	public static MachineIdAssigner getInstance(String cluster) throws Exception {
		return withSessionTimeout(cluster, DEFAULT_SESSION_TIMEOUT);
	}

	/**
	 * Create a MachineIdAssigner that will use the specified Zookeeper cluster
	 * and will allow the release of identifier assignments after the specified
	 * timeout, measured from the time the calling process terminates.
	 * 
	 * @param cluster
	 *            A ZooKeeper cluster, identified by a comma separated list of
	 *            colon separated hostname:port pairs.
	 * @param timeoutMs
	 *            request that the ZooKeeper servers release any ID assignments
	 *            after this many milliseconds.
	 * @return a MachineIdAssigner which can assign unique identifiers.
	 */
	public static MachineIdAssigner withSessionTimeout(String cluster, int timeoutMs) throws Exception {
		return new MachineIdAssigner(cluster, timeoutMs);
	}

	CuratorFramework curator;

	MachineIdAssigner(String zkCluster, int sessionTimeoutMs) throws Exception {
		curator = CuratorFrameworkFactory.newClient(zkCluster, sessionTimeoutMs, connectionTimeoutMs, retryPolicy);
		curator.start();
	}

	public String pathFromId(int id) {
		return String.format("/machine_id/%d", id);
	}

	public int assign(IdentityGenerator generator) {
		int retryCount = 0;
		while (retryCount < COLLISION_MAX_RETRIES) {
			try {
				retryCount++;
				int id = generator.generate();
				if (tryAssign(pathFromId(id))) {
					return id;
				}
			} catch (Exception e) {
				return ERR_ZK_FAILURE;
			}
		}
		return ERR_IDENTIFIER_IN_USE;
	}

	private boolean tryAssign(String path) throws Exception {
		InterProcessLock lock = new InterProcessMutex(curator, path);
		return lock.acquire(acquireTimeout, acquireTimeoutTimeUnit);
	}

  /**
   * Simulate process termination by pulling the curator out from underneath this assigner.
   * This is only public until MachineIdAssignerSpec is moved into the same package as this.
   */
  @Deprecated
	public void releaseAll() {
		curator.close();
	}
}
