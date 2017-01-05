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

package net.lshift.diffa.util

import org.springframework.web.servlet.view.json.MappingJacksonJsonView
import java.lang.String
import java.util.Map
import scala.collection.JavaConversions._

/**
 * Modified Jackson Json View that only renders the first element of the view if the view contains only one entry.
 */
class ResultOnlyMappingJacksonJsonView extends MappingJacksonJsonView {
  override def filterModel(model: Map[String, Object]) = {
    val filtered = super.filterModel(model).asInstanceOf[Map[String, Object]]
    if (filtered.size > 1) {
      filtered
    } else {
      filtered(filtered.keySet.head)
    }
  }
}