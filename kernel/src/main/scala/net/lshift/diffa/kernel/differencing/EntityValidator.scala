/**
 * Copyright (C) 2012 LShift Ltd.
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

package net.lshift.diffa.kernel.differencing

import net.lshift.diffa.participant.scanning.ScanResultEntry
import org.joda.time.DateTime
import net.lshift.diffa.participant.common.{InvalidEntityException, ScanEntityValidator}
import net.lshift.diffa.participant.changes.ChangeEvent
import scala.collection.JavaConversions._

case class ValidatableEntity(id:String, version:String, lastUpdated:DateTime, attributes: Map[String, String])

object ValidatableEntity {

  import ValidatableEntity._
  def apply(e: ScanResultEntry) : ValidatableEntity = this(e.getId, e.getVersion, e.getLastUpdated,
    maybeMap(e.getAttributes))

  def apply(e: ChangeEvent) : ValidatableEntity = this(e.getId, e.getVersion, e.getLastUpdated,
    maybeMap(e.getAttributes))

  private def maybe[T](x: T) = x match {
    case null => None
    case x => Some(x)
  }

  private def maybeMap(attributes: java.util.Map[String, String]): Map[String, String] = {
    maybe(attributes).map(_.toMap).getOrElse(Map())
  }

}

/**
 * The function of this class is to accept something representing an entity of
 * some description (be it a ScanResultEntry or a ChangeEvent), and ensure
 * that it is at least *syntactically* valid, before allowing it into the
 * kernel of the system. Currently, we check for:
 *
 * - That the id of the entity contains only characters from the printable
 * subset of ASCII characters, in order to avoid issues with collation orders
 * and/or normalisation forms that may confuse the Merkle tree machinery.
 */
object EntityValidator extends ScanEntityValidator {
  import scala.collection.JavaConversions._

  val regex = java.util.regex.Pattern.compile("^\\p{Graph}*$")

  def validateCharactersInField(location: String, s: String) = {
    if (!regex.matcher(s).matches())
      throw new InvalidEntityException(
        "entity field: %s contained invalid data: %s; please see documentation for details".format(location, s))
  }
  def validate(e: ValidatableEntity): Unit = {
    if (e.id != null) validateCharactersInField("id", e.id)
    e.attributes.foreach { case (name, value) => validateCharactersInField(name, value) }
  }

  def process(e: ScanResultEntry): Unit = validate(ValidatableEntity(e))
  def process(e: ChangeEvent) = validate(ValidatableEntity(e))




}