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
package net.lshift.diffa.kernel.limiting

import net.lshift.diffa.schema.servicelimits.ServiceLimit
import java.util.concurrent.ConcurrentHashMap
import net.lshift.diffa.kernel.util.{Lazy, Registry}
import org.apache.http.annotation.ThreadSafe

case class ServiceLimiterKey(limit: ServiceLimit, domain: Option[String], pair: Option[String]) {
  override def hashCode = 31 * (31 * (31 + limit.hashCode) + domain.getOrElse("__").hashCode) +
    pair.getOrElse("__").hashCode
}

object ServiceLimiterRegistry extends Registry[ServiceLimiterKey, Limiter] {
  val hashMap = new ConcurrentHashMap[ServiceLimiterKey, Lazy[Limiter]]()

  @ThreadSafe
  def get(key: ServiceLimiterKey, factory: () => Limiter): Limiter = {
    val newLimiter = new Lazy(factory())
    val oldLimiter = hashMap.putIfAbsent(key, newLimiter)

    if (oldLimiter == null) {
      newLimiter()
    } else {
      oldLimiter()
    }
  }
}
