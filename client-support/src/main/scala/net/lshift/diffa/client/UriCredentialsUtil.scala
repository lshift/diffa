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

package net.lshift.diffa.client

import com.sun.jersey.core.util.MultivaluedMapImpl
import net.lshift.diffa.kernel.config.QueryParameterCredentials
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.utils.URLEncodedUtils
import scala.collection.JavaConversions._
import java.net.URI

object UriCredentialsUtil {
  def buildQueryUri(base_uri: String, query: MultivaluedMapImpl, credentials: Option[QueryParameterCredentials]) = {
    val (path, queryPrefix) = base_uri.split("\\?", 2) match {
      case Array(p) => (p, None);
      case Array(p, qs) => (p, Some(qs))
    }

    val extraQueryString = constructQueryString(query) match {
      case "" => None
      case q => Some(q)
    }
    val completeQuery = List(
      queryPrefix, extraQueryString).flatMap(_.toSeq) match {
      case List() => None
      case query => Some(query.mkString("&"))
    }
    List(Some(path), completeQuery).flatMap(_.toSeq).mkString("?")
  }

  private def constructQueryString(queryParams: MultivaluedMapImpl) = {
    val qParams = {
      for (p <- queryParams.entrySet; v <- p.getValue)
      yield new BasicNameValuePair(p.getKey, v)
    }.toSeq

    URLEncodedUtils.format(qParams, "UTF-8")
  }
}
