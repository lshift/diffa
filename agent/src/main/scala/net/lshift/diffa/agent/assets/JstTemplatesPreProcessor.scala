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
package net.lshift.diffa.agent.assets

import ro.isdc.wro.model.resource.processor.ResourcePreProcessor
import ro.isdc.wro.model.resource.Resource
import org.apache.commons.io.IOUtils
import java.io.{Writer, Reader}
import org.apache.commons.lang3.StringEscapeUtils

/**
 * Pre-processes .html files, turning them into javascript code suitable for embedding.
 */
class JstTemplatesPreProcessor extends ResourcePreProcessor {
  def process(resource: Resource, input: Reader, output: Writer) {
    if (resource.getUri.endsWith(".jst")) {
      val content = IOUtils.toString(input)
      val name = removeSuffix(removePrefix(resource.getUri, "/js/templates/"), ".jst")

      output.write("window.JST = (window.JST || {});\n")
      output.write("window.JST['%s'] = _.template(\n".format(name))

      val escapedLines = content.lines.map(l => {
        "\"" + StringEscapeUtils.escapeJava(l) + "\""
      })
      output.write(escapedLines.mkString(" + \n"))
      output.write(");\n")
    } else {
      IOUtils.copy(input, output)
    }
  }

  def removePrefix(s:String, prefix:String) = {
    if (s.startsWith(prefix)) {
      s.substring(prefix.length())
    } else {
      s
    }
  }

  def removeSuffix(s:String, suffix:String) = {
    if (s.endsWith(suffix)) {
      s.substring(0, s.length() - suffix.length())
    } else {
      s
    }
  }
}