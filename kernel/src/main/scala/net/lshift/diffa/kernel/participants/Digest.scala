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

package net.lshift.diffa.kernel.participants

import org.joda.time.DateTime
import scala.beans.BeanProperty

/**
 * Describes a digest of version information, or the actual version itself.
 *
 * For an individual entity, the digest should be the version content and the
 * date should be the timestamp of the entity.
 *
 * For an aggregate entity, the digest should be the hashed aggregate of the
 * child entities within the given value range.
 *
 * The convention for the order is the lexiographical order of the declared categories of the pairing that this
 * digest is linked to. 
 *
 */
trait Digest {

  /**
   * A sequence of partition attributes in the order of the per-pair key names that they correspond to.
   */
  def attributes:Seq[String]

  /**
   * The actual digest to perform comparisons with.
   */
  def digest:String
}

/**
 *   A digest of aggregated versions.
 */
case class AggregateDigest(
  @BeanProperty var attributes:Seq[String],
  @BeanProperty var digest:String) extends Digest {

  def this() = this(null, null)
}

/**
 * The version digest an individual entity.
 */
case class EntityVersion(
  @BeanProperty var id:String,
  @BeanProperty var attributes:Seq[String],
  @BeanProperty var lastUpdated:DateTime,
  @BeanProperty var digest:String) extends Digest {

  def this() = this(null, null, null,null)
}