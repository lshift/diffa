/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.client

import net.lshift.diffa.participant.scanning.{ScanAggregation, ScanConstraint}
import javax.ws.rs.core.MultivaluedMap
import com.sun.jersey.core.util.MultivaluedMapImpl
import java.net.URI
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.message.BasicNameValuePair
import scala.collection.JavaConversions._

/**
 * This acts as a value type representing an individual HTTP request
 * (currently only GETs), with a builder-style interface for incrementally
 * adding information to it (eg: authentication) details.
 */

case class DiffaHttpQuery(uri: String,
                          accept: Option[String] = None,
                          query: Map[String, Seq[String]] = Map(),
                          basicAuth: Option[(String, String)] = None) {

  var encoding = "utf-8"

  def accepting(content: String) = copy(accept=Some(content))
  def withQuery(query: Map[String, Seq[String]]) = copy(query=query)
  def withConstraints(constraints: Seq[ScanConstraint] ) = {
    withMultiValuedMapQuery(RequestBuildingHelper.constraintsToQueryArguments(_, constraints))
  }

  def withBasicAuth(user: String, passwd: String) = copy(basicAuth = Some((user, passwd)))

  def withAggregations(aggregations: Seq[ScanAggregation]) =
    withMultiValuedMapQuery(RequestBuildingHelper.aggregationsToQueryArguments(_, aggregations))

  private def withMultiValuedMapQuery(updator: MultivaluedMap[String, String] => Unit) = {
    val mvm = new MultivaluedMapImpl()
    updator(mvm)
    val nquery = mvm.foldLeft(query) {
      case (query, (key, value) ) => query + (key -> (query.getOrElse(key, Seq()) ++ value))
    }

    copy(query = nquery)
  }

  def fullUri: URI = {

    val (path, queryPrefix) = uri.split("\\?", 2) match {
      case Array(p) => (p, None);
      case Array(p, qs) => (p, Some(qs))
    }

    val additionalQueryParams = for {
      (key, values) <- this.query
      value <- values
    } yield new BasicNameValuePair(key, value)

    val additionalQuery = URLEncodedUtils.format(additionalQueryParams.toList, encoding) match {
      case "" => None
      case s => Some(s)
    }
    val completeQuery = Seq(queryPrefix, additionalQuery).flatMap(_.toSeq) match {
      case List() => None
      case query => Some(query.mkString("&"))
    }

    new URI(List(Some(path), completeQuery).flatMap(_.toSeq).mkString("?"))
  }
}
